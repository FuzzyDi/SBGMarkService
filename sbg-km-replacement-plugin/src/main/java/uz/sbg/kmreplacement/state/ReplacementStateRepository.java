package uz.sbg.kmreplacement.state;

import java.util.List;

/**
 * Абстракция межвызовного хранилища состояний.
 * По умолчанию — in-memory (см. {@link InMemoryReplacementStateRepository}).
 * При перезапуске кассы состояние теряется — это ОК: чек всё равно откатится.
 */
public interface ReplacementStateRepository {

    ReplacementState find(CorrelationKey key);

    void save(ReplacementState state);

    void remove(CorrelationKey key);

    void removeByReceipt(int receiptNumber);

    /** Все записи в активном состоянии QR_SHOWN с expiresAt &lt;= nowMs. */
    List<ReplacementState> findExpired(long nowMs);

    /** Все терминальные записи, созданные раньше, чем cutoffMs (для очистки). */
    List<ReplacementState> findTerminalOlderThan(long cutoffMs);

    int size();
}
