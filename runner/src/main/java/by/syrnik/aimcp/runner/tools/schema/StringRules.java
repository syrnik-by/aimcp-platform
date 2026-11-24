package by.syrnik.aimcp.runner.tools.schema;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public record StringRules(String name, Integer minLength, Integer maxLength,
                          String pattern, List<String> enumValues) {
    public static final int DEFAULT_MAX_LENGTH = 256;

    public StringRules {

        int effectiveMax = maxLength != null ? maxLength : DEFAULT_MAX_LENGTH;
        if (minLength != null && (minLength < 0 || effectiveMax < 1 || minLength > effectiveMax)) {
            throw new IllegalArgumentException("Проверьте значения minLength и maxLength параметра " + name);
        }

        if (pattern != null) {
            try {
                Pattern.compile(pattern);
            } catch (PatternSyntaxException e) {
                throw new IllegalArgumentException("Некорректное регулярное выражение у параметра " + name);
            }
        }

        Objects.requireNonNull(enumValues);
        enumValues = List.copyOf(enumValues);

        HashSet<String> enumSet = new HashSet<>();

        for (String s : enumValues) {
            if (s.isBlank() || !enumSet.add(s) || !(s.length() <= effectiveMax)) {
                throw new IllegalArgumentException("Проверьте значения enum параметра " + name);
            }
        }
    }
}
