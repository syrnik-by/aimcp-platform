package by.syrnik.aimcp.runner.masking;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Базовый список docs/masking-rules.md: что маскируется, что остаётся как есть, повторное применение ничего не
 * меняет, враждебные входы укладываются в бюджет.
 */
class BaseMaskingRulesTest {

    private final RegexMaskingPort port = new RegexMaskingPort(BaseMaskingRules.rules(), Duration.ofSeconds(5));

    @Test
    void ruleNamesAreTheListFromTheContract() {
        assertThat(BaseMaskingRules.rules()).extracting(MaskingRule::name).containsExactly(
                "private-key", "jwt", "authorization", "bearer", "url-credentials", "secret-keyword", "cookie");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("maskedExamples")
    void secretsAreReplacedByPlaceholders(String title, String text, String expected) {
        assertThat(port.mask(text)).isEqualTo(expected);
    }

    static Stream<Arguments> maskedExamples() {
        return Stream.of(
                example("заголовок Authorization", "Authorization: Bearer abcdef0123456789", "Authorization: [MASKED:authorization]"),
                example("authorization в кавычках curl", "curl -H 'authorization=Basic dXNlcjpwYXNz'", "curl -H 'authorization=[MASKED:authorization]'"),
                example("Proxy-Authorization", "Proxy-Authorization: Basic dXNlcjpwYXNz", "Proxy-Authorization: [MASKED:authorization]"),
                example("Bearer в тексте", "token received Bearer abcdef0123456789xyz ok", "token received Bearer [MASKED:bearer] ok"),
                example("bearer строчными", "got bearer abcdef0123456789 here", "got bearer [MASKED:bearer] here"),
                example("пароль в адресе", "GET https://svc:p4ss@host.example.invalid/api", "GET https://svc:[MASKED:url-credentials]@host.example.invalid/api"),
                example("password в JSON", "{\"password\": \"hunter2\", \"user\": \"bob\"}", "{\"password\": \"[MASKED:secret-keyword]\", \"user\": \"bob\"}"),
                example("password в properties", "db.password=hunter2 timeout=5", "db.password=[MASKED:secret-keyword] timeout=5"),
                example("password с пробелами и кавычками", "password = 'hunter2'", "password = '[MASKED:secret-keyword]'"),
                example("верхний регистр", "PASSWORD=Abc1", "PASSWORD=[MASKED:secret-keyword]"),
                example("camelCase и snake_case", "authToken=abc123 apiKey: xyz789 client_secret=q",
                        "authToken=[MASKED:secret-keyword] apiKey: [MASKED:secret-keyword] client_secret=[MASKED:secret-keyword]"),
                example("несколько секретов в строке", "password=a&token=b", "password=[MASKED:secret-keyword]&token=[MASKED:secret-keyword]"),
                example("секрет не из ASCII", "Ошибка входа, password=секрет123", "Ошибка входа, password=[MASKED:secret-keyword]"),
                example("секреты на разных строках", "line1\npassword=x\nline3\ntoken: y\n", "line1\npassword=[MASKED:secret-keyword]\nline3\ntoken: [MASKED:secret-keyword]\n"),
                example("jwt", "jwt eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.c2lnbmF0dXJl end", "jwt [MASKED:jwt] end"),
                example("Set-Cookie", "Set-Cookie: JSESSIONID=abc; Path=/", "Set-Cookie: [MASKED:cookie]"),
                example("cookie строчными", "cookie: a=b", "cookie: [MASKED:cookie]"),
                example("закрытый ключ целиком", "-----BEGIN RSA PRIVATE KEY-----\nMIIEow\nIBAAKC\n-----END RSA PRIVATE KEY-----\nafter", "[MASKED:private-key]\nafter"),
                example("закрытый ключ в обрезанном логе", "-----BEGIN PRIVATE KEY-----\nMIIEow\nIBAAKC", "[MASKED:private-key]"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unchangedExamples")
    void textWithoutSecretsIsNotChanged(String title, String text) {
        assertThat(port.mask(text)).isEqualTo(text);
    }

    static Stream<Arguments> unchangedExamples() {
        return Stream.of(
                Arguments.of("строка журнала", "2026-11-11 10:40:39.601 INFO  PaymentTest - Открыть форму платежа, stand=test, status=PASSED"),
                Arguments.of("строка стектрейса", "at ru.example.payments.PaymentTest.paymentIsAccepted(PaymentTest.java:42)"),
                Arguments.of("сообщение проверки", "expected: <200> but was: <500>"),
                Arguments.of("время в миллисекундах", "startMs 1760000000000 stopMs 1760000004200"),
                Arguments.of("слово password без значения", "wrong password entered, try again"),
                Arguments.of("короткий Bearer", "Bearer abc"),
                Arguments.of("адрес без логина", "https://host.example.invalid/api?x=1"),
                Arguments.of("ПДн базовым списком не маскируются",
                        "card 4111 1111 1111 1111, mail ivan.petrov@example.invalid, phone +7 (495) 123-45-67"),
                Arguments.of("ключ облака без слова-ключа рядом", "key AKIAIOSFODNN7EXAMPLE here"),
                Arguments.of("пустая строка", ""));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("maskedExamples")
    void maskingTwiceGivesTheSameAsOnce(String title, String text, String expected) {
        assertThat(port.mask(expected)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("hostileInputs")
    void hostileInputIsProcessedWithinTheBudget(String title, String text) {
        // бюджет 5 секунд; исправное правило тратит на 300 КБ десятые доли секунды, а при неограниченном
        // квантификаторе падает с MaskingException
        assertThat(port.mask(text)).isNotNull();
    }

    static Stream<Arguments> hostileInputs() {
        return Stream.of(
                Arguments.of("цифры", "1".repeat(300_000)),
                Arguments.of("буквы", "a".repeat(300_000)),
                Arguments.of("a.a.a.", "a.".repeat(150_000)),
                Arguments.of("a@a@", "a@".repeat(150_000)),
                Arguments.of("пробелы", " ".repeat(300_000)),
                Arguments.of("password password", "password".repeat(40_000)),
                Arguments.of("password=", "password=".repeat(30_000)),
                Arguments.of("token: ", "token: ".repeat(40_000)),
                Arguments.of("цифры с пробелами", "1 ".repeat(150_000)),
                Arguments.of("BEGIN без END", "-----BEGIN PRIVATE KEY-----\nAAAA\n".repeat(10_000)),
                Arguments.of("ключ без конца", "-----BEGIN PRIVATE KEY-----" + "A".repeat(300_000)),
                Arguments.of("eyJ", "eyJaaaaa.".repeat(30_000)),
                Arguments.of("a://b:", "a://b:".repeat(50_000)),
                Arguments.of("http://", "http://".repeat(40_000)),
                Arguments.of("длинная схема", "a".repeat(300_000) + "://"),
                Arguments.of("token и пробелы", ("token" + " ".repeat(20_000)).repeat(15)),
                Arguments.of("authorization:", "authorization:".repeat(20_000)),
                Arguments.of("cookie:", "cookie:".repeat(40_000)),
                Arguments.of("bearer ", "bearer ".repeat(40_000)));
    }

    private static Arguments example(String title, String text, String expected) {
        return Arguments.of(title, text, expected);
    }
}
