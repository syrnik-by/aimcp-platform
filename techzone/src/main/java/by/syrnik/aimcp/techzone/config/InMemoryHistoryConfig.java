package by.syrnik.aimcp.techzone.config;

import by.syrnik.aimcp.adapters_stub.history.InMemoryRunHistory;
import by.syrnik.aimcp.core.history.RunHistoryPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

// PORTING: заглушка в памяти, для тестов и локального запуска без БД
@Configuration
@ConditionalOnProperty(name = "aimcp.history.store", havingValue = "memory", matchIfMissing = true)
public class InMemoryHistoryConfig {

    @Bean
    RunHistoryPort historyPort(Clock clock) {
        return new InMemoryRunHistory(clock);
    }
}
