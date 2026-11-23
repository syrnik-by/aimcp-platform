package by.syrnik.aimcp.core.profile;

/**
 * Профиль не загружен: файл нечитаем или нарушает контракт. Сообщение содержит путь к полю и условие, но не значения.
 */
public class InvalidProfileException extends RuntimeException {

    public InvalidProfileException(String message) {
        super(message);
    }

    public InvalidProfileException(String message, Throwable cause) {
        super(message, cause);
    }
}
