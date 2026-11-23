package by.syrnik.aimcp.techzone.config;

import by.syrnik.aimcp.adapters_postgres.history.JdbcRunHistory;
import by.syrnik.aimcp.adapters_stub.history.InMemoryRunHistory;
import by.syrnik.aimcp.core.history.RunHistoryPort;
import by.syrnik.aimcp.techzone.ingest.ResultIngestService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Выбор хранилища настройкой aimcp.history.store без БД и без Docker: для postgres пул подменяется моком.
 */
class HistoryStoreSelectionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(HistoryConfig.class, InMemoryHistoryConfig.class, PostgresHistoryConfig.class);

    @Test
    void memoryIsTheDefaultAndNeedsNoDatabaseSettings() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(RunHistoryPort.class);
            assertThat(context.getBean(RunHistoryPort.class)).isInstanceOf(InMemoryRunHistory.class);
            assertThat(context).doesNotHaveBean(DataSource.class);
            assertThat(context).hasSingleBean(ResultIngestService.class);
        });
    }

    @Test
    void memoryCanBeChosenExplicitly() {
        runner.withPropertyValues("aimcp.history.store=memory").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(RunHistoryPort.class)).isInstanceOf(InMemoryRunHistory.class);
            assertThat(context).doesNotHaveBean(DataSource.class);
        });
    }

    @Test
    void postgresStoreServesHistoryFromJdbcOnTheConfiguredPool() {
        DataSource pool = mock(DataSource.class);

        runner.withPropertyValues("aimcp.history.store=postgres", "aimcp.db.url=jdbc:postgresql://db.example.invalid:5432/history",
                        "aimcp.db.user=history_user", "aimcp.db.password=placeholder-password")
                .withAllowBeanDefinitionOverriding(true)
                .withBean("dataSource", DataSource.class, () -> pool)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RunHistoryPort.class);
                    assertThat(context.getBean(RunHistoryPort.class)).isInstanceOf(JdbcRunHistory.class);
                    assertThat(context.getBean(DataSource.class)).isSameAs(pool);
                    assertThat(context).hasSingleBean(ResultIngestService.class);
                });
    }

    @Test
    void unknownStoreDoesNotStartInsteadOfFallingBackToMemory() {
        runner.withPropertyValues("aimcp.history.store=postgress").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("RunHistoryPort");
        });
    }
}
