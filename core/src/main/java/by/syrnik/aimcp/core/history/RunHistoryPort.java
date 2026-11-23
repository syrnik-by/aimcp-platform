package by.syrnik.aimcp.core.history;

import by.syrnik.aimcp.core.history.records.RunInfo;
import by.syrnik.aimcp.core.history.records.RunKey;
import by.syrnik.aimcp.core.history.records.RunSummary;
import by.syrnik.aimcp.core.history.records.TestResult;

import java.util.Optional;
import java.util.List;

/**
 * История прогонов. Прогон — логический запуск набора тестов проекта; его части (джобы CI на разных ВМ)
 * загружаются отдельными вызовами {@link #save}. Аргументы всех методов не равны {@code null}.
 */
public interface RunHistoryPort {

    /**
     * Сохраняет результаты части прогона. Возвращает число результатов, добавленных впервые.
     * <ul>
     *   <li>Прогон создаётся первым вызовом с данным ключом, тогда же фиксируется {@code receivedAt};
     *       следующие вызовы дополняют прогон.</li>
     *   <li>Результат с уже известным в этом прогоне {@code uuid} пропускается: повторная загрузка
     *       того же файла безопасна.</li>
     *   <li>Поля {@link RunInfo}: остаётся первое непустое значение, пустое дополняется следующими вызовами.</li>
     *   <li>Одинаковый {@code runId} в разных проектах — разные прогоны.</li>
     * </ul>
     */
    int save(RunKey key, RunInfo info, List<TestResult> results);

    /**
     * Сводка прогона; пусто, если прогона нет. {@code startedAt} — самое раннее начало, {@code finishedAt} —
     * самое позднее окончание среди его тестов (тесты без времени не учитываются; если времени нет ни у одного — {@code null}).
     */
    Optional<RunSummary> findRun(RunKey key);

    /** Все результаты прогона по возрастанию времени начала (без времени — в конце), затем по uuid; для неизвестного прогона — пустой список. */
    List<TestResult> findResults(RunKey key);
}
