package uz.sbg.kmreplacement.state;

import java.util.List;

/**
 * Абстракция межвызовного хранилища состояний замены.
 *
 * <p>Ключ хранения записи — пара {@code (CorrelationKey, attemptIndex)}.
 * В одном {@code CorrelationKey} (одна тройка shop|pos|receipt|gtin) может
 * храниться несколько записей — по одной на каждую попытку замены (например,
 * 4 одинаковые кока-колы с 4 разными КМ в одном чеке).</p>
 *
 * <p>По умолчанию — in-memory (см. {@link InMemoryReplacementStateRepository}).
 * При перезапуске кассы состояние теряется — это ОК: чек всё равно откатится.</p>
 */
public interface ReplacementStateRepository {

    /**
     * Все записи по данному базовому ключу (во всех статусах). Пустой список,
     * если записей нет. Не null.
     */
    List<ReplacementState> findAll(CorrelationKey key);

    /**
     * Найти активную QR_SHOWN запись, у которой {@code replacementKm == scannedKm}
     * (глобальный поиск по всему хранилищу). Используется на шаге "кассир
     * отсканировал заменяющий КМ" — записи по произвольному чеку/позиции
     * идентифицируются по содержимому КМ.
     *
     * @return запись или null, если не найдено
     */
    ReplacementState findQrShownByReplacement(String scannedKm);

    /**
     * Найти активную QR_SHOWN запись внутри заданного базового ключа, у которой
     * {@code originalKm == scannedKm}. Используется для идемпотентной обработки
     * повторного скана того же "плохого" КМ при уже висящем оверлее.
     *
     * @return запись или null
     */
    ReplacementState findQrShownByOriginalInBase(CorrelationKey key, String scannedKm);

    /** Сохранить (create/update) запись. Идентификатор — {@code (key, attemptIndex)}. */
    void save(ReplacementState state);

    /** Удалить запись по паре {@code (key, attemptIndex)}. */
    void remove(CorrelationKey key, int attemptIndex);

    /** Все записи, принадлежащие данному чеку (во всех статусах). */
    List<ReplacementState> findByReceipt(int receiptNumber);

    /** Удалить все записи, у которых {@code receiptNumber} совпадает. */
    void removeByReceipt(int receiptNumber);

    /** Все записи в активном состоянии QR_SHOWN с {@code expiresAt &lt;= nowMs}. */
    List<ReplacementState> findExpired(long nowMs);

    /** Все терминальные записи, созданные раньше, чем cutoffMs (для очистки). */
    List<ReplacementState> findTerminalOlderThan(long cutoffMs);

    int size();
}
