package uz.sbg.kmreplacement.plugin;

import org.slf4j.Logger;
import ru.crystals.pos.api.plugin.ExciseValidationPluginExtended;
import ru.crystals.pos.api.plugin.excise.validation.ExciseValidationResponse;
import ru.crystals.pos.spi.IntegrationProperties;
import ru.crystals.pos.spi.POSInfo;
import ru.crystals.pos.spi.annotation.Inject;
import ru.crystals.pos.spi.annotation.POSPlugin;
import ru.crystals.pos.spi.feedback.Feedback;
import ru.crystals.pos.spi.plugin.excise.validation.ExciseValidationCallback;
import ru.crystals.pos.spi.plugin.excise.validation.ExciseValidationRequest;
import ru.crystals.pos.spi.receipt.MarkInfo;
import ru.crystals.pos.spi.receipt.Receipt;
import ru.crystals.pos.spi.receipt.LineItem;
import uz.sbg.kmreplacement.config.KmReplacementConfig;
import uz.sbg.kmreplacement.http.MarkingHttpClient;
import uz.sbg.kmreplacement.http.dto.MarkOperationRequest;
import uz.sbg.kmreplacement.lifecycle.Decision;
import uz.sbg.kmreplacement.lifecycle.StateMachine;
import uz.sbg.kmreplacement.match.LineItemMarkMatcher;
import uz.sbg.kmreplacement.overlay.OverlayPlacement;
import uz.sbg.kmreplacement.overlay.QrOverlayService;
import uz.sbg.kmreplacement.overlay.QrOverlayWindow;
import uz.sbg.kmreplacement.overlay.QrPayloadBuilder;
import uz.sbg.kmreplacement.resolver.HttpReplacementResolver;
import uz.sbg.kmreplacement.resolver.ReplacementResolver;
import uz.sbg.kmreplacement.resolver.ResolveContext;
import uz.sbg.kmreplacement.resolver.StubReplacementResolver;
import uz.sbg.kmreplacement.state.CorrelationKey;
import uz.sbg.kmreplacement.state.InMemoryReplacementStateRepository;
import uz.sbg.kmreplacement.state.ReplacementState;
import uz.sbg.kmreplacement.state.ReplacementStateRepository;
import uz.sbg.kmreplacement.state.Status;
import uz.sbg.kmreplacement.ttl.ExpirationScheduler;
import uz.sbg.kmreplacement.util.Strings;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.UUID;

/**
 * <h2>SBG KM Replacement — главная точка входа для SR10.</h2>
 *
 * <p>Реализует {@link ExciseValidationPluginExtended} (sync + async,
 * потому что SR10 10.4.21 на продакшен-стенде ходит по sync-пути —
 * см. CLAUDE.md "Известные ошибки №4").</p>
 *
 * <p>Логика на каждый скан КМ:</p>
 * <ol>
 *   <li>построить {@link CorrelationKey};</li>
 *   <li>делегировать решение в {@link StateMachine};</li>
 *   <li>если {@link Decision#shouldShowOverlay()} — сгенерировать QR и
 *       показать через {@link QrOverlayService};</li>
 *   <li>если {@link Decision#shouldCloseOverlay()} — закрыть overlay;</li>
 *   <li>вернуть кассе {@link ExciseValidationResponse}.</li>
 * </ol>
 *
 * <p>На этом шаге используется {@link StubReplacementResolver} —
 * детерминированная заглушка без сети. Она:
 * <ul>
 *   <li>считает валидными любые КМ, содержащие подстроку
 *       {@link StubReplacementResolver#VALID_MARKER} (т.е. именно те,
 *       что сама и генерирует в QR);</li>
 *   <li>всем остальным возвращает синтетический замещающий КМ.</li>
 * </ul>
 * Этого достаточно, чтобы пройти весь UX-поток на стенде: скан A →
 * reject + QR → скан QR → ACCEPT + overlay закрыт. После успешной
 * стендовой проверки stub заменяется на {@code HttpReplacementResolver}
 * с вызовами sbg-marking-server-py.</p>
 *
 * <p>Java 8 совместимо. Никаких {@code var}, {@code List.of()},
 * {@code switch ->}.</p>
 */
@POSPlugin(id = SbgKmReplacementExcisePlugin.PLUGIN_ID)
public class SbgKmReplacementExcisePlugin implements ExciseValidationPluginExtended {

    /**
     * ВАЖНО: id плагина совпадает с лицензионным id из
     * {@code sbg-set10-marking-plugin}. Менять нельзя — на него оформлена
     * лицензия Set Retail 10. Новый jar выступает полноценной заменой
     * старого (deploy только один из двух).
     */
    public static final String PLUGIN_ID = "sbg.marking.auto.plugin";
    private static final String TAG = "SBG-KMR";

    @Inject private Logger log;
    @Inject private IntegrationProperties properties;
    @Inject private POSInfo pos;

    private KmReplacementConfig config;
    private ReplacementResolver resolver;
    /** Ненулевой только в HTTP-режиме. Нужен для sold-confirm / sale-release. */
    private MarkingHttpClient httpClient;
    private ReplacementStateRepository stateRepo;
    private QrOverlayService overlay;
    private ExpirationScheduler scheduler;
    private StateMachine stateMachine;

    public SbgKmReplacementExcisePlugin() {
        System.out.println("[" + TAG + "] constructor invoked (class loaded by SR10)");
    }

    @PostConstruct
    public void init() {
        try {
            if (log != null) {
                log.info("[{}] @PostConstruct init() START | pluginId={} | POS shop={} pos={}",
                        TAG, PLUGIN_ID,
                        pos == null ? "null" : pos.getShopNumber(),
                        pos == null ? "null" : pos.getPOSNumber());
            }

            this.config = KmReplacementConfig.fromProperties(
                    properties != null ? properties.getServiceProperties() : null);

            if (config.isStubEnabled()) {
                this.resolver = new StubReplacementResolver();
                this.httpClient = null;
                if (log != null) log.info("[{}] resolver = STUB (no network)", TAG);
            } else {
                this.httpClient = new MarkingHttpClient(config);
                this.resolver = new HttpReplacementResolver(httpClient);
                if (log != null) log.info("[{}] resolver = HTTP | baseUrl={}", TAG, config.getBaseUrl());
            }
            this.stateRepo = new InMemoryReplacementStateRepository();
            this.overlay   = new QrOverlayService();
            this.scheduler = new ExpirationScheduler(stateRepo, overlay, httpClient);
            this.scheduler.start();

            this.stateMachine = new StateMachine(resolver, stateRepo, config, new StateMachine.Clock() {
                @Override public long nowMs() { return System.currentTimeMillis(); }
            });

            if (log != null) {
                log.info("[{}] @PostConstruct init() DONE | config={}", TAG, config);
            }
        } catch (Throwable t) {
            System.err.println("[" + TAG + "] init failed: " + t.getMessage());
            t.printStackTrace();
            if (log != null) {
                log.error("[{}] init failed: {}", TAG, t.getMessage(), t);
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        try {
            if (scheduler != null) scheduler.stop();
            if (overlay != null) overlay.hideAll();
        } catch (Throwable ignore) { }
    }

    // =========================================================================
    // sync-методы — обязательный путь для SR10 10.4.21
    // =========================================================================
    @Override
    public ExciseValidationResponse validateExciseForSale(ExciseValidationRequest request) {
        return handle(request, /*isRefund=*/ false);
    }

    @Override
    public ExciseValidationResponse validateExciseForRefund(ExciseValidationRequest request) {
        return handle(request, /*isRefund=*/ true);
    }

    // =========================================================================
    // async-методы — зеркалят sync (на случай, если версия SR10 пойдёт по async-пути)
    // =========================================================================
    @Override
    public void validateExciseForSale(ExciseValidationRequest request, ExciseValidationCallback callback) {
        callback.onExciseValidationCompleted(validateExciseForSale(request));
    }

    @Override
    public void validateExciseForRefund(ExciseValidationRequest request, ExciseValidationCallback callback) {
        callback.onExciseValidationCompleted(validateExciseForRefund(request));
    }

    // =========================================================================
    // eventReceiptFiscalized — финализация всех записей данного чека.
    //
    //   !cancel: для каждой REPLACEMENT_ACCEPTED — ищем соответствующий LineItem:
    //            найден → /sold-confirm; не найден (позиция удалена) → /sale-release.
    //   cancel : все REPLACEMENT_ACCEPTED → /sale-release (причина "receipt_cancelled").
    //   QR_SHOWN/EXPIRED/FAILED с reservationId → best-effort /sale-release.
    //   Записи stub-резолвера (reservationId == null) просто удаляются локально.
    //
    //   В любом случае после обработки локальные записи удаляются и все
    //   overlay-окна гасятся.
    // =========================================================================
    @Override
    public Feedback eventReceiptFiscalized(Receipt receipt, boolean isCancelReceipt) {
        try {
            if (receipt == null || stateRepo == null) return null;
            int receiptNum = receipt.getNumber();
            List<ReplacementState> states = stateRepo.findByReceipt(receiptNum);
            if (log != null) {
                log.info("[{}] eventReceiptFiscalized | receipt={} | cancel={} | states={}",
                        TAG, receiptNum, isCancelReceipt, states.size());
            }
            for (ReplacementState s : states) {
                try {
                    finalizeState(s, receipt, isCancelReceipt);
                } catch (Throwable t) {
                    if (log != null) log.error("[{}] finalize FAILED | {} | err={}",
                            TAG, s, t.getMessage(), t);
                } finally {
                    stateRepo.remove(s.getCorrelationKey(), s.getAttemptIndex());
                }
            }
            if (overlay != null) overlay.hideAll();
        } catch (Throwable t) {
            if (log != null) {
                log.warn("[{}] eventReceiptFiscalized error: {}", TAG, t.getMessage(), t);
            }
        }
        return null;
    }

    private void finalizeState(ReplacementState s, Receipt receipt, boolean cancel) {
        String rid = s.getReservationId();
        boolean hasReservation = httpClient != null && !Strings.isBlank(rid);

        // Не-ACCEPTED записи (висящий QR_SHOWN, EXPIRED, FAILED) — best-effort release, если есть резерв.
        if (s.getStatus() != Status.REPLACEMENT_ACCEPTED) {
            if (hasReservation) {
                safeRelease(s, receipt, "not_accepted_at_fiscalize");
            } else if (log != null) {
                log.info("[{}] finalize skip (non-accepted, no rid) | {}", TAG, s);
            }
            return;
        }

        // REPLACEMENT_ACCEPTED: действуем по-разному в зависимости от cancel и наличия позиции в чеке.
        if (cancel) {
            if (hasReservation) safeRelease(s, receipt, "receipt_cancelled");
            else if (log != null) log.info("[{}] finalize stub/cancel | {}", TAG, s);
            return;
        }

        String markCode = s.getReplacementKm();
        LineItem matched = LineItemMarkMatcher.findMatching(receipt, markCode);
        if (matched == null) {
            // Позиция удалена кассиром до фискализации (например, 4→2 коки).
            if (log != null) {
                log.info("[{}] position DELETED before fiscalize | {} | markCode={}",
                        TAG, s, abbreviate(markCode));
            }
            if (hasReservation) safeRelease(s, receipt, "position_deleted");
            return;
        }

        // Позиция продана.
        if (log != null) {
            log.info("[{}] position SOLD | {} | lineNumber={}", TAG, s, matched.getNumber());
        }
        if (hasReservation) safeConfirm(s, receipt);
    }

    private void safeConfirm(ReplacementState s, Receipt receipt) {
        try {
            MarkOperationRequest req = buildOpRequest(s, receipt, null);
            httpClient.soldConfirm(req);
            if (log != null) log.info("[{}] sold-confirm OK | rid={}", TAG, s.getReservationId());
        } catch (Throwable t) {
            if (log != null) log.warn("[{}] sold-confirm FAILED | rid={} | err={}",
                    TAG, s.getReservationId(), t.getMessage(), t);
        }
    }

    private void safeRelease(ReplacementState s, Receipt receipt, String reason) {
        try {
            MarkOperationRequest req = buildOpRequest(s, receipt, reason);
            httpClient.saleRelease(req);
            if (log != null) log.info("[{}] sale-release OK | rid={} | reason={}",
                    TAG, s.getReservationId(), reason);
        } catch (Throwable t) {
            if (log != null) log.warn("[{}] sale-release FAILED | rid={} | reason={} | err={}",
                    TAG, s.getReservationId(), reason, t.getMessage(), t);
        }
    }

    private static MarkOperationRequest buildOpRequest(ReplacementState s, Receipt receipt, String reason) {
        MarkOperationRequest req = new MarkOperationRequest();
        req.setOperationId(UUID.randomUUID().toString());
        req.setReservationId(s.getReservationId());
        req.setMarkCode(s.getReplacementKm() != null ? s.getReplacementKm() : s.getOriginalKm());
        req.setReceiptNumber(receipt != null ? Integer.valueOf(receipt.getNumber()) : null);
        req.setReason(reason);
        return req;
    }

    @Override
    public void onRepeatSend(Feedback feedback) {
        // В этом плагине не используется: бекенд stub не держит резервов для retry.
    }

    // =========================================================================
    // Основная логика
    // =========================================================================
    private ExciseValidationResponse handle(ExciseValidationRequest request, boolean isRefund) {
        if (request == null) {
            log.warn("[{}] handle got null request — ALLOW pass-through", TAG);
            return new ExciseValidationResponse(true, null, false);
        }

        String excise = request.getExcise();
        String barcode = request.getBarcode();
        String productType = request.getProductType() != null ? request.getProductType().name() : null;
        MarkInfo markInfo = request.getMarkInfo();
        String gtin = (markInfo != null) ? markInfo.getGtin() : null;

        int shop = pos != null ? pos.getShopNumber() : 0;
        int posNum = pos != null ? pos.getPOSNumber() : 0;

        String receiptId = null;
        int receiptNumber = 0;
        try {
            Receipt r = request.getReceipt();
            if (r != null) {
                receiptNumber = r.getNumber();
                receiptId = Integer.toString(receiptNumber);
            }
        } catch (Throwable ignore) {
            // Receipt может оказаться null в каких-то ранних стадиях — не критично.
        }

        String productKey = !Strings.isBlank(gtin) ? gtin : Strings.orEmpty(barcode);
        CorrelationKey key = CorrelationKey.of(shop, posNum, receiptId, productKey);
        ResolveContext ctx = new ResolveContext(gtin, barcode, productType, shop, posNum, receiptId, isRefund);

        log.info("[{}] >>> validate{} | key={} | excise='{}' | gtin={} | barcode={} | productType={}",
                TAG, isRefund ? "Refund" : "Sale",
                key, abbreviate(excise), gtin, barcode, productType);

        Decision decision = stateMachine.onScan(excise, key, ctx, receiptNumber);
        log.info("[{}] decision | key={} | {}", TAG, key, decision);

        if (decision.shouldShowOverlay()) {
            showOverlay(decision.getCorrelationKey(), decision.getAttemptIndex(), decision.getReplacementKm());
        }
        if (decision.shouldCloseOverlay()) {
            overlay.hide(decision.getCorrelationKey(), decision.getAttemptIndex());
        }

        if (decision.isAccept()) {
            ExciseValidationResponse ok = new ExciseValidationResponse(true, null, false);
            if (request.getItem() != null) {
                ok.setItem(request.getItem());
            }
            return ok;
        }
        String msg = decision.getMessage() != null ? decision.getMessage() : "КМ не принят";
        return new ExciseValidationResponse(false, msg, false);
    }

    private void showOverlay(CorrelationKey key, int attemptIndex, String replacementKm) {
        try {
            BufferedImage qr = QrPayloadBuilder.build(replacementKm, QrOverlayWindow.QR_PX);
            String title = "ЗАМЕНА КМ";
            String subtitle = "Сканируйте QR как новый КМ";
            overlay.show(key, attemptIndex, qr, title, subtitle);
            // позиция берётся внутри окна (OverlayPlacement); место — правый верхний угол.
            log.info("[{}] overlay show | key={}#{} | screen={}",
                    TAG, key, attemptIndex, OverlayPlacement.screenBounds());
        } catch (Throwable t) {
            log.error("[{}] overlay show FAILED | key={}#{} | error={}",
                    TAG, key, attemptIndex, t.getMessage(), t);
        }
    }

    private static String abbreviate(String km) {
        if (km == null) return "null";
        if (km.length() <= 40) return km;
        return km.substring(0, 37) + "...";
    }
}
