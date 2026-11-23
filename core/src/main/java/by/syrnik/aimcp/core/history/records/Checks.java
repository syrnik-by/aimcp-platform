package by.syrnik.aimcp.core.history.records;

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
}
