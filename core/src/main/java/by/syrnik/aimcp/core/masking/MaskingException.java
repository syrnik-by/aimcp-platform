package by.syrnik.aimcp.core.masking;

/**
 * Маскирование не выполнено. Сообщение не содержит фрагментов текста: оно уходит в журнал.
 */
public class MaskingException extends RuntimeException {

    public MaskingException(String message) {
        super(message);
    }

    public MaskingException(String message, Throwable cause) {
        super(message, cause);
    }
}
