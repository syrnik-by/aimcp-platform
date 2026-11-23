package by.syrnik.aimcp.runner.profile;

import by.syrnik.aimcp.core.profile.DataPolicy;
import by.syrnik.aimcp.core.profile.Env;
import by.syrnik.aimcp.core.profile.InvalidProfileException;
import by.syrnik.aimcp.core.profile.ProjectProfile;
import by.syrnik.aimcp.core.profile.Run;
import by.syrnik.aimcp.core.profile.Testit;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

final class ProfileParser {

    static final int SUPPORTED_VERSION = 1;
    static final Duration DEFAULT_TIMEOUT = Duration.ofHours(1);
    static final Duration MAX_TIMEOUT = Duration.ofHours(12);
    static final int MAX_PATTERNS = 100;
    static final int MAX_MASK_LENGTH = 500;

    private static final Pattern PROJECT = Pattern.compile("[A-Za-z0-9._-]{1,100}");
    private static final Pattern DRIVE_LETTER = Pattern.compile("^[A-Za-z]:.*", Pattern.DOTALL);

    ProjectProfile parse(Object tree) {
        Map<String, Object> root = asObject(tree, "");
        checkVersion(root);
        rejectUnknownKeys(root, "", "profile_version", "project", "repo", "framework_ver", "testit", "run", "env",
                "data_policy", "owners");
        return new ProjectProfile(
                requiredProject(root),
                requiredRepo(root),
                optionalText(root, "", "framework_ver"),
                parseTestit(optionalObject(root, "", "testit")),
                parseRun(requiredObject(root, "", "run")),
                parseEnv(requiredObject(root, "", "env")),
                parseDataPolicy(optionalObject(root, "", "data_policy")),
                optionalStrings(root, "", "owners", Integer.MAX_VALUE));
    }

    private static void checkVersion(Map<String, Object> root) {
        if (!(required(root, "", "profile_version") instanceof Integer version)) {
            throw invalid("/profile_version", "ожидалось целое число");
        }
        if (version != SUPPORTED_VERSION) {
            throw invalid("/profile_version", "версия не поддерживается, ожидалась " + SUPPORTED_VERSION);
        }
    }

    private static String requiredProject(Map<String, Object> root) {
        String project = requiredText(root, "", "project");
        if (!PROJECT.matcher(project).matches()) {
            throw invalid("/project", "допустимы символы A-Za-z0-9._- , длина 1-100");
        }
        return project;
    }

    private static String requiredRepo(Map<String, Object> root) {
        String repo = requiredText(root, "", "repo");
        if (repo.regionMatches(true, 0, "http://", 0, 7) || repo.regionMatches(true, 0, "https://", 0, 8)) {
            try {
                if (new URI(repo).getRawUserInfo() != null) {
                    throw invalid("/repo", "в ссылке не должно быть логина и пароля");
                }
            } catch (URISyntaxException e) {
                throw invalid("/repo", "не адрес");
            }
        }
        return repo;
    }

    private static Testit parseTestit(Map<String, Object> testit) {
        rejectUnknownKeys(testit, "/testit", "project_id", "adapter_ver");
        return new Testit(optionalText(testit, "/testit", "project_id"), optionalText(testit, "/testit", "adapter_ver"));
    }

    private static Run parseRun(Map<String, Object> run) {
        rejectUnknownKeys(run, "/run", "command", "timeout");
        List<String> command = strings(required(run, "/run", "command"), "/run/command", Integer.MAX_VALUE);
        if (command.isEmpty()) {
            throw invalid("/run/command", "список не может быть пустым");
        }
        return new Run(command, optionalTimeout(run));
    }

    private static Duration optionalTimeout(Map<String, Object> run) {
        if (!run.containsKey("timeout")) {
            return DEFAULT_TIMEOUT;
        }
        if (!(run.get("timeout") instanceof String text)) {
            throw invalid("/run/timeout", "ожидалась строка с длительностью ISO-8601, например PT45M");
        }
        Duration timeout;
        try {
            timeout = Duration.parse(text);
        } catch (DateTimeParseException e) {
            throw invalid("/run/timeout", "не длительность ISO-8601, например PT45M");
        }
        if (timeout.isZero() || timeout.isNegative() || timeout.compareTo(MAX_TIMEOUT) > 0) {
            throw invalid("/run/timeout", "должна быть больше нуля и не больше " + MAX_TIMEOUT);
        }
        return timeout;
    }

    private static Env parseEnv(Map<String, Object> env) {
        rejectUnknownKeys(env, "/env", "restricted");
        if (!(required(env, "/env", "restricted") instanceof Boolean restricted)) {
            throw invalid("/env/restricted", "ожидалось true или false");
        }
        return new Env(restricted);
    }

    private static DataPolicy parseDataPolicy(Map<String, Object> policy) {
        rejectUnknownKeys(policy, "/data_policy", "mask", "never_send");
        List<String> mask = optionalStrings(policy, "/data_policy", "mask", MAX_PATTERNS);
        for (int i = 0; i < mask.size(); i++) {
            checkMask(mask.get(i), "/data_policy/mask/" + i);
        }
        List<String> neverSend = optionalStrings(policy, "/data_policy", "never_send", MAX_PATTERNS);
        for (int i = 0; i < neverSend.size(); i++) {
            checkGlob(neverSend.get(i), "/data_policy/never_send/" + i);
        }
        return new DataPolicy(mask, neverSend);
    }

    private static void checkMask(String regex, String path) {
        if (regex.length() > MAX_MASK_LENGTH) {
            throw invalid(path, "длиннее " + MAX_MASK_LENGTH + " символов");
        }
        Pattern pattern;
        try {
            pattern = Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            throw invalid(path, e.getIndex() >= 0 ? "не регулярное выражение (позиция " + e.getIndex() + ")" : "не регулярное выражение");
        }
        if (pattern.matcher("").find()) {
            throw invalid(path, "совпадает с пустой строкой, замаскировало бы всё");
        }
    }

    private static void checkGlob(String glob, String path) {
        boolean relative = !glob.startsWith("/") && !glob.contains("\\") && !DRIVE_LETTER.matcher(glob).matches()
                && !Arrays.asList(glob.split("/", -1)).contains("..");
        if (!relative) {
            throw invalid(path, "нужен относительный путь с «/» и без «..»");
        }
        try {
            FileSystems.getDefault().getPathMatcher("glob:" + glob);
        } catch (PatternSyntaxException e) {
            throw invalid(path, "не glob-шаблон");
        }
    }

    // --- чтение дерева

    private static Map<String, Object> asObject(Object node, String path) {
        if (!(node instanceof Map<?, ?> map)) {
            throw invalid(path, "ожидался объект");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw invalid(path, "ключ должен быть строкой");   // YAML читает ключи on:, no: как Boolean
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    private static void rejectUnknownKeys(Map<String, Object> map, String path, String... known) {
        Set<String> allowed = Set.of(known);
        for (String key : map.keySet()) {
            if (!allowed.contains(key)) {
                throw invalid(path + "/" + printable(key), "неизвестный ключ");
            }
        }
    }

    private static Object required(Map<String, Object> map, String path, String key) {
        if (!map.containsKey(key)) {
            throw invalid(path + "/" + key, "обязательное поле отсутствует");
        }
        return map.get(key);
    }

    private static Map<String, Object> requiredObject(Map<String, Object> map, String path, String key) {
        return asObject(required(map, path, key), path + "/" + key);
    }

    private static Map<String, Object> optionalObject(Map<String, Object> map, String path, String key) {
        return map.containsKey(key) ? asObject(map.get(key), path + "/" + key) : Map.of();
    }

    private static String requiredText(Map<String, Object> map, String path, String key) {
        return text(required(map, path, key), path + "/" + key);
    }

    private static String optionalText(Map<String, Object> map, String path, String key) {
        return map.containsKey(key) ? text(map.get(key), path + "/" + key) : null;
    }

    private static String text(Object value, String path) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw invalid(path, "ожидалась непустая строка");
        }
        return text;
    }

    private static List<String> optionalStrings(Map<String, Object> map, String path, String key, int maxItems) {
        return map.containsKey(key) ? strings(map.get(key), path + "/" + key, maxItems) : List.of();
    }

    private static List<String> strings(Object value, String path, int maxItems) {
        if (!(value instanceof List<?> list)) {
            throw invalid(path, "ожидался список строк");
        }
        if (list.size() > maxItems) {
            throw invalid(path, "не более " + maxItems + " элементов");
        }
        List<String> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            result.add(text(list.get(i), path + "/" + i));
        }
        return result;
    }

    private static InvalidProfileException invalid(String path, String problem) {
        return new InvalidProfileException((path.isEmpty() ? "/" : path) + ": " + problem);
    }

    /** Имя ключа попадает в сообщение: обрезаем и убираем управляющие символы (сообщение уходит в журнал). */
    private static String printable(String key) {
        String cleaned = key.replaceAll("\\p{Cntrl}", "?");
        return cleaned.length() > 40 ? cleaned.substring(0, 40) + "…" : cleaned;
    }
}
