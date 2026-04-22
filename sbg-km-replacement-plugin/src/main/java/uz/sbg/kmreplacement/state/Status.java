package uz.sbg.kmreplacement.state;

/**
 * Статусы межвызовной записи замены.
 *
 * <pre>
 *  NONE ──(A невалиден, B найден)──▶ QR_SHOWN ──(скан B=VALID)──▶ REPLACEMENT_ACCEPTED
 *                                        │
 *                                        ├──(TTL истёк)──▶ EXPIRED
 *                                        │
 *                                        └──(B тоже невалиден / MAX_ATTEMPTS превышен)──▶ FAILED
 * </pre>
 *
 * <p>Терминальные состояния (REPLACEMENT_ACCEPTED, EXPIRED, FAILED) держатся
 * ещё 5 минут после перехода — для диагностики и защиты от дублирующих сканов —
 * затем удаляются {@code ExpirationScheduler}.</p>
 */
public enum Status {
    NONE,
    QR_SHOWN,
    REPLACEMENT_ACCEPTED,
    EXPIRED,
    FAILED
}
