package uz.sbg.kmreplacement.lifecycle;

import uz.sbg.kmreplacement.state.CorrelationKey;

/**
 * Неизменяемый результат работы {@link StateMachine}. Сообщает плагину:
 * какой ответ выдать кассе, и нужно ли обновить/закрыть оверлей.
 */
public final class Decision {

    private final DecisionKind kind;
    private final CorrelationKey correlationKey;
    private final String message;          // текст сообщения для кассы (только для REJECT)
    private final String replacementKm;    // КМ замены, если нужно показать overlay

    private Decision(DecisionKind kind,
                     CorrelationKey key,
                     String message,
                     String replacementKm) {
        this.kind = kind;
        this.correlationKey = key;
        this.message = message;
        this.replacementKm = replacementKm;
    }

    public static Decision accept(CorrelationKey key) {
        return new Decision(DecisionKind.ACCEPT, key, null, null);
    }

    public static Decision acceptCloseOverlay(CorrelationKey key) {
        return new Decision(DecisionKind.ACCEPT_CLOSE_OVERLAY, key, null, null);
    }

    public static Decision reject(CorrelationKey key, String message) {
        return new Decision(DecisionKind.REJECT, key, message, null);
    }

    public static Decision rejectShowOverlay(CorrelationKey key,
                                             String message,
                                             String replacementKm) {
        return new Decision(DecisionKind.REJECT, key, message, replacementKm);
    }

    public DecisionKind    getKind()           { return kind; }
    public CorrelationKey  getCorrelationKey() { return correlationKey; }
    public String          getMessage()        { return message; }
    public String          getReplacementKm()  { return replacementKm; }

    public boolean isAccept()          { return kind != DecisionKind.REJECT; }
    public boolean shouldShowOverlay() { return replacementKm != null; }
    public boolean shouldCloseOverlay(){ return kind == DecisionKind.ACCEPT_CLOSE_OVERLAY; }

    @Override
    public String toString() {
        return "Decision{" + kind + ", key=" + correlationKey
                + (replacementKm != null ? ", replacement=(set)" : "")
                + (message != null ? ", message='" + message + "'" : "") + "}";
    }
}
