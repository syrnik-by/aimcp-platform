package by.syrnik.aimcp.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class CoreImportTest {

    private static final Pattern IMPORT_PATTERN =
            Pattern.compile("^\\s*import\\s+(?:static\\s+)?([\\w.]+\\*?)\\s*;");

    private static final Set<String> ALLOWED_IMPORT_PREFIXES = Set.of(
            "java.util.",
            "java.time.",
            "java.math.",
            "by.syrnik.aimcp.core."
    );

    Path mainSources = Path.of("src", "main", "java");

    @Test
    void allJavaImportsShouldStartWithAllowedPrefix() throws IOException {
        List<String> reportLines = new ArrayList<>();

        try (Stream<Path> files = Files.walk(mainSources)) {
            files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(file -> collectInvalidImports(file, reportLines));
        }

        assertThat(reportLines)
                .as("Найдены импорты, не начинающиеся с разрешённого префикса; отчёт: %s", reportLines)
                .isEmpty();
    }

    private void collectInvalidImports(
            Path javaFile,
            List<String> reportLines
    ) {
        try {
            List<String> lines = Files.readAllLines(javaFile, StandardCharsets.UTF_8);

            for (int i = 0; i < lines.size(); i++) {
                Matcher matcher = IMPORT_PATTERN.matcher(lines.get(i));

                if (!matcher.lookingAt()) {
                    continue;
                }

                String fullImport = matcher.group(1).trim();

                if (isAllowed(fullImport)) {
                    continue;
                }

                reportLines.add("%s:%d: %s".formatted(javaFile, i + 1, fullImport));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось прочитать файл: " + javaFile, e);
        }
    }

    private boolean isAllowed(String fullImport) {

        return ALLOWED_IMPORT_PREFIXES.stream()
                .anyMatch(fullImport::startsWith);
    }
}
