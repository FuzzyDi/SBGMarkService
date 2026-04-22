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
import uz.sbg.kmreplacement.config.KmReplacementConfig;
import uz.sbg.kmreplacement.http.MarkingHttpClient;
import uz.sbg.kmreplacement.lifecycle.Decision;
import uz.sbg.kmreplacement.lifecycle.StateMachine;
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
import uz.sbg.kmreplacement.state.ReplacementStateRepository;
import uz.sbg.kmreplacement.ttl.ExpirationScheduler;
import uz.sbg.kmreplacement.util.Strings;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.awt.image.BufferedImage;

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
                if (log != null) log.info("[{}] resolver = STUB (no network)", TAG);
            } else {
                MarkingHttpClient httpClient = new MarkingHttpClient(config);
                this.resolver = new HttpReplacementResolver(httpClient);
                if (log != null) log.info("[{}] resolver = HTTP | baseUrl={}", TAG, config.getBaseUrl());
            }
            this.stateRepo = new InMemoryReplacementStateRepository();
            this.overlay   = new QrOverlayService();
            this.scheduler = new ExpirationScheduler(stateRepo, overlay);
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
    // eventReceiptFiscalized — гасим все оверлеи данного чека
    // =========================================================================
    @Override
    public Feedback eventReceiptFiscalized(Receipt receipt, boolean isCancelReceipt) {
        try {
            if (receipt != null && overlay != null && stateRepo != null) {
                int num = receipt.getNumber();
                stateRepo.removeByReceipt(num);
                // overlay-сервис не знает о receipt number напрямую, поэтому проще
                // пересобрать: мы уже очистили записи, оверлей выживших нет —
                // для надёжности закрываем все.
                overlay.hideAll();
                if (log != null) {
                    log.info("[{}] eventReceiptFiscalized | receipt={} | cancel={} | overlays hidden",
                            TAG, num, isCancelReceipt);
                }
            }
        } catch (Throwable t) {
            if (log != null) {
                log.warn("[{}] eventReceiptFiscalized error: {}", TAG, t.getMessage(), t);
            }
        }
        return null;
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
