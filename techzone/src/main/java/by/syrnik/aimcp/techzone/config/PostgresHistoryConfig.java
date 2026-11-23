package by.syrnik.aimcp.techzone.config;

import by.syrnik.aimcp.adapters_postgres.history.JdbcRunHistory;
import by.syrnik.aimcp.core.history.RunHistoryPort;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

// PORTING: подключение к БД, заведённой по заявке; параметры только из окружения
@Configuration
@ConditionalOnProperty(name = "aimcp.history.store", havingValue = "postgres")
public class PostgresHistoryConfig {

    @Value("${aimcp.db.url}")
    private String jdbcUrl;

    @Value("${aimcp.db.user}")
    private String username;

    @Value("${aimcp.db.password}")
    private String password;

    @Bean
    DataSource dataSource() {

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);

        return new HikariDataSource(hikariConfig);
    }

    @Bean
    RunHistoryPort runHistoryPort() {
        return new JdbcRunHistory(dataSource());
    }
}
