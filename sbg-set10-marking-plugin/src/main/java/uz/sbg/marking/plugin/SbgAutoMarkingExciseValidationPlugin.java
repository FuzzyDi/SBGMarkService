package uz.sbg.marking.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import ru.crystals.pos.api.plugin.ExciseValidationPluginExtended;
import ru.crystals.pos.api.plugin.user.User;
import ru.crystals.pos.api.plugin.excise.validation.ExciseValidationResponse;
import ru.crystals.pos.spi.IntegrationProperties;
import ru.crystals.pos.spi.POSInfo;
import ru.crystals.pos.spi.ResBundle;
import ru.crystals.pos.spi.annotation.Inject;
import ru.crystals.pos.spi.annotation.POSPlugin;
import ru.crystals.pos.spi.feedback.Feedback;
import ru.crystals.pos.spi.plugin.excise.validation.ExciseValidationCallback;
import ru.crystals.pos.spi.plugin.excise.validation.ExciseValidationRequest;
import ru.crystals.pos.spi.receipt.LineItem;
import ru.crystals.pos.spi.receipt.MarkInfo;
import ru.crystals.pos.spi.receipt.Receipt;
import ru.crystals.pos.spi.receipt.ReceiptType;
import uz.sbg.marking.contracts.ErrorCode;
import uz.sbg.marking.contracts.MarkOperationRequest;
import uz.sbg.marking.contracts.OperationResponse;
import uz.sbg.marking.contracts.ProductRef;
import uz.sbg.marking.contracts.ResolveAndReserveRequest;
import uz.sbg.marking.contracts.ResolveAndReserveResponse;
import uz.sbg.marking.contracts.ResolveResult;
import uz.sbg.marking.contracts.ReturnResolveAndReserveRequest;
import uz.sbg.marking.contracts.ReturnResolveAndReserveResponse;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@POSPlugin(id = SbgAutoMarkingExciseValidationPlugin.PLUGIN_ID)
public class SbgAutoMarkingExciseValidationPlugin implements ExciseValidationPluginExtended {
    public static final String PLUGIN_ID = "sbg.marking.auto.plugin";

    private static final String ENDPOINT_SOLD_CONFIRM = "sold-confirm";
    private static final String ENDPOINT_SALE_RELEASE = "sale-release";
    private static final String ENDPOINT_RETURN_CONFIRM = "return-confirm";
    private static final String ENDPOINT_RETURN_RELEASE = "return-release";

    @Inject
    private Logger log;

    @Inject
    private IntegrationProperties properties;

    @Inject
    private ResBundle res;

    @Inject
    private POSInfo pos;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SbgMarkingApiClient apiClient;
    private final ConcurrentMap<String, String> saleReservationByMark = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> returnReservationByMark = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        this.apiClient = new SbgMarkingApiClient(PluginConfig.fromProperties(properties), objectMapper);
    }

    @Override
    public void validateExciseForSale(ExciseValidationRequest request, ExciseValidationCallback callback) {
        try {
            ResolveAndReserveRequest payload = new ResolveAndReserveRequest();
            payload.setOperationId(buildOperationId("SALE", request.getReceipt(), request.getExcise()));
            payload.setShopId(Integer.toString(pos.getShopNumber()));
            payload.setPosId(Integer.toString(pos.getPOSNumber()));
            payload.setCashierId(resolveCashierId());
            payload.setScannedMark(request.getExcise());
            payload.setProduct(toProductRef(request));

            ResolveAndReserveResponse response = apiClient.resolveAndReserve(payload);
            if (response.getResult() == ResolveResult.ACCEPT_SCANNED || response.getResult() == ResolveResult.ACCEPT_AUTO_SELECTED) {
                if (response.getAppliedMark() != null && response.getReservationId() != null) {
                    saleReservationByMark.put(response.getAppliedMark(), response.getReservationId());
                }
                callback.onExciseValidationCompleted(allowResponse(request));
                return;
            }

            callback.onExciseValidationCompleted(denyResponse(response.getErrorCode(), response.getMessage()));
        } catch (Exception ex) {
            log.error("sale validation failed", ex);
            callback.onExciseValidationCompleted(denyResponse(ErrorCode.SERVICE_UNAVAILABLE, res.getString("sbg.error.service.unavailable")));
        }
    }

    @Override
    public void validateExciseForRefund(ExciseValidationRequest request, ExciseValidationCallback callback) {
        try {
            ReturnResolveAndReserveRequest payload = new ReturnResolveAndReserveRequest();
            payload.setOperationId(buildOperationId("REFUND", request.getReceipt(), request.getExcise()));
            payload.setShopId(Integer.toString(pos.getShopNumber()));
            payload.setPosId(Integer.toString(pos.getPOSNumber()));
            payload.setCashierId(resolveCashierId());
            payload.setScannedMark(request.getExcise());
            payload.setProduct(toProductRef(request));

            Receipt saleReceipt = request.getReceipt() != null ? request.getReceipt().getSaleReceipt() : null;
            if (saleReceipt != null) {
                payload.setSaleReceiptId(Integer.toString(saleReceipt.getNumber()));
            }

            ReturnResolveAndReserveResponse response = apiClient.returnResolveAndReserve(payload);
            if (response.isSuccess()) {
                if (response.getAppliedMark() != null && response.getReservationId() != null) {
                    returnReservationByMark.put(response.getAppliedMark(), response.getReservationId());
                }
                callback.onExciseValidationCompleted(allowResponse(request));
                return;
            }

            callback.onExciseValidationCompleted(denyResponse(response.getErrorCode(), response.getMessage()));
        } catch (Exception ex) {
            log.error("refund validation failed", ex);
            callback.onExciseValidationCompleted(denyResponse(ErrorCode.SERVICE_UNAVAILABLE, res.getString("sbg.error.service.unavailable")));
        }
    }

    @Override
    public Feedback eventReceiptFiscalized(Receipt receipt, boolean isCancelReceipt) {
        List<String> marks = extractMarks(receipt);
        if (marks.isEmpty()) {
            return null;
        }

        boolean isRefund = receipt.getType() == ReceiptType.REFUND;
        String endpoint = resolveEndpoint(isRefund, isCancelReceipt);
        List<MarkOperationRequest> requests = new ArrayList<>();
        for (String mark : marks) {
            MarkOperationRequest request = new MarkOperationRequest();
            request.setOperationId(buildOperationId(endpoint, receipt, mark));
            request.setMarkCode(mark);
            request.setReceiptNumber(receipt.getNumber());
            request.setShiftNumber(receipt.getShiftNo());
            request.setFiscalDocId(receipt.getFiscalDocId());
            request.setFiscalSign(receipt.getFiscalSign());
            request.setReceiptId(Integer.toString(receipt.getNumber()));
            request.setReservationId(isRefund ? returnReservationByMark.get(mark) : saleReservationByMark.get(mark));
            requests.add(request);
        }

        try {
            dispatch(endpoint, requests);
            cleanupReservations(endpoint, marks);
            return null;
        } catch (Exception ex) {
            log.error("failed to send fiscalized confirmation to backend", ex);
            return createFeedback(endpoint, requests);
        }
    }

    @Override
    public void onRepeatSend(Feedback feedback) throws Exception {
        PendingOperationsPayload payload = apiClient.fromJson(feedback.getPayload(), PendingOperationsPayload.class);
        dispatch(payload.getEndpoint(), payload.getRequests());
        List<String> marks = payload.getRequests() == null
                ? List.of()
                : payload.getRequests().stream()
                .map(MarkOperationRequest::getMarkCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        cleanupReservations(payload.getEndpoint(), marks);
    }

    private void dispatch(String endpoint, List<MarkOperationRequest> requests) throws Exception {
        for (MarkOperationRequest request : requests) {
            OperationResponse response;
            switch (endpoint) {
                case ENDPOINT_SOLD_CONFIRM -> response = apiClient.soldConfirm(request);
                case ENDPOINT_SALE_RELEASE -> response = apiClient.saleRelease(request);
                case ENDPOINT_RETURN_CONFIRM -> response = apiClient.returnConfirm(request);
                case ENDPOINT_RETURN_RELEASE -> response = apiClient.returnRelease(request);
                default -> throw new IllegalArgumentException("Unsupported endpoint: " + endpoint);
            }

            if (!response.isSuccess()) {
                throw new IllegalStateException("Backend rejected operation: " + response.getErrorCode() + ", " + response.getMessage());
            }
        }
    }

    private String resolveEndpoint(boolean isRefund, boolean isCancelReceipt) {
        if (isRefund) {
            return isCancelReceipt ? ENDPOINT_RETURN_RELEASE : ENDPOINT_RETURN_CONFIRM;
        }
        return isCancelReceipt ? ENDPOINT_SALE_RELEASE : ENDPOINT_SOLD_CONFIRM;
    }

    private void cleanupReservations(String endpoint, List<String> marks) {
        boolean salePath = ENDPOINT_SOLD_CONFIRM.equals(endpoint) || ENDPOINT_SALE_RELEASE.equals(endpoint);
        for (String mark : marks) {
            if (salePath) {
                saleReservationByMark.remove(mark);
            } else {
                returnReservationByMark.remove(mark);
            }
        }
    }

    private Feedback createFeedback(String endpoint, List<MarkOperationRequest> requests) {
        try {
            PendingOperationsPayload payload = new PendingOperationsPayload();
            payload.setEndpoint(endpoint);
            payload.setRequests(requests);
            return new Feedback(apiClient.toJson(payload));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to serialize feedback payload", ex);
        }
    }

    private ProductRef toProductRef(ExciseValidationRequest request) {
        ProductRef productRef = new ProductRef();
        productRef.setBarcode(request.getBarcode());
        productRef.setItem(request.getItem());
        productRef.setProductType(request.getProductType() == null ? null : request.getProductType().name());

        MarkInfo markInfo = request.getMarkInfo();
        if (markInfo != null) {
            productRef.setGtin(markInfo.getGtin());
        }

        return productRef;
    }

    private List<String> extractMarks(Receipt receipt) {
        List<String> marks = new ArrayList<>();
        for (LineItem lineItem : receipt.getLineItems()) {
            String mark = extractMark(lineItem);
            if (mark != null && !mark.isBlank()) {
                marks.add(mark);
            }
        }
        return marks;
    }

    private String extractMark(LineItem lineItem) {
        if (lineItem == null) {
            return null;
        }

        MarkInfo markInfo = lineItem.getMarkInfo();
        if (markInfo != null && markInfo.getMarkCode() != null && !markInfo.getMarkCode().isBlank()) {
            return markInfo.getMarkCode();
        }

        String excise = lineItem.getExcise();
        if (excise != null && !excise.isBlank()) {
            return excise;
        }

        return null;
    }

    private ExciseValidationResponse allowResponse(ExciseValidationRequest request) {
        ExciseValidationResponse response = new ExciseValidationResponse(true, null, false);
        response.setItem(request.getItem());
        return response;
    }

    private ExciseValidationResponse denyResponse(ErrorCode errorCode, String backendMessage) {
        String message = mapMessage(errorCode, backendMessage);
        return new ExciseValidationResponse(false, message, false);
    }

    private String mapMessage(ErrorCode errorCode, String backendMessage) {
        if (errorCode == null || errorCode == ErrorCode.NONE) {
            return backendMessage;
        }

        return switch (errorCode) {
            case NO_CANDIDATE -> res.getString("sbg.error.no.candidate");
            case INVALID_MARK_FOR_PRODUCT -> res.getString("sbg.error.invalid.for.product");
            case ALREADY_RETURNED -> res.getString("sbg.error.already.returned");
            case SERVICE_UNAVAILABLE -> res.getString("sbg.error.service.unavailable");
            default -> backendMessage != null ? backendMessage : res.getString("sbg.error.generic");
        };
    }

    private String buildOperationId(String stage, Receipt receipt, String mark) {
        int shiftNo = receipt == null ? -1 : receipt.getShiftNo();
        int receiptNo = receipt == null ? -1 : receipt.getNumber();
        String base = stage + "-" + pos.getShopNumber() + "-" + pos.getPOSNumber() + "-" + shiftNo + "-" + receiptNo + "-" + Objects.hashCode(mark);
        return base + "-" + UUID.randomUUID();
    }

    private String resolveCashierId() {
        User user = pos.getUser();
        if (user == null) {
            return "unknown";
        }

        if (user.getTabNumber() != null && !user.getTabNumber().isBlank()) {
            return user.getTabNumber();
        }

        String lastName = user.getLastName() == null ? "" : user.getLastName().trim();
        String firstName = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String fullName = (lastName + " " + firstName).trim();
        return fullName.isBlank() ? "unknown" : fullName;
    }
}
