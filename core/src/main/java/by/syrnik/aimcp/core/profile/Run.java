package by.syrnik.aimcp.core.profile;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** Как запускать тесты: {@code command} — готовая команда списком аргументов, без оболочки. */
public record Run(List<String> command, Duration timeout) {

    public Run {
        command = Checks.requireNonBlankItems(command, "command");
        if (command.isEmpty()) {
            throw new IllegalArgumentException("Поле 'command' не может быть пустым");
        }
        Objects.requireNonNull(timeout, "timeout");
    }
}
