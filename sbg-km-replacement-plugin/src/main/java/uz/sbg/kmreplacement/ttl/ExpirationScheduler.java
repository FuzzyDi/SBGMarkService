package uz.sbg.kmreplacement.ttl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uz.sbg.kmreplacement.http.MarkingHttpClient;
import uz.sbg.kmreplacement.http.dto.MarkOperationRequest;
import uz.sbg.kmreplacement.overlay.QrOverlayService;
import uz.sbg.kmreplacement.state.ReplacementState;
import uz.sbg.kmreplacement.state.ReplacementStateRepository;
import uz.sbg.kmreplacement.state.Status;
import uz.sbg.kmreplacement.util.Strings;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Фоновый компонент. Раз в секунду ищет {@code QR_SHOWN} записи с
 * {@code expiresAt &lt;= now}:
 * <ul>
 *   <li>гасит overlay и переводит запись в {@code EXPIRED};</li>
 *   <li>если есть {@code reservationId} и настроен HTTP-резолвер —
 *       best-effort вызывает {@code /sale-release} с reason={@code ttl_expired},
 *       чтобы не оставить повисший резерв на backend.</li>
 * </ul>
 *
 * <p>Терминальные записи ({@code REPLACEMENT_ACCEPTED}, {@code EXPIRED},
 * {@code FAILED}) этот scheduler НЕ удаляет — финализирует их
 * {@code eventReceiptFiscalized} в плагине.</p>
 */
public final class ExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExpirationScheduler.class);

    private static final long TICK_SEC = 1L;

    private final ReplacementStateRepository repo;
    private final QrOverlayService overlay;
    /** Nullable: в stub-режиме клиент отсутствует, TTL-release не делается. */
    private final MarkingHttpClient httpClient;
    private ScheduledExecutorService executor;

    public ExpirationScheduler(ReplacementStateRepository repo,
                               QrOverlayService overlay,
                               MarkingHttpClient httpClient) {
        this.repo = repo;
        this.overlay = overlay;
        this.httpClient = httpClient;
    }

    public synchronized void start() {
        if (executor != null) return;
        executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "sbg-kmr-expiration");
                t.setDaemon(true);
                return t;
            }
        });
        executor.scheduleAtFixedRate(new Runnable() {
            @Override public void run() { tick(); }
        }, TICK_SEC, TICK_SEC, TimeUnit.SECONDS);
        log.info("[SBG-KMR-TTL] ExpirationScheduler started | tick={}s | httpRelease={}",
                TICK_SEC, httpClient != null);
    }

    public synchronized void stop() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
            log.info("[SBG-KMR-TTL] ExpirationScheduler stopped");
        }
    }

    void tick() {
        try {
            long now = System.currentTimeMillis();
            List<ReplacementState> expired = repo.findExpired(now);
            for (ReplacementState s : expired) {
                s.setStatus(Status.EXPIRED);
                repo.save(s);
                overlay.hide(s.getCorrelationKey(), s.getAttemptIndex());
                log.info("[SBG-KMR-TTL] overlay EXPIRED | {}", s);
                safeReleaseOnExpiry(s);
            }
        } catch (Throwable t) {
            log.error("[SBG-KMR-TTL] tick failed: {}", t.getMessage(), t);
        }
    }

    private void safeReleaseOnExpiry(ReplacementState s) {
        if (httpClient == null) return;
        String rid = s.getReservationId();
        if (Strings.isBlank(rid)) return;
        try {
            MarkOperationRequest req = new MarkOperationRequest();
            req.setOperationId(UUID.randomUUID().toString());
            req.setReservationId(rid);
            req.setMarkCode(s.getReplacementKm() != null ? s.getReplacementKm() : s.getOriginalKm());
            req.setReceiptNumber(Integer.valueOf(s.getReceiptNumber()));
            req.setReason("ttl_expired");
            httpClient.saleRelease(req);
            log.info("[SBG-KMR-TTL] sale-release OK on TTL | rid={} | {}", rid, s.getCorrelationKey());
        } catch (Throwable t) {
            log.warn("[SBG-KMR-TTL] sale-release FAILED on TTL | rid={} | err={}", rid, t.getMessage());
        }
    }
}
