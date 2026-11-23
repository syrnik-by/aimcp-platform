package by.syrnik.aimcp.core.profile;

/** {@code restricted}: {@code true} — логи и стектрейсы остаются на ВМ, {@code false} — после маскирования уходят в тех.зону. */
public record Env(boolean restricted) {
}
