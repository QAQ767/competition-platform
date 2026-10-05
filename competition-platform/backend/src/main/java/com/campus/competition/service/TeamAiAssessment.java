package com.campus.competition.service;

import com.campus.competition.vo.RecommendUserVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

/** Strictly validate the entire assessment before applying it; unknown/duplicate users are rejected. */
public final class TeamAiAssessment {
    public static final String SCHEMA = "{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"teamAnalysis\",\"candidates\"],"
            + "\"properties\":{\"teamAnalysis\":{\"type\":\"string\"},\"candidates\":{\"type\":\"array\",\"items\":{"
            + "\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"userId\",\"score\",\"reason\",\"suggestedRole\",\"uncertainty\"],"
            + "\"properties\":{\"userId\":{\"type\":\"integer\"},\"score\":{\"type\":\"integer\",\"minimum\":0,\"maximum\":100},"
            + "\"reason\":{\"type\":\"string\"},\"suggestedRole\":{\"type\":\"string\"},\"uncertainty\":{\"type\":\"string\"}}}}}}";
    private TeamAiAssessment() { }

    public static boolean apply(String output, List<RecommendUserVO> candidates) {
        if (output == null || output.isBlank()) return false;
        try {
            String clean = output.replaceAll("(?s)<think>.*?</think>", "").trim();
            int start = clean.indexOf('{'), end = clean.lastIndexOf('}');
            if (start < 0 || end < start) return false;
            JsonNode root = new ObjectMapper().readTree(clean.substring(start, end + 1));
            String analysis = text(root, "teamAnalysis", 1200);
            JsonNode rows = root.path("candidates");
            if (analysis == null || !rows.isArray() || rows.size() != candidates.size()) return false;
            Map<Long, JsonNode> mapped = new HashMap<>();
            Set<Long> allowed = new HashSet<>();
            candidates.forEach(c -> allowed.add(c.getUserId()));
            for (JsonNode row : rows) {
                if (!row.path("userId").isIntegralNumber() || !row.path("score").isIntegralNumber()) return false;
                long id = row.path("userId").asLong();
                int score = row.path("score").asInt();
                if (!allowed.contains(id) || mapped.put(id, row) != null || score < 0 || score > 100
                        || text(row, "reason", 1000) == null || text(row, "suggestedRole", 100) == null
                        || text(row, "uncertainty", 500) == null) return false;
            }
            for (RecommendUserVO candidate : candidates) {
                JsonNode row = mapped.get(candidate.getUserId());
                candidate.setMatchScore(row.path("score").asInt());
                candidate.setReason(row.path("reason").asText());
                candidate.setSuggestedRole(row.path("suggestedRole").asText());
                candidate.setUncertainty(row.path("uncertainty").asText());
                candidate.setTeamAnalysis(analysis);
                candidate.setReasonSource("LLM");
            }
            candidates.sort(Comparator.comparing(RecommendUserVO::getMatchScore).reversed());
            return true;
        } catch (Exception invalid) { return false; }
    }

    private static String text(JsonNode row, String key, int max) {
        JsonNode value = row.path(key);
        if (!value.isTextual() || value.asText().isBlank() || value.asText().length() > max) return null;
        return value.asText();
    }
}
