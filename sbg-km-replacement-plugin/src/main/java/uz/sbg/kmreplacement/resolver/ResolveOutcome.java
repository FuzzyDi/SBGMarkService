package uz.sbg.kmreplacement.resolver;

/**
 * Результат проверки КМ в {@link ReplacementResolver}.
 *
 * <p>Значимые ветки:</p>
 * <ul>
 *   <li>{@link Kind#VALID} — КМ валиден, касса должна его принять как есть.</li>
 *   <li>{@link Kind#REPLACE_WITH} — КМ невалиден, но есть замена: {@link #getReplacementKm()}.</li>
 *   <li>{@link Kind#UNAVAILABLE} — КМ невалиден и заменить нечем; касса отвергает без оверлея.</li>
 *   <li>{@link Kind#ERROR} — ошибка связи / бекенда; касса отвергает с диагностическим сообщением.</li>
 * </ul>
 *
 * <p>Опционально содержит {@link #getReservationId()} — идентификатор
 * резерва на бекенде (выдаётся для {@link Kind#VALID} и {@link Kind#REPLACE_WITH}
 * реальным HTTP-резолвером и нужен в fiscalized/cancelled-колбэках плагина,
 * чтобы вызвать {@code /sold-confirm} или {@code /sale-release}). Stub-резолвер
 * резерв не создаёт и поле оставляет {@code null}.</p>
 */
public final class ResolveOutcome {

    public enum Kind { VALID, REPLACE_WITH, UNAVAILABLE, ERROR }

    private final Kind   kind;
    private final String replacementKm;
    private final String message;
    private final String reservationId;

    private ResolveOutcome(Kind kind, String replacementKm, String message, String reservationId) {
        this.kind = kind;
        this.replacementKm = replacementKm;
        this.message = message;
        this.reservationId = reservationId;
    }

    // --- фабричные методы без reservationId (используется в stub/тестах) ---
    public static ResolveOutcome valid()                 { return new ResolveOutcome(Kind.VALID,        null, null, null); }
    public static ResolveOutcome replaceWith(String km)  { return new ResolveOutcome(Kind.REPLACE_WITH, km,   null, null); }
    public static ResolveOutcome unavailable(String msg) { return new ResolveOutcome(Kind.UNAVAILABLE,  null, msg,  null); }
    public static ResolveOutcome error(String msg)       { return new ResolveOutcome(Kind.ERROR,        null, msg,  null); }

    // --- фабричные методы с reservationId (HTTP-резолвер) ---
    public static ResolveOutcome valid(String reservationId) {
        return new ResolveOutcome(Kind.VALID, null, null, reservationId);
    }
    public static ResolveOutcome replaceWith(String km, String reservationId) {
        return new ResolveOutcome(Kind.REPLACE_WITH, km, null, reservationId);
    }

    public Kind   getKind()          { return kind; }
    public String getReplacementKm() { return replacementKm; }
    public String getMessage()       { return message; }
    public String getReservationId() { return reservationId; }
}
