package by.syrnik.aimcp.techzone.config;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Как application.yaml превращает переменные окружения в настройки приложения. Переменные заданы свойствами теста:
 * для подстановки ${...} Spring берёт их из тех же источников, что и переменные окружения.
 */
class ApplicationPropertiesTest {

    @Nested
    @SpringBootTest(properties = {
            "AIMCP_HISTORY_STORE=memory",
            "DB_HOST=db.example.invalid", "DB_PORT=15432", "DB_NAME=history",
            "DB_USER=history_user", "DB_PASSWORD=placeholder-password"})
    class UrlIsBuiltFromSeparateVariables {

        @Autowired
        Environment environment;

        @Test
        void settingsAreReadFromTheVariables() {
            assertThat(environment.getProperty("aimcp.history.store")).isEqualTo("memory");
            assertThat(environment.getProperty("aimcp.db.url")).isEqualTo("jdbc:postgresql://db.example.invalid:15432/history");
            assertThat(environment.getProperty("aimcp.db.user")).isEqualTo("history_user");
            assertThat(environment.getProperty("aimcp.db.password")).isEqualTo("placeholder-password");
        }
    }

    @Nested
    @SpringBootTest(properties = {
            "AIMCP_HISTORY_STORE=memory",
            "AIMCP_DB_URL=jdbc:postgresql://other.example.invalid:5432/history?currentSchema=aimcp",
            "DB_HOST=ignored.example.invalid", "DB_USER=history_user", "DB_PASSWORD=placeholder-password"})
    class FullUrlWinsOverSeparateVariables {

        @Autowired
        Environment environment;

        @Test
        void fullUrlIsUsedAsIs() {
            assertThat(environment.getProperty("aimcp.db.url"))
                    .isEqualTo("jdbc:postgresql://other.example.invalid:5432/history?currentSchema=aimcp");
        }
    }
}
