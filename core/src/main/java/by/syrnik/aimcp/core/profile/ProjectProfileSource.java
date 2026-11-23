package by.syrnik.aimcp.core.profile;

/**
 * Источник профиля проекта. Реализации: файл {@code at-profile.yaml} (runner), заглушка с готовым профилем (adapters-stub).
 */
public interface ProjectProfileSource {

    /**
     * Загружает профиль. Закрыто по умолчанию: любая ошибка означает, что профиля нет, «частично верного» профиля не бывает.
     *
     * @throws InvalidProfileException источник недоступен или профиль нарушает контракт
     */
    ProjectProfile load();
}
