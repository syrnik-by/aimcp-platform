package by.syrnik.aimcp.runner.tools.schema;

import by.syrnik.aimcp.runner.tools.ErrorCode;
import by.syrnik.aimcp.runner.tools.ToolException;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public record StringRules(Integer minLength, Integer maxLength,
                          String pattern, List<String> enumValues) {
    public static final int DEFAULT_MAX_LENGTH = 256;
    public static final StringRules NONE = new StringRules(null, null, null, List.of());

    public StringRules(Integer minLength, Integer maxLength, String pattern, List<String> enumValues) {

        if (minLength <= 0 || maxLength <= 1 || minLength >= maxLength || maxLength > DEFAULT_MAX_LENGTH ) {
            throw new IllegalArgumentException();
        }

        this.minLength = minLength;
        this.maxLength = maxLength;

        try {
            Pattern.compile(pattern);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException();
        }

        this.pattern = pattern;

        this.enumValues = List.copyOf(enumValues);
        for (String s : enumValues) {

        }
    }
}
