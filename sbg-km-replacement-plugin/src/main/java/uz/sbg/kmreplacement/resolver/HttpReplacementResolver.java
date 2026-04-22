package uz.sbg.kmreplacement.resolver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uz.sbg.kmreplacement.http.MarkingHttpClient;
import uz.sbg.kmreplacement.http.dto.ProductRef;
import uz.sbg.kmreplacement.http.dto.ResolveAndReserveRequest;
import uz.sbg.kmreplacement.http.dto.ResolveAndReserveResponse;
import uz.sbg.kmreplacement.http.dto.ResolveResult;
import uz.sbg.kmreplacement.http.dto.ReturnResolveAndReserveRequest;
import uz.sbg.kmreplacement.http.dto.ReturnResolveAndReserveResponse;

import java.io.IOException;
import java.util.UUID;

/**
 * Реальный резолвер, ходящий в {@code sbg-marking-server-py}.
 *
 * <p>Маппинг продажного ответа {@code /resolve-and-reserve} на
 * {@link ResolveOutcome}:</p>
 * <ul>
 *   <li>{@code ACCEPT_SCANNED} → {@link ResolveOutcome#valid(String)} с reservationId.</li>
 *   <li>{@code ACCEPT_AUTO_SELECTED} + {@code appliedMark} →
 *       {@link ResolveOutcome#replaceWith(String, String)}.</li>
 *   <li>{@code REJECT_NO_CANDIDATE} → {@link ResolveOutcome#unavailable(String)}.
 *       Ключевой случай: «в пуле нет годной марки», overlay показывать не надо.</li>
 *   <li>{@code HARD_REJECT} → {@link ResolveOutcome#unavailable(String)}.
 *       Бизнес-отказ (невалидна для товара, уже возвращена, ...).</li>
 *   <li>{@link IOException} / любая ошибка транспорта → {@link ResolveOutcome#error(String)}.</li>
 * </ul>
 *
 * <p>Для возврата (isRefund=true) backend делает только проверку
 * соответствия КМ товару — без автоподмены (CLAUDE.md §Backend). Ответ
 * {@code success} → valid(); {@code !success} → unavailable.</p>
 *
 * <p>{@code operationId} — UUID на каждый скан (не reservationId).
 * Нужен бекенду для идемпотентности/трассировки.</p>
 */
public final class HttpReplacementResolver implements ReplacementResolver {

    private static final Logger log = LoggerFactory.getLogger(HttpReplacementResolver.class);

    private final MarkingHttpClient client;

    public HttpReplacementResolver(MarkingHttpClient client) {
        if (client == null) throw new IllegalArgumentException("client is null");
        this.client = client;
    }

    @Override
    public ResolveOutcome resolve(String scannedKm, ResolveContext ctx) {
        if (scannedKm == null || scannedKm.isEmpty()) {
            return ResolveOutcome.unavailable("empty KM");
        }
        if (ctx == null) {
            return ResolveOutcome.error("missing context");
        }
        try {
            return ctx.isRefund() ? resolveRefund(scannedKm, ctx) : resolveSale(scannedKm, ctx);
        } catch (IOException io) {
            log.warn("[SBG-KMR-HTTP] transport error: {}", io.getMessage());
            return ResolveOutcome.error("Сервис маркировки недоступен");
        } catch (Throwable t) {
            log.error("[SBG-KMR-HTTP] unexpected error: {}", t.getMessage(), t);
            return ResolveOutcome.error("Внутренняя ошибка плагина");
        }
    }

    // ---------------------------------------------------------------
    private ResolveOutcome resolveSale(String scannedKm, ResolveContext ctx) throws IOException {
        ResolveAndReserveRequest req = new ResolveAndReserveRequest();
        req.setOperationId(UUID.randomUUID().toString());
        req.setShopId(Integer.toString(ctx.getShopNumber()));
        req.setPosId(Integer.toString(ctx.getPosNumber()));
        req.setCashierId(null);    // У нас нет cashier id на этом этапе.
        req.setProduct(toProductRef(ctx));
        req.setScannedMark(scannedKm);
        req.setQuantity(1);

        log.info("[SBG-KMR-HTTP] POST /resolve-and-reserve | op={} | gtin={}",
                req.getOperationId(), req.getProduct().getGtin());

        ResolveAndReserveResponse resp = client.resolveAndReserve(req);

        ResolveResult result = resp.getResult();
        String appliedMark = resp.getAppliedMark();
        String rid         = resp.getReservationId();
        String msg         = resp.getMessage();

        log.info("[SBG-KMR-HTTP] /resolve-and-reserve <- result={} | source={} | rid={} | err={} | msg='{}'",
                result, resp.getSource(), rid, resp.getErrorCode(), msg);

        if (result == null) {
            return ResolveOutcome.error("Пустой ответ сервиса маркировки");
        }
        switch (result) {
            case ACCEPT_SCANNED:
                return ResolveOutcome.valid(rid);
            case ACCEPT_AUTO_SELECTED:
                if (appliedMark == null || appliedMark.isEmpty()) {
                    return ResolveOutcome.error("Сервис не вернул appliedMark для авто-подбора");
                }
                return ResolveOutcome.replaceWith(appliedMark, rid);
            case REJECT_NO_CANDIDATE:
                return ResolveOutcome.unavailable(msg != null ? msg : "Нет доступного КМ для товара");
            case HARD_REJECT:
                return ResolveOutcome.unavailable(msg != null ? msg : "КМ отклонён");
            default:
                return ResolveOutcome.error("Неизвестный результат: " + result);
        }
    }

    private ResolveOutcome resolveRefund(String scannedKm, ResolveContext ctx) throws IOException {
        ReturnResolveAndReserveRequest req = new ReturnResolveAndReserveRequest();
        req.setOperationId(UUID.randomUUID().toString());
        req.setShopId(Integer.toString(ctx.getShopNumber()));
        req.setPosId(Integer.toString(ctx.getPosNumber()));
        req.setCashierId(null);
        req.setProduct(toProductRef(ctx));
        req.setScannedMark(scannedKm);
        req.setSaleReceiptId(ctx.getReceiptNumber());    // при возврате это исходный чек продажи

        log.info("[SBG-KMR-HTTP] POST /return-resolve-and-reserve | op={} | gtin={}",
                req.getOperationId(), req.getProduct().getGtin());

        ReturnResolveAndReserveResponse resp = client.returnResolveAndReserve(req);
        log.info("[SBG-KMR-HTTP] /return-resolve-and-reserve <- success={} | rid={} | err={} | msg='{}'",
                resp.isSuccess(), resp.getReservationId(), resp.getErrorCode(), resp.getMessage());

        if (resp.isSuccess()) {
            return ResolveOutcome.valid(resp.getReservationId());
        }
        String msg = resp.getMessage();
        return ResolveOutcome.unavailable(msg != null ? msg : "КМ отклонён для возврата");
    }

    private static ProductRef toProductRef(ResolveContext ctx) {
        ProductRef p = new ProductRef();
        p.setGtin(ctx.getGtin());
        p.setBarcode(ctx.getBarcode());
        p.setProductType(ctx.getProductType());
        // item/артикул пока не передаём — ctx его не содержит; для бекенда
        // item — только fallback, gtin/barcode достаточно.
        return p;
    }
}
