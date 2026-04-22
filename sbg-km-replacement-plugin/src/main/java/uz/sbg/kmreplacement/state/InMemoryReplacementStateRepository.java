package uz.sbg.kmreplacement.state;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Потокобезопасное in-memory хранилище состояний замены.
 *
 * <p>Структура: {@code Map<CorrelationKey, Map<Integer, ReplacementState>>}. Оба
 * уровня — {@link ConcurrentHashMap}. Первый уровень — базовый ключ
 * (shop|pos|receipt|gtin), второй — {@code attemptIndex} → запись.</p>
 *
 * <p>Подходит для сценария "редкий write (1-2 на скан), редкий read (1 на скан)".
 * Никаких блокировок снаружи не нужно.</p>
 */
public final class InMemoryReplacementStateRepository implements ReplacementStateRepository {

    private final Map<CorrelationKey, Map<Integer, ReplacementState>> store =
            new ConcurrentHashMap<CorrelationKey, Map<Integer, ReplacementState>>();

    @Override
    public List<ReplacementState> findAll(CorrelationKey key) {
        if (key == null) return Collections.emptyList();
        Map<Integer, ReplacementState> bucket = store.get(key);
        if (bucket == null || bucket.isEmpty()) return Collections.emptyList();
        return new ArrayList<ReplacementState>(bucket.values());
    }

    @Override
    public ReplacementState findQrShownByReplacement(String scannedKm) {
        if (scannedKm == null || scannedKm.isEmpty()) return null;
        for (Map<Integer, ReplacementState> bucket : store.values()) {
            for (ReplacementState s : bucket.values()) {
                if (s.getStatus() == Status.QR_SHOWN && scannedKm.equals(s.getReplacementKm())) {
                    return s;
                }
            }
        }
        return null;
    }

    @Override
    public ReplacementState findQrShownByOriginalInBase(CorrelationKey key, String scannedKm) {
        if (key == null || scannedKm == null || scannedKm.isEmpty()) return null;
        Map<Integer, ReplacementState> bucket = store.get(key);
        if (bucket == null) return null;
        for (ReplacementState s : bucket.values()) {
            if (s.getStatus() == Status.QR_SHOWN && scannedKm.equals(s.getOriginalKm())) {
                return s;
            }
        }
        return null;
    }

    @Override
    public ReplacementState findAcceptedByOriginalInBase(CorrelationKey key, String scannedKm) {
        if (key == null || scannedKm == null || scannedKm.isEmpty()) return null;
        Map<Integer, ReplacementState> bucket = store.get(key);
        if (bucket == null) return null;
        for (ReplacementState s : bucket.values()) {
            if (s.getStatus() == Status.REPLACEMENT_ACCEPTED && scannedKm.equals(s.getOriginalKm())) {
                return s;
            }
        }
        return null;
    }

    @Override
    public void save(ReplacementState state) {
        if (state == null || state.getCorrelationKey() == null) return;
        CorrelationKey key = state.getCorrelationKey();
        Map<Integer, ReplacementState> bucket = store.get(key);
        if (bucket == null) {
            bucket = new ConcurrentHashMap<Integer, ReplacementState>();
            Map<Integer, ReplacementState> prev = store.putIfAbsent(key, bucket);
            if (prev != null) bucket = prev;
        }
        bucket.put(state.getAttemptIndex(), state);
    }

    @Override
    public void remove(CorrelationKey key, int attemptIndex) {
        if (key == null) return;
        Map<Integer, ReplacementState> bucket = store.get(key);
        if (bucket == null) return;
        bucket.remove(attemptIndex);
        if (bucket.isEmpty()) {
            store.remove(key, bucket);
        }
    }

    @Override
    public List<ReplacementState> findByReceipt(int receiptNumber) {
        List<ReplacementState> result = new ArrayList<ReplacementState>();
        for (Map<Integer, ReplacementState> bucket : store.values()) {
            for (ReplacementState s : bucket.values()) {
                if (s.getReceiptNumber() == receiptNumber) {
                    result.add(s);
                }
            }
        }
        return result;
    }

    @Override
    public void removeByReceipt(int receiptNumber) {
        Iterator<Map.Entry<CorrelationKey, Map<Integer, ReplacementState>>> it = store.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<CorrelationKey, Map<Integer, ReplacementState>> e = it.next();
            Map<Integer, ReplacementState> bucket = e.getValue();
            Iterator<Map.Entry<Integer, ReplacementState>> bi = bucket.entrySet().iterator();
            while (bi.hasNext()) {
                if (bi.next().getValue().getReceiptNumber() == receiptNumber) {
                    bi.remove();
                }
            }
            if (bucket.isEmpty()) {
                it.remove();
            }
        }
    }

    @Override
    public List<ReplacementState> findExpired(long nowMs) {
        List<ReplacementState> result = new ArrayList<ReplacementState>();
        for (Map<Integer, ReplacementState> bucket : store.values()) {
            for (ReplacementState s : bucket.values()) {
                if (s.getStatus() == Status.QR_SHOWN && s.isExpired(nowMs)) {
                    result.add(s);
                }
            }
        }
        return result;
    }

    @Override
    public List<ReplacementState> findTerminalOlderThan(long cutoffMs) {
        List<ReplacementState> result = new ArrayList<ReplacementState>();
        for (Map<Integer, ReplacementState> bucket : store.values()) {
            for (ReplacementState s : bucket.values()) {
                if (s.isTerminal() && s.getCreatedAtMs() < cutoffMs) {
                    result.add(s);
                }
            }
        }
        return result;
    }

    @Override
    public int size() {
        int n = 0;
        for (Map<Integer, ReplacementState> bucket : store.values()) {
            n += bucket.size();
        }
        return n;
    }
}
