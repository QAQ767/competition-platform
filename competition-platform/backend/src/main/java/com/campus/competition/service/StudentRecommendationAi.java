package com.campus.competition.service;

import com.campus.competition.vo.CompetitionRecommendationVO;
import com.campus.competition.vo.TeamRecommendationVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 模型输出校验。模型只能对已召回的 ID 重排和解释：
 * 假 ID、重复 ID、缺失候选、越界分数、超长文本、残缺 JSON 都会导致整批回退规则结果。
 *
 * Schema 会把本次候选 ID 写成 enum，并固定数组长度；这能让 Ollama 的 JSON 语法约束
 * 直接拒绝“编一个不存在的赛事 ID”或“少输出几个候选”。
 */
public final class StudentRecommendationAi {

    private StudentRecommendationAi() {
    }

    /** 为本次候选动态生成 Schema，ID 白名单与数量都由服务端决定。 */
    public static String schema(List<Long> competitionIds, List<Long> teamIds) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        root.put("type", "object");
        root.put("additionalProperties", false);
        ArrayNode required = root.putArray("required");
        required.add("insight").add("competitions").add("teams");
        ObjectNode properties = root.putObject("properties");
        properties.putObject("insight").put("type", "string").put("maxLength", 800);

        properties.set("competitions", arraySchema(mapper, competitionIds, false));
        properties.set("teams", arraySchema(mapper, teamIds, true));
        return root.toString();
    }

    private static ObjectNode arraySchema(ObjectMapper mapper, List<Long> ids, boolean team) {
        ObjectNode array = mapper.createObjectNode();
        array.put("type", "array");
        if (!ids.isEmpty()) {
            array.put("minItems", ids.size());
            array.put("maxItems", ids.size());
        } else {
            array.put("maxItems", 0);
        }
        ObjectNode item = array.putObject("items");
        item.put("type", "object");
        item.put("additionalProperties", false);
        ArrayNode required = item.putArray("required");
        required.add("id").add("score").add("reason").add("risks").add("nextStep");
        if (team) required.add("suggestedRole");
        ObjectNode properties = item.putObject("properties");
        ObjectNode id = properties.putObject("id");
        id.put("type", "integer");
        if (!ids.isEmpty()) {
            ArrayNode allowed = id.putArray("enum");
            ids.forEach(allowed::add);
        }
        ObjectNode score = properties.putObject("score");
        score.put("type", "integer").put("minimum", 0).put("maximum", 100);
        properties.putObject("reason").put("type", "string").put("maxLength", 600);
        ObjectNode risks = properties.putObject("risks");
        risks.put("type", "array").put("maxItems", 4);
        risks.putObject("items").put("type", "string").put("maxLength", 300);
        properties.putObject("nextStep").put("type", "string").put("maxLength", 250);
        if (team) {
            properties.putObject("suggestedRole").put("type", "string").put("maxLength", 80);
        }
        return array;
    }

    public static final class Application {
        public boolean ok;
        public String insight;
        public String error;

        static Application fail(String reason) {
            Application application = new Application();
            application.ok = false;
            application.error = reason;
            return application;
        }
    }

    public static Application apply(String output,
                                    List<CompetitionRecommendationVO> competitions,
                                    List<TeamRecommendationVO> teams) {
        if (output == null || output.isBlank()) return Application.fail("输出为空");
        try {
            String clean = output.replaceAll("(?s)<think>.*?</think>", "").trim();
            int start = clean.indexOf('{');
            int end = clean.lastIndexOf('}');
            if (start < 0 || end <= start) return Application.fail("找不到 JSON 对象");
            JsonNode root = new ObjectMapper().readTree(clean.substring(start, end + 1));

            String insight = text(root, "insight", 800);
            if (insight == null) return Application.fail("insight 为空或超长");

            List<Long> expectedCompetitions = competitions.stream()
                    .map(CompetitionRecommendationVO::getId).toList();
            List<Long> expectedTeams = teams.stream()
                    .map(TeamRecommendationVO::getId).toList();
            String compMismatch = mismatch(root.path("competitions"), expectedCompetitions);
            if (compMismatch != null) return Application.fail("竞赛 " + compMismatch);
            String teamMismatch = mismatch(root.path("teams"), expectedTeams);
            if (teamMismatch != null) return Application.fail("队伍 " + teamMismatch);
            Map<Long, JsonNode> compRows = rows(root.path("competitions"), expectedCompetitions);
            Map<Long, JsonNode> teamRows = rows(root.path("teams"), expectedTeams);
            if (compRows == null) return Application.fail("竞赛 ID 缺失、重复或数量不一致");
            if (teamRows == null) return Application.fail("队伍 ID 缺失、重复或数量不一致");

            for (CompetitionRecommendationVO vo : competitions) {
                JsonNode row = compRows.get(vo.getId());
                if (!validRow(row, false)) return Application.fail("竞赛条目字段不合法，id=" + vo.getId());
                vo.setMatchScore(row.path("score").asInt());
                vo.setReason(row.path("reason").asText().trim());
                vo.setRisks(strings(row.path("risks")));
                vo.setNextStep(row.path("nextStep").asText().trim());
            }
            for (TeamRecommendationVO vo : teams) {
                JsonNode row = teamRows.get(vo.getId());
                if (!validRow(row, true)) return Application.fail("队伍条目字段不合法，id=" + vo.getId());
                vo.setMatchScore(row.path("score").asInt());
                vo.setReason(row.path("reason").asText().trim());
                vo.setRisks(strings(row.path("risks")));
                vo.setNextStep(row.path("nextStep").asText().trim());
                vo.setSuggestedRole(cleanRole(row.path("suggestedRole").asText("")));
            }
            competitions.sort(Comparator.comparing(CompetitionRecommendationVO::getMatchScore).reversed());
            teams.sort(Comparator.comparing(TeamRecommendationVO::getMatchScore).reversed());

            Application application = new Application();
            application.ok = true;
            application.insight = insight;
            return application;
        } catch (Exception invalid) {
            return Application.fail("JSON 解析失败：" + invalid.getClass().getSimpleName());
        }
    }

    /** 返回可读的不一致原因；null 表示 ID 集合正确。只描述 ID，不记录模型正文。 */
    private static String mismatch(JsonNode node, List<Long> expected) {
        if (!node.isArray()) return "列表不是数组";
        if (node.size() != expected.size()) {
            return "数量 " + node.size() + "/" + expected.size();
        }
        Set<Long> allowed = new HashSet<>(expected);
        Set<Long> seen = new HashSet<>();
        for (JsonNode row : node) {
            if (!row.path("id").isIntegralNumber()) return "包含非整数 ID";
            long id = row.path("id").asLong();
            if (!allowed.contains(id)) return "包含未知 ID " + id;
            if (!seen.add(id)) return "重复 ID " + id;
        }
        return null;
    }

    /**
     * 每类必须完整覆盖候选 ID，且每个 ID 只出现一次。返回 null 表示不合格。
     */
    private static Map<Long, JsonNode> rows(JsonNode node, List<Long> expected) {
        if (!node.isArray() || node.size() != expected.size()) return null;
        Set<Long> allowed = new HashSet<>(expected);
        Map<Long, JsonNode> mapped = new HashMap<>();
        for (JsonNode row : node) {
            if (!row.path("id").isIntegralNumber()) return null;
            long id = row.path("id").asLong();
            if (!allowed.contains(id) || mapped.put(id, row) != null) return null;
        }
        return mapped.size() == allowed.size() ? mapped : null;
    }

    private static boolean validRow(JsonNode row, boolean team) {
        if (row == null || !row.path("score").isIntegralNumber()) return false;
        int score = row.path("score").asInt();
        if (score < 0 || score > 100) return false;
        if (text(row, "reason", 600) == null || text(row, "nextStep", 250) == null) return false;
        JsonNode risks = row.path("risks");
        if (!risks.isArray() || risks.size() > 4) return false;
        for (JsonNode risk : risks) {
            if (!risk.isTextual() || risk.asText().isBlank() || risk.asText().length() > 300) return false;
        }
        return !team || text(row, "suggestedRole", 10000) != null;
    }

    /**
     * 小模型常把“建议角色”写成斜杠分隔的长串。只保留第一个角色并限长，
     * 这是展示层清洗，不新增任何模型没有给出的信息。
     */
    private static String cleanRole(String value) {
        if (value == null || value.isBlank()) return null;
        String role = value.trim().split("[/、,，;；|]")[0].trim();
        if (role.isBlank()) return null;
        return role.length() <= 40 ? role : role.substring(0, 40);
    }

    private static List<String> strings(JsonNode node) {
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) values.add(item.asText().trim());
        return values;
    }

    private static String text(JsonNode row, String key, int max) {
        JsonNode value = row.path(key);
        if (!value.isTextual() || value.asText().isBlank() || value.asText().length() > max) return null;
        return value.asText().trim();
    }
}
