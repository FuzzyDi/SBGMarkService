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
 */
public final class ResolveOutcome {

    public enum Kind { VALID, REPLACE_WITH, UNAVAILABLE, ERROR }

    private final Kind kind;
    private final String replacementKm;
    private final String message;

    private ResolveOutcome(Kind kind, String replacementKm, String message) {
        this.kind = kind;
        this.replacementKm = replacementKm;
        this.message = message;
    }

    public static ResolveOutcome valid()                     { return new ResolveOutcome(Kind.VALID, null, null); }
    public static ResolveOutcome replaceWith(String km)      { return new ResolveOutcome(Kind.REPLACE_WITH, km, null); }
    public static ResolveOutcome unavailable(String msg)     { return new ResolveOutcome(Kind.UNAVAILABLE, null, msg); }
    public static ResolveOutcome error(String msg)           { return new ResolveOutcome(Kind.ERROR, null, msg); }

    public Kind   getKind()          { return kind; }
    public String getReplacementKm() { return replacementKm; }
    public String getMessage()       { return message; }
}
