package uz.sbg.kmreplacement.state;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Потокобезопасное in-memory хранилище состояний замены.
 *
 * <p>Используется {@link ConcurrentHashMap} — подходит для сценария
 * "редкий write (1-2 на скан), редкий read (1 на скан), редкий scan (1 раз в секунду)".
 * Никаких блокировок снаружи не нужно.</p>
 */
public final class InMemoryReplacementStateRepository implements ReplacementStateRepository {

    private final Map<CorrelationKey, ReplacementState> store = new ConcurrentHashMap<CorrelationKey, ReplacementState>();

    @Override
    public ReplacementState find(CorrelationKey key) {
        if (key == null) return null;
        return store.get(key);
    }

    @Override
    public void save(ReplacementState state) {
        if (state == null || state.getCorrelationKey() == null) return;
        store.put(state.getCorrelationKey(), state);
    }

    @Override
    public void remove(CorrelationKey key) {
        if (key == null) return;
        store.remove(key);
    }

    @Override
    public void removeByReceipt(int receiptNumber) {
        // ConcurrentHashMap итерация безопасна от ConcurrentModificationException.
        for (Map.Entry<CorrelationKey, ReplacementState> e : store.entrySet()) {
            if (e.getValue().getReceiptNumber() == receiptNumber) {
                store.remove(e.getKey(), e.getValue());
            }
        }
    }

    @Override
    public List<ReplacementState> findExpired(long nowMs) {
        List<ReplacementState> result = new ArrayList<ReplacementState>();
        for (ReplacementState s : store.values()) {
            if (s.getStatus() == Status.QR_SHOWN && s.isExpired(nowMs)) {
                result.add(s);
            }
        }
        return result;
    }

    @Override
    public List<ReplacementState> findTerminalOlderThan(long cutoffMs) {
        List<ReplacementState> result = new ArrayList<ReplacementState>();
        for (ReplacementState s : store.values()) {
            if (s.isTerminal() && s.getCreatedAtMs() < cutoffMs) {
                result.add(s);
            }
        }
        return result;
    }

    @Override
    public int size() {
        return store.size();
    }
}
