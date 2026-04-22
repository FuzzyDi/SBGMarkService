package uz.sbg.kmreplacement.ttl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uz.sbg.kmreplacement.overlay.QrOverlayService;
import uz.sbg.kmreplacement.state.ReplacementState;
import uz.sbg.kmreplacement.state.ReplacementStateRepository;
import uz.sbg.kmreplacement.state.Status;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Фоновый компонент. Раз в секунду ищет {@code QR_SHOWN} записи с
 * {@code expiresAt &lt;= now} → гасит overlay + ставит EXPIRED.
 *
 * <p>Терминальные записи ({@code REPLACEMENT_ACCEPTED}, {@code EXPIRED},
 * {@code FAILED}) этот scheduler НЕ удаляет. Удаление терминальных записей
 * — ответственность обработчика {@code eventReceiptFiscalized} плагина,
 * который в этот момент решает для каждой: нужно ли вызвать
 * {@code sold-confirm} (позиция продана) или {@code sale-release} (позиция
 * удалена / чек отменён).</p>
 *
 * <p>Очистка EXPIRED-записей, если фискализации так и не случилось
 * (брошенный чек, перезапуск кассы и т.п.), происходит лениво —
 * {@code eventReceiptFiscalized} их зачистит одновременно с остальными
 * записями по receiptNumber, либо записи просто теряются при перезапуске
 * вместе с in-memory хранилищем.</p>
 */
public final class ExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExpirationScheduler.class);

    private static final long TICK_SEC = 1L;

    private final ReplacementStateRepository repo;
    private final QrOverlayService overlay;
    private ScheduledExecutorService executor;

    public ExpirationScheduler(ReplacementStateRepository repo, QrOverlayService overlay) {
        this.repo = repo;
        this.overlay = overlay;
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
        log.info("[SBG-KMR-TTL] ExpirationScheduler started | tick={}s", TICK_SEC);
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
            }
        } catch (Throwable t) {
            log.error("[SBG-KMR-TTL] tick failed: {}", t.getMessage(), t);
        }
    }
}
