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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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
    private final ConcurrentMap<String, ReservationBinding> saleBindingsByAlias = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ReservationBinding> returnBindingsByAlias = new ConcurrentHashMap<>();

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
                registerBinding(saleBindingsByAlias, request.getExcise(), response.getAppliedMark(), response.getReservationId());
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
                registerBinding(returnBindingsByAlias, request.getExcise(), response.getAppliedMark(), response.getReservationId());
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
        if (receipt == null) {
            return null;
        }
        List<String> marks = extractMarks(receipt);
        if (marks.isEmpty()) {
            return null;
        }

        boolean isRefund = receipt.getType() == ReceiptType.REFUND;
        String endpoint = resolveEndpoint(isRefund, isCancelReceipt);
        ConcurrentMap<String, ReservationBinding> bindingsByAlias = isRefund ? returnBindingsByAlias : saleBindingsByAlias;
        List<MarkOperationRequest> requests = new ArrayList<>();
        for (String markAlias : marks) {
            ReservationBinding binding = bindingsByAlias.get(markAlias);
            String markForBackend = binding != null && !isBlank(binding.getAppliedMark())
                    ? binding.getAppliedMark()
                    : markAlias;

            MarkOperationRequest request = new MarkOperationRequest();
            request.setOperationId(buildOperationId(endpoint, receipt, markForBackend));
            request.setMarkCode(markForBackend);
            request.setReceiptNumber(receipt.getNumber());
            request.setShiftNumber(receipt.getShiftNo());
            request.setFiscalDocId(receipt.getFiscalDocId());
            request.setFiscalSign(receipt.getFiscalSign());
            request.setReceiptId(Integer.toString(receipt.getNumber()));
            request.setReservationId(binding == null ? null : binding.getReservationId());
            requests.add(request);
        }

        try {
            dispatch(endpoint, requests);
            cleanupReservations(endpoint, requests, marks);
            return null;
        } catch (Exception ex) {
            log.error("failed to send fiscalized confirmation to backend", ex);
            return createFeedback(endpoint, requests);
        }
    }

    @Override
    public void onRepeatSend(Feedback feedback) throws Exception {
        if (feedback == null || feedback.getPayload() == null || feedback.getPayload().isBlank()) {
            return;
        }
        PendingOperationsPayload payload = apiClient.fromJson(feedback.getPayload(), PendingOperationsPayload.class);
        if (payload == null) {
            return;
        }
        dispatch(payload.getEndpoint(), payload.getRequests());
        cleanupReservations(payload.getEndpoint(), payload.getRequests(), null);
    }

    private void dispatch(String endpoint, List<MarkOperationRequest> requests) throws Exception {
        if (requests == null || requests.isEmpty()) {
            return;
        }
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

    private void cleanupReservations(String endpoint,
                                     List<MarkOperationRequest> requests,
                                     List<String> sourceAliases) {
        boolean salePath = ENDPOINT_SOLD_CONFIRM.equals(endpoint) || ENDPOINT_SALE_RELEASE.equals(endpoint);
        ConcurrentMap<String, ReservationBinding> bindings = salePath ? saleBindingsByAlias : returnBindingsByAlias;

        if (sourceAliases != null) {
            for (String alias : sourceAliases) {
                if (!isBlank(alias)) {
                    bindings.remove(alias);
                }
            }
        }

        if (requests == null) {
            return;
        }

        for (MarkOperationRequest request : requests) {
            if (request == null) {
                continue;
            }

            if (!isBlank(request.getReservationId())) {
                removeBindingsByReservation(bindings, request.getReservationId());
                continue;
            }

            if (!isBlank(request.getMarkCode())) {
                bindings.remove(request.getMarkCode());
            }
        }
    }

    private void removeBindingsByReservation(ConcurrentMap<String, ReservationBinding> bindings,
                                             String reservationId) {
        if (isBlank(reservationId)) {
            return;
        }

        bindings.forEach((alias, binding) -> {
            if (binding != null && reservationId.equals(binding.getReservationId())) {
                bindings.remove(alias, binding);
            }
        });
    }

    private void registerBinding(ConcurrentMap<String, ReservationBinding> bindings,
                                 String scannedMarkAlias,
                                 String appliedMark,
                                 String reservationId) {
        if (isBlank(reservationId)) {
            return;
        }

        String resolvedAppliedMark = firstNonBlank(appliedMark, scannedMarkAlias);
        ReservationBinding binding = new ReservationBinding(reservationId, resolvedAppliedMark);
        if (!isBlank(scannedMarkAlias)) {
            bindings.put(scannedMarkAlias, binding);
        }
        if (!isBlank(resolvedAppliedMark)) {
            bindings.put(resolvedAppliedMark, binding);
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
        if (receipt == null || receipt.getLineItems() == null) {
            return marks;
        }
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
        return stage + "-" + pos.getShopNumber() + "-" + pos.getPOSNumber() + "-" + shiftNo + "-" + receiptNo + "-" + Objects.hashCode(mark);
    }

    private String firstNonBlank(String first, String second) {
        return isBlank(first) ? second : first;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
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

    private static final class ReservationBinding {
        private final String reservationId;
        private final String appliedMark;

        private ReservationBinding(String reservationId, String appliedMark) {
            this.reservationId = reservationId;
            this.appliedMark = appliedMark;
        }

        private String getReservationId() {
            return reservationId;
        }

        private String getAppliedMark() {
            return appliedMark;
        }
    }
}
