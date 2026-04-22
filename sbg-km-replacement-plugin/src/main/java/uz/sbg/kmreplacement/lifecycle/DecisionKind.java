package uz.sbg.kmreplacement.lifecycle;

/**
 * Типы решений state machine.
 *
 * <ul>
 *   <li>{@link #ACCEPT} — отдать кассе ACCEPT по текущему КМ. Оверлей не трогается.</li>
 *   <li>{@link #ACCEPT_CLOSE_OVERLAY} — ACCEPT + закрыть оверлей (успешный повторный скан B).</li>
 *   <li>{@link #REJECT} — REJECT кассе. Оверлей показывается/обновляется параллельно
 *       (если решение — предложить замену B).</li>
 * </ul>
 */
public enum DecisionKind {
    ACCEPT,
    ACCEPT_CLOSE_OVERLAY,
    REJECT
}
