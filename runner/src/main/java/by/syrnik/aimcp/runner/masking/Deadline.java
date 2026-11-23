package by.syrnik.aimcp.runner.masking;

import by.syrnik.aimcp.core.masking.MaskingException;

/**
 * Текст для регулярного выражения со сроком: движок читает символы через {@code charAt}, и по истечении срока
 * чтение бросает {@link MaskingException}. Так выражение из профиля проекта не может подвесить агента.
 * Время проверяется раз в 1024 чтения, чтобы не замедлять разбор.
 */
final class Deadline implements CharSequence {

    static final String EXPIRED = "маскирование не выполнено: бюджет времени исчерпан";

    private final CharSequence text;
    private final long deadline;
    private int calls;

    Deadline(CharSequence text, long deadline) {
        this.text = text;
        this.deadline = deadline;
    }

    @Override
    public char charAt(int index) {
        if ((++calls & 0x3FF) == 0 && System.nanoTime() - deadline > 0) {
            throw new MaskingException(EXPIRED);
        }
        return text.charAt(index);
    }

    @Override
    public int length() {
        return text.length();
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return new Deadline(text.subSequence(start, end), deadline);
    }

    @Override
    public String toString() {
        return text.toString();
    }
}
