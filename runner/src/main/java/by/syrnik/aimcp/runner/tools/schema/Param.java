package by.syrnik.aimcp.runner.tools.schema;

import by.syrnik.aimcp.runner.tools.ErrorCode;
import by.syrnik.aimcp.runner.tools.ToolException;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public sealed interface Param permits StringParam, IntegerParam, BooleanParam, StringArrayParam {

    String name();
    String description();
    boolean required();

    static void checkName(String name) {
        Objects.requireNonNull(name);
        Pattern pattern = Pattern.compile("[a-z][a-z0-9_]{0,63}");
        Matcher matcher = pattern.matcher(name);

        if (!matcher.find()) {
            throw new IllegalArgumentException();
        }
    }

    static void checkDescription(String description) {
        Objects.requireNonNull(description);
        if (description.isBlank()) {
            throw new IllegalArgumentException();
        }
    }
}
