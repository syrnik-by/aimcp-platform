package by.syrnik.aimcp.runner.masking;

import by.syrnik.aimcp.core.masking.MaskingException;
import by.syrnik.aimcp.core.masking.MaskingPort;
import by.syrnik.aimcp.core.profile.DataPolicy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Маскирование регулярными выражениями (docs/masking-rules.md): правила по очереди, каждое применяется к результату
 * предыдущего. Закрыто по умолчанию: любой сбой, в том числе исчерпанный бюджет времени, даёт {@link MaskingException}.
 * Потокобезопасно: состояния между вызовами нет.
 */
public class RegexMaskingPort implements MaskingPort {

    private static final String SECRET_GROUP = "secret";
    private static final Duration MAX_BUDGET = Duration.ofHours(1);

    private final List<MaskingRule> rules;
    private final Duration budget;

    /**
     * @param rules  правила в порядке применения
     * @param budget предельное время одного вызова {@link #mask}: больше нуля и не больше часа
     */
    public RegexMaskingPort(List<MaskingRule> rules, Duration budget) {
        this.rules = List.copyOf(rules);
        Objects.requireNonNull(budget, "budget");
        if (budget.isNegative() || budget.isZero() || budget.compareTo(MAX_BUDGET) > 0) {
            throw new IllegalArgumentException("Бюджет времени должен быть больше нуля и не больше часа");
        }
        this.budget = budget;
    }

    /** Базовый список, затем шаблоны профиля проекта под именами {@code project-1}, {@code project-2}, ... */
    public static RegexMaskingPort forPolicy(DataPolicy policy, Duration budget) {
        List<MaskingRule> rules = new ArrayList<>(BaseMaskingRules.rules());
        List<String> patterns = policy.mask();
        for (int i = 0; i < patterns.size(); i++) {
            String name = "project-" + (i + 1);
            try {
                rules.add(new MaskingRule(name, Pattern.compile(patterns.get(i))));
            } catch (PatternSyntaxException e) {
                throw new IllegalArgumentException("Шаблон " + name + " не компилируется");
            }
        }
        return new RegexMaskingPort(rules, budget);
    }

    @Override
    public String mask(String text) {
        Objects.requireNonNull(text, "text");
        long deadline = System.nanoTime() + budget.toNanos();
        String result = text;
        try {
            for (MaskingRule rule : rules) {
                result = apply(rule, result, deadline);
                if (System.nanoTime() - deadline > 0) {
                    throw new MaskingException(Deadline.EXPIRED);
                }
            }
        } catch (MaskingException e) {
            throw e;
        } catch (StackOverflowError e) {
            throw new MaskingException("маскирование не выполнено: слишком глубокая рекурсия регулярного выражения");
        } catch (RuntimeException e) {
            // без cause: в сообщении исключения может оказаться фрагмент текста
            throw new MaskingException("маскирование не выполнено: " + e.getClass().getSimpleName());
        }
        return result;
    }

    private static String apply(MaskingRule rule, String text, long deadline) {
        Matcher matcher = rule.pattern().matcher(new Deadline(text, deadline));
        StringBuilder out = null;
        int last = 0;
        boolean hasSecretGroup = true;
        while (matcher.find()) {
            int from = matcher.start();
            int to = matcher.end();
            if (hasSecretGroup) {
                try {
                    from = matcher.start(SECRET_GROUP);
                    to = matcher.end(SECRET_GROUP);
                } catch (IllegalArgumentException e) {
                    hasSecretGroup = false;   // группы secret нет: маскируем всё совпадение
                }
            }
            if (from == to) {
                continue;   // пустое совпадение; группа secret, не участвовавшая в совпадении, даёт start = end = -1
            }
            if (out == null) {
                out = new StringBuilder(text.length() + 32);
            }
            out.append(text, last, from).append("[MASKED:").append(rule.name()).append(']');
            last = to;
        }
        return out == null ? text : out.append(text, last, text.length()).toString();
    }
}
