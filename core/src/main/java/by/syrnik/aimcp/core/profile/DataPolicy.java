package by.syrnik.aimcp.core.profile;

import java.util.List;
import java.util.Objects;

/**
 * Политика данных: {@code mask} — регулярные выражения для маскирования, {@code neverSend} — glob-шаблоны путей
 * от корня репозитория, которые не читаем и не отдаём никогда. Оба списка хранятся строками: компилирует их тот,
 * кто применяет.
 */
public record DataPolicy(List<String> mask, List<String> neverSend) {

    public DataPolicy {
        Objects.requireNonNull(mask, "mask");
        Objects.requireNonNull(neverSend, "neverSend");
        mask = List.copyOf(mask);
        neverSend = List.copyOf(neverSend);
    }
}
