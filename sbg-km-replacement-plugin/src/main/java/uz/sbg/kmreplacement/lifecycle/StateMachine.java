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

/**
 * Чистая логика state machine — без Swing, без HTTP, без SR10 API.
 * Легко покрывается unit-тестами.
 *
 * <p>Все зависимости передаются в конструктор. {@link Clock} — абстракция
 * над временем, чтобы тесты могли подменять now().</p>
 *
 * <p>Правила (см. раздел §4 проектного документа):</p>
 * <ol>
 *   <li>scanned KM валиден:
 *     <ul>
 *       <li>если есть активная запись QR_SHOWN и scanned == replacement →
 *           ACCEPT_CLOSE_OVERLAY + status=REPLACEMENT_ACCEPTED;</li>
 *       <li>иначе → ACCEPT (оверлей не трогаем, если вдруг есть).</li>
 *     </ul></li>
 *   <li>scanned KM невалиден:
 *     <ul>
 *       <li>если уже есть активная запись QR_SHOWN по этому ключу и
 *           scanned != replacement — ожидается, что кассир пока не отсканировал
 *           QR. Оверлей оставляем как есть, возвращаем REJECT;</li>
 *       <li>если есть активная запись и scanned == replacement —
 *           странная гонка (B считался валидным, но стал невалидным):
 *           status=FAILED, overlay HIDE, REJECT;</li>
 *       <li>если превышен maxAttempts — REJECT без overlay;</li>
 *       <li>иначе ищем замену: resolver.resolve() → REPLACE_WITH(B) →
 *           создаём/обновляем запись QR_SHOWN, возвращаем REJECT с
 *           указанием показать overlay(B);</li>
 *       <li>resolver.ERROR или UNAVAILABLE — REJECT без overlay с
 *           соответствующим сообщением.</li>
 *     </ul></li>
 * </ol>
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
        ReplacementState st = repo.find(key);

        // Сбросить устаревшую QR_SHOWN запись, прежде чем принимать решение.
        if (st != null && st.getStatus() == Status.QR_SHOWN && st.isExpired(now)) {
            log.info("[SBG-KMR-SM] state EXPIRED | key={}", key);
            st.setStatus(Status.EXPIRED);
            repo.save(st);
            st = null;   // дальше считаем, как будто записи не было
        }

        ResolveOutcome outcome = resolver.resolve(scannedKm, ctx);
        log.info("[SBG-KMR-SM] resolve | key={} | scanned='{}' | outcome={}",
                key, abbreviate(scannedKm), outcome.getKind());

        switch (outcome.getKind()) {
            case VALID:
                return onValid(scannedKm, key, st, now);
            case REPLACE_WITH:
                return onNeedReplacement(scannedKm, key, ctx, st, outcome.getReplacementKm(), now, receiptNumber);
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

    private Decision onValid(String scannedKm, CorrelationKey key, ReplacementState st, long now) {
        if (st != null && st.getStatus() == Status.QR_SHOWN && scannedKm.equals(st.getReplacementKm())) {
            // Кассир отсканировал ожидаемый B — закрываем overlay и фиксируем успех.
            st.setStatus(Status.REPLACEMENT_ACCEPTED);
            st.setExpiresAtMs(now + 5L * 60_000L);   // продлеваем на 5 мин для диагностики
            repo.save(st);
            log.info("[SBG-KMR-SM] ACCEPT close overlay | key={} | scanned==replacement", key);
            return Decision.acceptCloseOverlay(key);
        }
        // Любой другой валидный скан: если висит overlay по этому товару, тоже гасим —
        // товар продан, замена уже не актуальна.
        if (st != null && st.getStatus() == Status.QR_SHOWN) {
            st.setStatus(Status.REPLACEMENT_ACCEPTED);
            repo.save(st);
            log.info("[SBG-KMR-SM] ACCEPT close overlay | key={} | different KM accepted", key);
            return Decision.acceptCloseOverlay(key);
        }
        return Decision.accept(key);
    }

    private Decision onNeedReplacement(String scannedKm,
                                       CorrelationKey key,
                                       ResolveContext ctx,
                                       ReplacementState st,
                                       String proposedReplacement,
                                       long now,
                                       int receiptNumber) {

        // Дефенс: resolver вернул REPLACE_WITH, но предложил тот же КМ → считаем ошибкой.
        if (proposedReplacement == null || proposedReplacement.isEmpty() || proposedReplacement.equals(scannedKm)) {
            log.warn("[SBG-KMR-SM] resolver returned invalid REPLACE_WITH | key={} | same/empty", key);
            return Decision.reject(key, "Internal: resolver proposed invalid replacement");
        }

        // Ситуация: активная запись уже есть по этому ключу.
        if (st != null) {
            if (st.getStatus() == Status.QR_SHOWN && scannedKm.equals(st.getReplacementKm())) {
                // Скан == ожидаемого B, но B оказался невалидным → гонка / смена состояния в БД.
                st.setStatus(Status.FAILED);
                st.setLastRejectReason("replacement_no_longer_valid");
                repo.save(st);
                log.warn("[SBG-KMR-SM] FAILED: B became invalid | key={}", key);
                return Decision.reject(key, "Предложенная замена больше не действительна");
            }
            // Анти-цикл: исчерпали попытки — FAILED вне зависимости от текущего статуса.
            if (st.getAttemptCount() >= config.getMaxAttempts()) {
                st.setStatus(Status.FAILED);
                st.setLastRejectReason("max_attempts_exceeded");
                repo.save(st);
                log.warn("[SBG-KMR-SM] FAILED: attempts exceeded | key={} | attempts={}",
                        key, st.getAttemptCount());
                return Decision.reject(key, "Превышено число попыток замены КМ");
            }
            if (st.getStatus() == Status.QR_SHOWN) {
                // Кассир сканирует НОВЫЙ A при уже висящем оверлее — overlay не трогаем,
                // пусть кассир всё-таки отсканирует ожидаемый B.
                log.info("[SBG-KMR-SM] REJECT keep overlay | key={} | overlay already shown", key);
                return Decision.reject(key, "Отсканируйте предложенный заменяющий КМ");
            }
            // Терминальная запись по тому же ключу (EXPIRED/FAILED/ACCEPTED) — даём ещё одну попытку.
        }

        // Создаём / обновляем запись QR_SHOWN.
        int attempts = (st != null) ? st.getAttemptCount() + 1 : 1;
        ReplacementState fresh = new ReplacementState(
                key,
                scannedKm,
                proposedReplacement,
                ctx != null ? ctx.getBarcode() : null,
                ctx != null ? ctx.getProductType() : null,
                Status.QR_SHOWN,
                now,
                now + config.getQrTtlMs(),
                attempts,
                receiptNumber
        );
        repo.save(fresh);
        log.info("[SBG-KMR-SM] REJECT show overlay | key={} | attempts={} | ttl={}ms",
                key, attempts, config.getQrTtlMs());
        return Decision.rejectShowOverlay(key,
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

    private static String abbreviate(String km) {
        if (km == null) return "null";
        if (km.length() <= 30) return km;
        return km.substring(0, 27) + "...";
    }
}
