package by.syrnik.aimcp.runner.tools.schema;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public sealed interface Param permits StringParam, IntegerParam, BooleanParam, StringArrayParam {

    String name();

    String description();

    boolean required();

    public static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_]{0,63}");

    static void checkName(String name) {
        Objects.requireNonNull(name);

        Matcher matcher = NAME.matcher(name);

        if (!matcher.matches()) {
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
