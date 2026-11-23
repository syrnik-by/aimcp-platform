package by.syrnik.aimcp.adapters_stub.history;

import by.syrnik.aimcp.core.history.RunHistoryPort;
import by.syrnik.aimcp.core.history.RunHistoryPortContract;
import by.syrnik.aimcp.core.history.records.RunKey;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRunHistoryTest extends RunHistoryPortContract {

    @Override
    protected RunHistoryPort createPort() {
        return new InMemoryRunHistory(Clock.systemUTC());
    }

    @Test
    void receivedAtIsTheClockTimeOfTheFirstSave() {
        Instant first = Instant.parse("2026-03-01T12:00:00Z");
        SettableClock clock = new SettableClock(first);
        InMemoryRunHistory history = new InMemoryRunHistory(clock);
        RunKey key = newKey();

        history.save(key, emptyInfo(), List.of(result("a", "a", null, null)));
        clock.set(first.plusSeconds(600));
        history.save(key, emptyInfo(), List.of(result("b", "b", null, null)));

        assertThat(history.findRun(key).orElseThrow().receivedAt()).isEqualTo(first);
    }

    private static final class SettableClock extends Clock {
        private Instant now;

        SettableClock(Instant now) {
            this.now = now;
        }

        void set(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
