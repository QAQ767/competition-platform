package com.campus.competition.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

/** 兼容已有数据库：只补缺失字段，不修改或删除用户数据。先于演示数据初始化执行。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PreparationSchemaUpgrade implements CommandLineRunner {
    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    public PreparationSchemaUpgrade(DataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public void run(String... args) throws SQLException {
        addColumn("team_task", "phase", "VARCHAR(20) NOT NULL DEFAULT '准备阶段'");
        addColumn("team_task", "priority", "VARCHAR(10) NOT NULL DEFAULT '普通'");
        addColumn("team_task", "resource_id", "BIGINT");
        addColumn("team_task", "completion_note", "VARCHAR(1000)");
        addColumn("team_task", "completed_at", "DATETIME");
        addColumn("notification", "invite_id", "BIGINT");
        addColumn("users", "project_experience", "VARCHAR(3000)");
        addColumn("users", "preferred_roles", "VARCHAR(100)");
        addColumn("users", "weekly_hours", "INT");
        // 赛事推荐的可用结构化字段；旧数据保持空，不自动推断成“官方资格”。
        addColumn("competition", "tags", "VARCHAR(300)");
        addColumn("competition", "eligibility_text", "VARCHAR(1000)");
        addColumn("competition", "competition_year", "INT");
        addColumn("competition", "edition", "VARCHAR(50)");
        addColumn("competition", "track", "VARCHAR(100)");
        addColumn("competition", "source_url", "VARCHAR(800)");
        addColumn("competition", "source_type", "VARCHAR(30)");
        addColumn("competition", "identity_key", "VARCHAR(80)");
        addColumn("competition_draft", "reviewed_version", "INT");
        addColumn("competition_draft_review", "change_summary", "VARCHAR(1000)");
        // 已有库可能已有重复历史数据，建索引失败只记录告警；新的发布仍由事务内查重兜底。
        try {
            if (hasTable("competition") && !hasIndex("competition", "uk_competition_identity")) {
                jdbc.execute("CREATE UNIQUE INDEX uk_competition_identity ON competition(identity_key)");
            }
        } catch (org.springframework.dao.DataAccessException error) {
            org.slf4j.LoggerFactory.getLogger(PreparationSchemaUpgrade.class)
                    .warn("未能创建赛事实例唯一索引（可能存在历史重复数据）：{}", error.getMessage());
        }
        backfillIdentityKeys();
    }

    /**
     * 身份键算法调整后（如届次归一），已有记录需要重算，否则新的发布无法与旧记录比对。
     * 单行冲突只记录告警，不影响启动。
     */
    private void backfillIdentityKeys() {
        try {
            if (!hasTable("competition")) return;
            for (java.util.Map<String, Object> row : jdbc.queryForList(
                    "SELECT id, name, organizer, competition_year, edition, track, identity_key FROM competition")) {
                Object id = raw(row, "id");
                if (id == null) continue;
                String key = com.campus.competition.support.CompetitionIdentity.key(
                        valueOf(row, "name"), valueOf(row, "organizer"),
                        integerOf(row, "competition_year"),
                        valueOf(row, "edition"), valueOf(row, "track"));
                Object current = raw(row, "identity_key");
                if (key == null ? current == null : key.equals(current)) continue;
                try {
                    jdbc.update("UPDATE competition SET identity_key = ? WHERE id = ?", key, id);
                } catch (org.springframework.dao.DataAccessException error) {
                    org.slf4j.LoggerFactory.getLogger(PreparationSchemaUpgrade.class)
                            .warn("赛事 {} 的身份键未能更新（可能与现有记录重复）：{}", id, error.getMessage());
                }
            }
        } catch (SQLException | org.springframework.dao.DataAccessException error) {
            org.slf4j.LoggerFactory.getLogger(PreparationSchemaUpgrade.class)
                    .warn("回填赛事实例身份键失败：{}", error.getMessage());
        }
    }

    private String valueOf(java.util.Map<String, Object> row, String column) {
        Object value = raw(row, column);
        return value == null ? null : value.toString();
    }

    private Integer integerOf(java.util.Map<String, Object> row, String column) {
        Object value = raw(row, column);
        if (value == null) return null;
        return value instanceof Number number ? number.intValue() : Integer.valueOf(value.toString());
    }

    /** 不同数据库返回的列名大小写不同，两种都查一次。 */
    private Object raw(java.util.Map<String, Object> row, String column) {
        Object value = row.get(column);
        if (value != null) return value;
        for (java.util.Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(column)) return entry.getValue();
        }
        return null;
    }

    private boolean hasIndex(String table, String index) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             ResultSet indexes = connection.getMetaData().getIndexInfo(
                     connection.getCatalog(), null, table, false, false)) {
            while (indexes.next()) {
                if (index.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) return true;
            }
            return false;
        }
    }

    private void addColumn(String table, String column, String definition) throws SQLException {
        if (!hasTable(table)) return;
        if (hasColumn(table, column)) return;
        try {
            jdbc.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        } catch (org.springframework.dao.DataAccessException error) {
            // 多实例同时启动时，另一实例可能已添加字段。其他错误仍应阻止启动。
            if (!hasColumn(table, column)) throw error;
        }
    }

    private boolean hasTable(String table) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             ResultSet tables = connection.getMetaData().getTables(
                     connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                if (table.equalsIgnoreCase(tables.getString("TABLE_NAME"))) return true;
            }
            return false;
        }
    }

    private boolean hasColumn(String table, String column) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(), null, "%", "%")) {
            while (columns.next()) {
                if (table.equalsIgnoreCase(columns.getString("TABLE_NAME"))
                        && column.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) return true;
            }
            return false;
        }
    }
}
