package by.syrnik.aimcp.core.profile;

import java.util.List;
import java.util.Objects;

final class Checks {
    private Checks() {}

    static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Поле '" + name + "' не может быть пустым");
        }
        return value;
    }

    /** Неизменяемая копия списка; элементы не null и не пустые. */
    static List<String> requireNonBlankItems(List<String> values, String name) {
        Objects.requireNonNull(values, name);
        List<String> copy = List.copyOf(values);
        for (String value : copy) {
            if (value.isBlank()) {
                throw new IllegalArgumentException("Элементы поля '" + name + "' не могут быть пустыми");
            }
        }
        return copy;
    }
}
