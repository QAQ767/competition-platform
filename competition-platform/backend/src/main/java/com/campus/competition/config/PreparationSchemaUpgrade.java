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
    }

    private void addColumn(String table, String column, String definition) throws SQLException {
        if (hasColumn(table, column)) return;
        try {
            jdbc.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        } catch (org.springframework.dao.DataAccessException error) {
            // 多实例同时启动时，另一实例可能已添加字段。其他错误仍应阻止启动。
            if (!hasColumn(table, column)) throw error;
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
