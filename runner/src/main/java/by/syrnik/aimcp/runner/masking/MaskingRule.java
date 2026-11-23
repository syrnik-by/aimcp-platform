package by.syrnik.aimcp.runner.masking;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Правило маскирования: {@code name} попадает в заменитель {@code [MASKED:name]}. Если в выражении есть именованная
 * группа {@code secret}, заменяется только она, иначе всё совпадение.
 */
public record MaskingRule(String name, Pattern pattern) {

    public MaskingRule {
        Objects.requireNonNull(pattern, "pattern");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Поле 'name' не может быть пустым");
        }
    }
}
