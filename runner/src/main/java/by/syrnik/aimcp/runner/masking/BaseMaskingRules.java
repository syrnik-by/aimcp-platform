package by.syrnik.aimcp.runner.masking;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Базовый список маскирования секретов, docs/masking-rules.md. Все квантификаторы ограничены сверху: так правила
 * не дают квадратичного времени на длинных повторяющихся входах. Новое правило проверяется на злом входе.
 */
public final class BaseMaskingRules {

    private static final List<MaskingRule> RULES = List.of(
            rule("private-key", "-----BEGIN [A-Z ]{0,30}PRIVATE KEY-----[A-Za-z0-9+/=\\s]*(?:-----END [A-Z ]{0,30}PRIVATE KEY-----)?"),
            rule("jwt", "\\beyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]*"),
            rule("authorization", "(?i)\\b(?:proxy-)?authorization\\b[\"']?\\s*[:=]\\s*[\"']?(?<secret>[^\\r\\n\"']+)"),
            rule("bearer", "(?i)\\bbearer\\s+(?<secret>[A-Za-z0-9._~+/=-]{8,})"),
            rule("url-credentials", "(?i)\\b[a-z][a-z0-9+.-]{0,19}://[^\\s/:@]+:(?<secret>[^\\s/@]+)@"),
            rule("secret-keyword", "(?i)(?:password|passwd|pwd|secret|token|api[_-]?key|access[_-]?key)[\\w-]{0,20}[\"']?\\s*[:=]\\s*[\"']?(?<secret>[^\\s\"',;&]+)"),
            rule("cookie", "(?i)\\b(?:set-)?cookie\\b\\s*:\\s*(?<secret>[^\\r\\n]+)"));

    private BaseMaskingRules() {
    }

    public static List<MaskingRule> rules() {
        return RULES;
    }

    private static MaskingRule rule(String name, String regex) {
        return new MaskingRule(name, Pattern.compile(regex));
    }
}
