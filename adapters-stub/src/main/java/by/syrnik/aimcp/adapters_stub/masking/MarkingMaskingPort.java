package by.syrnik.aimcp.adapters_stub.masking;

import by.syrnik.aimcp.core.masking.MaskingPort;

import java.util.Objects;

/** Заглушка для тестов: оборачивает текст в {@code MASKED(...)}, чтобы тест видел, что текст прошёл через порт. */
public class MarkingMaskingPort implements MaskingPort {

    @Override
    public String mask(String text) {
        return "MASKED(" + Objects.requireNonNull(text, "text") + ")";
    }
}
