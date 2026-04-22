package uz.sbg.kmreplacement.lifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uz.sbg.kmreplacement.config.KmReplacementConfig;
import uz.sbg.kmreplacement.resolver.ReplacementResolver;
import uz.sbg.kmreplacement.resolver.ResolveContext;
import uz.sbg.kmreplacement.resolver.ResolveOutcome;
import uz.sbg.kmreplacement.state.CorrelationKey;
import uz.sbg.kmreplacement.state.ReplacementState;
import uz.sbg.kmreplacement.state.ReplacementStateRepository;
import uz.sbg.kmreplacement.state.Status;

import java.util.List;

/**
 * Чистая логика state machine — без Swing, без HTTP, без SR10 API.
 * Легко покрывается unit-тестами.
 *
 * <p>Все зависимости передаются в конструктор. {@link Clock} — абстракция
 * над временем, чтобы тесты могли подменять now().</p>
 *
 * <h3>Multi-position (несколько одинаковых товаров в одном чеке)</h3>
 * <p>В пределах одного {@link CorrelationKey} (shop|pos|receipt|gtin) может
 * быть несколько активных/терминальных записей — по одной на каждую
 * позицию-попытку замены (см. {@code ReplacementState.attemptIndex}).
 * Например, 4 одинаковых кока-колы с 4 разными невалидными КМ → 4 записи с
 * {@code attemptIndex = 1,2,3,4}. Идентификация "какую запись закрывать на
 * скан B" выполняется по содержимому КМ:</p>
 * <ul>
 *   <li>Скан валидного КМ → ищем {@code findQrShownByReplacement(scanned)}.
 *       Если нашли — это и есть та самая позиция, закрываем её оверлей.
 *       Если не нашли — обычный ACCEPT (ничей оверлей не трогаем).</li>
 *   <li>Скан невалидного КМ, для которого резолвер предложил замену → сначала
 *       проверяем, не лежит ли он уже в QR_SHOWN с тем же {@code originalKm}
 *       в пределах базового ключа ({@code findQrShownByOriginalInBase}); если
 *       да — идемпотентно показываем тот же overlay. Иначе выделяем новый
 *       {@code attemptIndex} и создаём новую запись.</li>
 * </ul>
 */
public final class StateMachine {

    private static final Logger log = LoggerFactory.getLogger(StateMachine.class);

    private final ReplacementResolver resolver;
    private final ReplacementStateRepository repo;
    private final KmReplacementConfig config;
    private final Clock clock;

    public StateMachine(ReplacementResolver resolver,
                        ReplacementStateRepository repo,
                        KmReplacementConfig config,
                        Clock clock) {
        this.resolver = resolver;
        this.repo = repo;
        this.config = config;
        this.clock = clock;
    }

    public interface Clock {
        long nowMs();
    }

    public Decision onScan(String scannedKm,
                           CorrelationKey key,
                           ResolveContext ctx,
                           int receiptNumber) {
        long now = clock.nowMs();

        // Сбросить устаревшие QR_SHOWN записи для этого базового ключа до принятия решения.
        for (ReplacementState s : repo.findAll(key)) {
            if (s.getStatus() == Status.QR_SHOWN && s.isExpired(now)) {
                log.info("[SBG-KMR-SM] state EXPIRED pre-scan | {}", s);
                s.setStatus(Status.EXPIRED);
                repo.save(s);
            }
        }

        ResolveOutcome outcome = resolver.resolve(scannedKm, ctx);
        log.info("[SBG-KMR-SM] resolve | key={} | scanned='{}' | outcome={}",
                key, abbreviate(scannedKm), outcome.getKind());

        switch (outcome.getKind()) {
            case VALID:
                return onValid(scannedKm, key, outcome.getReservationId(), now);
            case REPLACE_WITH:
                return onNeedReplacement(scannedKm, key, ctx,
                        outcome.getReplacementKm(), outcome.getReservationId(), now, receiptNumber);
            case UNAVAILABLE:
                return onUnavailable(key, outcome.getMessage());
            case ERROR:
            default:
                return onResolverError(key, outcome.getMessage());
        }
    }

    // =========================================================================
    // Ветки
    // =========================================================================

    private Decision onValid(String scannedKm, CorrelationKey key, String reservationId, long now) {
        // 1) Глобальный поиск: если scanned совпадает с замещающим КМ какой-то
        //    активной QR_SHOWN записи (в любом чеке/товаре) — закрываем её overlay.
        //    Обычно такое совпадение происходит для той же позиции, где ожидалась
        //    замена. Использование глобального поиска даёт корректность даже в
        //    случае, если SR10 по какой-то причине вызвал нас с другим базовым ключом.
        ReplacementState targeted = repo.findQrShownByReplacement(scannedKm);
        if (targeted != null) {
            targeted.setStatus(Status.REPLACEMENT_ACCEPTED);
            // Удерживаем запись долго — до eventReceiptFiscalized или cancel.
            // Expiration-scheduler терминальные записи больше не чистит (см. TtlScheduler).
            targeted.setExpiresAtMs(now + Long.MAX_VALUE / 2);
            // Backend может выдать НОВЫЙ reservationId на второй скан (scan B =
            // ACCEPT_SCANNED); используем именно его для sold-confirm.
            if (reservationId != null && !reservationId.isEmpty()) {
                targeted.setReservationId(reservationId);
            }
            repo.save(targeted);
            log.info("[SBG-KMR-SM] ACCEPT close overlay | {} | scanned==replacement | rid={}",
                    targeted, targeted.getReservationId());
            return Decision.acceptCloseOverlay(targeted.getCorrelationKey(), targeted.getAttemptIndex());
        }

        // 2) Обычный ACCEPT: никакой активной замены для этого КМ нет.
        //    Специально НЕ гасим чужие overlay'ы по базовому ключу: они могут быть
        //    валидны для других позиций того же товара в чеке.
        return Decision.accept(key);
    }

    private Decision onNeedReplacement(String scannedKm,
                                       CorrelationKey key,
                                       ResolveContext ctx,
                                       String proposedReplacement,
                                       String reservationId,
                                       long now,
                                       int receiptNumber) {

        // Если автоподмена выключена настройкой — плагин работает как обычный
        // валидатор: никаких overlay, никакого state/резерва. Резерв, который
        // backend мог предварительно создать под REPLACE_WITH, отпустится его
        // собственным TTL — плагин не держит его rid у себя.
        if (!config.isReplacementEnabled()) {
            log.info("[SBG-KMR-SM] REJECT (replacement disabled) | key={} | rid={}", key, reservationId);
            return Decision.reject(key, "КМ не валиден");
        }

        // Дефенс: resolver вернул REPLACE_WITH, но предложил тот же КМ → считаем ошибкой.
        if (proposedReplacement == null || proposedReplacement.isEmpty() || proposedReplacement.equals(scannedKm)) {
            log.warn("[SBG-KMR-SM] resolver returned invalid REPLACE_WITH | key={} | same/empty", key);
            return Decision.reject(key, "Internal: resolver proposed invalid replacement");
        }

        // Идемпотентный повторный скан того же "плохого" КМ: если для этого
        // originalKm уже есть активный QR_SHOWN в пределах базового ключа —
        // просто показываем тот же overlay ещё раз (index/replacement не меняем).
        ReplacementState existing = repo.findQrShownByOriginalInBase(key, scannedKm);
        if (existing != null) {
            log.info("[SBG-KMR-SM] REJECT keep overlay (idempotent rescan) | {}", existing);
            return Decision.rejectShowOverlay(
                    existing.getCorrelationKey(),
                    existing.getAttemptIndex(),
                    "Отсканируйте предложенный заменяющий КМ",
                    existing.getReplacementKm());
        }

        // Новая позиция (новая попытка замены) — выделяем следующий attemptIndex.
        // Индекс — max(существующих) + 1, чтобы не пересечься с терминальными.
        int nextIndex = nextAttemptIndex(key);

        ReplacementState fresh = new ReplacementState(
                key,
                nextIndex,
                scannedKm,
                proposedReplacement,
                ctx != null ? ctx.getBarcode() : null,
                ctx != null ? ctx.getProductType() : null,
                Status.QR_SHOWN,
                now,
                now + config.getQrTtlMs(),
                1,
                receiptNumber
        );
        fresh.setReservationId(reservationId);
        repo.save(fresh);
        log.info("[SBG-KMR-SM] REJECT show overlay | {} | ttl={}ms", fresh, config.getQrTtlMs());
        return Decision.rejectShowOverlay(
                key,
                nextIndex,
                "КМ не найден. Отсканируйте заменяющий КМ с экрана кассы.",
                proposedReplacement);
    }

    private Decision onUnavailable(CorrelationKey key, String message) {
        log.info("[SBG-KMR-SM] REJECT unavailable | key={} | msg='{}'", key, message);
        return Decision.reject(key, message != null ? message : "Подходящий КМ не найден");
    }

    private Decision onResolverError(CorrelationKey key, String message) {
        log.error("[SBG-KMR-SM] REJECT resolver error | key={} | msg='{}'", key, message);
        return Decision.reject(key,
                "Сервис маркировки временно недоступен" + (message != null ? " (" + message + ")" : ""));
    }

    private int nextAttemptIndex(CorrelationKey key) {
        List<ReplacementState> all = repo.findAll(key);
        int max = 0;
        for (ReplacementState s : all) {
            if (s.getAttemptIndex() > max) max = s.getAttemptIndex();
        }
        return max + 1;
    }

    private static String abbreviate(String km) {
        if (km == null) return "null";
        if (km.length() <= 30) return km;
        return km.substring(0, 27) + "...";
    }
}
