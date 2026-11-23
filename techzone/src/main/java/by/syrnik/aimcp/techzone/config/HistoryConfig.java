package by.syrnik.aimcp.techzone.config;

import by.syrnik.aimcp.core.history.RunHistoryPort;
import by.syrnik.aimcp.techzone.ingest.ResultFileParser;
import by.syrnik.aimcp.techzone.ingest.ResultIngestService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class HistoryConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ResultFileParser resultFileParser() {
        return new ResultFileParser();
    }

    @Bean
    ResultIngestService resultIngestService(RunHistoryPort history, ResultFileParser parser) {
        return new ResultIngestService(history, parser);
    }
}
