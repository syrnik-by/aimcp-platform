package by.syrnik.aimcp.runner.tools;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeTest {

    private static final Map<String, Integer> CONTRACT = Map.ofEntries(
            Map.entry("invalid_request", 400), Map.entry("invalid_arguments", 400), Map.entry("unauthorized", 401),
            Map.entry("unknown_tool", 404), Map.entry("not_found", 404), Map.entry("method_not_allowed", 405),
            Map.entry("busy", 409), Map.entry("request_too_large", 413), Map.entry("tool_failed", 500),
            Map.entry("masking_failed", 500), Map.entry("response_too_large", 500), Map.entry("internal", 500));

    @Test
    void codesAndStatusesAreTheTableOfTheContract() {
        Map<String, Integer> actual = new LinkedHashMap<>();
        for (ErrorCode value : ErrorCode.values()) {
            actual.put(value.code(), value.httpStatus());
        }

        assertThat(actual).containsExactlyInAnyOrderEntriesOf(CONTRACT);
    }

    @Test
    void codesAndStatusesMatchTheTableInTheContractDocument() throws IOException {
        List<String> lines = Files.readAllLines(Path.of("..", "docs", "runner-tools.md"), StandardCharsets.UTF_8);
        Pattern row = Pattern.compile("^\\| `([a-z_]+)` \\| (\\d{3}) \\|");
        Map<String, Integer> documented = new LinkedHashMap<>();
        for (String line : lines) {
            Matcher matcher = row.matcher(line);
            if (matcher.find()) {
                documented.put(matcher.group(1), Integer.parseInt(matcher.group(2)));
            }
        }

        assertThat(documented).hasSize(ErrorCode.values().length);
        for (ErrorCode value : ErrorCode.values()) {
            assertThat(documented).containsEntry(value.code(), value.httpStatus());
        }
    }

    @Test
    void codeDoesNotDependOnTheDefaultLocale() {
        Locale saved = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));   // в турецкой локали «I» переводится в «ı»

            assertThat(ErrorCode.INVALID_REQUEST.code()).isEqualTo("invalid_request");
            assertThat(Arrays.stream(ErrorCode.values()).map(ErrorCode::code)).containsExactlyInAnyOrderElementsOf(CONTRACT.keySet());
        } finally {
            Locale.setDefault(saved);
        }
    }

    @Test
    void codesAreUnique() {
        assertThat(Arrays.stream(ErrorCode.values()).map(ErrorCode::code).distinct().count())
                .isEqualTo(ErrorCode.values().length);
    }
}
