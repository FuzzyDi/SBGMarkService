package uz.sbg.marking.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uz.sbg.marking.contracts.ErrorCode;
import uz.sbg.marking.contracts.HistoryQueryResponse;
import uz.sbg.marking.contracts.ImportMarkItem;
import uz.sbg.marking.contracts.ImportRequest;
import uz.sbg.marking.contracts.ImportResponse;
import uz.sbg.marking.contracts.MarkOperationRequest;
import uz.sbg.marking.contracts.MarkingHistoryEvent;
import uz.sbg.marking.contracts.MarkSource;
import uz.sbg.marking.contracts.MarkStatus;
import uz.sbg.marking.contracts.OperationResponse;
import uz.sbg.marking.contracts.ProductRef;
import uz.sbg.marking.contracts.ReportSummaryResponse;
import uz.sbg.marking.contracts.ResolveAndReserveRequest;
import uz.sbg.marking.contracts.ResolveAndReserveResponse;
import uz.sbg.marking.contracts.ResolveResult;
import uz.sbg.marking.contracts.ReturnResolveAndReserveRequest;
import uz.sbg.marking.contracts.ReturnResolveAndReserveResponse;
import uz.sbg.marking.server.model.MarkRecord;
import uz.sbg.marking.server.model.ReservationRecord;
import uz.sbg.marking.server.model.ReservationType;
import uz.sbg.marking.server.persistence.entity.HistoryEventEntity;
import uz.sbg.marking.server.persistence.entity.IdempotencyEntryEntity;
import uz.sbg.marking.server.persistence.entity.MarkEntity;
import uz.sbg.marking.server.persistence.entity.ReservationEntity;
import uz.sbg.marking.server.persistence.repository.HistoryEventRepository;
import uz.sbg.marking.server.persistence.repository.IdempotencyEntryRepository;
import uz.sbg.marking.server.persistence.repository.MarkRepository;
import uz.sbg.marking.server.persistence.repository.ReservationRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class MarkingService {
    private static final Comparator<MarkRecord> FIFO_COMPARATOR = Comparator
            .comparing(MarkRecord::getFifoTs, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(MarkRecord::getMarkCode);

    private final Map<String, MarkRecord> marks = new ConcurrentHashMap<>();
    private final Map<String, ReservationRecord> reservations = new ConcurrentHashMap<>();
    private final Map<String, ResolveAndReserveResponse> resolveIdempotency = new ConcurrentHashMap<>();
    private final Map<String, ReturnResolveAndReserveResponse> returnResolveIdempotency = new ConcurrentHashMap<>();
    private final Map<String, OperationResponse> operationIdempotency = new ConcurrentHashMap<>();
    private final List<MarkingHistoryEvent> history = new ArrayList<>();
    private final MarkRepository markRepository;
    private final ReservationRepository reservationRepository;
    private final HistoryEventRepository historyEventRepository;
    private final IdempotencyEntryRepository idempotencyEntryRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Duration reservationTtl;
    private final Duration idempotencyRetention;
    private final int historyMaxSize;
    private final Object monitor = new Object();

    public MarkingService(@Value("${sbg.marking.reservation.ttl.seconds:900}") long reservationTtlSec,
                          @Value("${sbg.marking.idempotency.retention.days:30}") long idempotencyRetentionDays,
                          @Value("${sbg.marking.history.max-size:100000}") int historyMaxSize,
                          MarkRepository markRepository,
                          ReservationRepository reservationRepository,
                          HistoryEventRepository historyEventRepository,
                          IdempotencyEntryRepository idempotencyEntryRepository,
                          ObjectMapper objectMapper) {
        this.markRepository = markRepository;
        this.reservationRepository = reservationRepository;
        this.historyEventRepository = historyEventRepository;
        this.idempotencyEntryRepository = idempotencyEntryRepository;
        this.objectMapper = objectMapper;
        this.clock = Clock.systemUTC();
        this.reservationTtl = Duration.ofSeconds(Math.max(60, reservationTtlSec));
        this.idempotencyRetention = Duration.ofDays(Math.max(1, idempotencyRetentionDays));
        this.historyMaxSize = Math.max(1000, historyMaxSize);
    }

    @PostConstruct
    public void loadStateFromDb() {
        synchronized (monitor) {
            marks.clear();
            for (MarkEntity entity : markRepository.findAll()) {
                MarkRecord mark = fromEntity(entity);
                marks.put(mark.getMarkCode(), mark);
            }

            reservations.clear();
            for (ReservationEntity entity : reservationRepository.findAll()) {
                ReservationRecord reservation = fromEntity(entity);
                reservations.put(reservation.getId(), reservation);
            }

            history.clear();
            List<MarkingHistoryEvent> loadedHistory = historyEventRepository.findAll().stream()
                    .map(this::fromEntity)
                    .sorted(Comparator.comparing(MarkingHistoryEvent::getTimestamp, Comparator.nullsLast(Comparator.naturalOrder())))
                    .collect(Collectors.toList());

            int fromIndex = Math.max(0, loadedHistory.size() - historyMaxSize);
            history.addAll(loadedHistory.subList(fromIndex, loadedHistory.size()));

            cleanupExpiredReservations();
            cleanupExpiredIdempotencyEntries();
        }
    }

    public ResolveAndReserveResponse resolveAndReserve(ResolveAndReserveRequest request) {
        synchronized (monitor) {
            cleanupExpiredReservations();
            final String route = "resolve";
            final String operationId = request == null ? null : request.getOperationId();
            final String key = idempotencyKey(route, operationId);
            ResolveAndReserveResponse cached = findIdempotent(route, operationId, key, ResolveAndReserveResponse.class, resolveIdempotency);
            if (cached != null) {
                return cached;
            }

            ResolveAndReserveResponse validationError = validateResolveRequest(request);
            if (validationError != null) {
                rememberIdempotent(route, operationId, key, validationError, resolveIdempotency);
                recordResolveEvent("SALE_RESOLVE", request, validationError);
                return validationError;
            }

            MarkRecord scanned = marks.get(request.getScannedMark());
            if (scanned != null) {
                if (!matchesProduct(scanned, request.getProduct())) {
                    ResolveAndReserveResponse response = hardReject(ErrorCode.INVALID_MARK_FOR_PRODUCT, "Scanned mark belongs to another product.");
                    rememberIdempotent(route, operationId, key, response, resolveIdempotency);
                    recordResolveEvent("SALE_RESOLVE", request, response);
                    return response;
                }
                if (!eligibleForSale(scanned)) {
                    ResolveAndReserveResponse response = hardReject(ErrorCode.INVALID_STATE, "Scanned mark is not available for sale.");
                    rememberIdempotent(route, operationId, key, response, resolveIdempotency);
                    recordResolveEvent("SALE_RESOLVE", request, response);
                    return response;
                }
                ResolveAndReserveResponse response = reserveForSale(scanned, request.getOperationId(), MarkSource.SCANNED, ResolveResult.ACCEPT_SCANNED, "Scanned mark accepted.");
                rememberIdempotent(route, operationId, key, response, resolveIdempotency);
                recordResolveEvent("SALE_RESOLVE", request, response);
                return response;
            }

            Optional<MarkRecord> candidate = marks.values().stream()
                    .filter(this::eligibleForSale)
                    .filter(mark -> matchesProduct(mark, request.getProduct()))
                    .sorted(FIFO_COMPARATOR)
                    .findFirst();

            if (candidate.isEmpty()) {
                ResolveAndReserveResponse response = rejectNoCandidate("No suitable mark is available.");
                rememberIdempotent(route, operationId, key, response, resolveIdempotency);
                recordResolveEvent("SALE_RESOLVE", request, response);
                return response;
            }

            ResolveAndReserveResponse response = reserveForSale(candidate.get(), request.getOperationId(), MarkSource.AUTO_SELECTED, ResolveResult.ACCEPT_AUTO_SELECTED, "Mark was selected automatically by FIFO.");
            rememberIdempotent(route, operationId, key, response, resolveIdempotency);
            recordResolveEvent("SALE_RESOLVE", request, response);
            return response;
        }
    }

    public ReturnResolveAndReserveResponse returnResolveAndReserve(ReturnResolveAndReserveRequest request) {
        synchronized (monitor) {
            cleanupExpiredReservations();
            final String route = "return-resolve";
            final String operationId = request == null ? null : request.getOperationId();
            final String key = idempotencyKey(route, operationId);
            ReturnResolveAndReserveResponse cached = findIdempotent(route, operationId, key, ReturnResolveAndReserveResponse.class, returnResolveIdempotency);
            if (cached != null) {
                return cached;
            }

            ReturnResolveAndReserveResponse validation = validateReturnResolveRequest(request);
            if (validation != null) {
                rememberIdempotent(route, operationId, key, validation, returnResolveIdempotency);
                recordReturnResolveEvent("RETURN_RESOLVE", request, validation);
                return validation;
            }

            MarkRecord mark = marks.get(request.getScannedMark());
            if (mark == null) {
                ReturnResolveAndReserveResponse response = failReturn(ErrorCode.INVALID_STATE, "Mark was not found in local mark pool.");
                rememberIdempotent(route, operationId, key, response, returnResolveIdempotency);
                recordReturnResolveEvent("RETURN_RESOLVE", request, response);
                return response;
            }
            if (!matchesProduct(mark, request.getProduct())) {
                ReturnResolveAndReserveResponse response = failReturn(ErrorCode.INVALID_MARK_FOR_PRODUCT, "Scanned mark belongs to another product.");
                rememberIdempotent(route, operationId, key, response, returnResolveIdempotency);
                recordReturnResolveEvent("RETURN_RESOLVE", request, response);
                return response;
            }
            if (mark.getStatus() != MarkStatus.SOLD) {
                ErrorCode errorCode = mark.getStatus() == MarkStatus.AVAILABLE && mark.getLastReturnReceiptId() != null
                        ? ErrorCode.ALREADY_RETURNED
                        : ErrorCode.INVALID_STATE;
                ReturnResolveAndReserveResponse response = failReturn(errorCode, "Mark is not in SOLD state and cannot be returned.");
                rememberIdempotent(route, operationId, key, response, returnResolveIdempotency);
                recordReturnResolveEvent("RETURN_RESOLVE", request, response);
                return response;
            }

            ReservationRecord reservation = createReservation(mark.getMarkCode(), request.getOperationId(), ReservationType.RETURN);
            mark.setStatus(MarkStatus.RETURN_RESERVED);
            mark.setActiveReservationId(reservation.getId());
            saveMark(mark);

            ReturnResolveAndReserveResponse response = new ReturnResolveAndReserveResponse();
            response.setSuccess(true);
            response.setAppliedMark(mark.getMarkCode());
            response.setReservationId(reservation.getId());
            response.setMessage("Return reservation created.");
            rememberIdempotent(route, operationId, key, response, returnResolveIdempotency);
            recordReturnResolveEvent("RETURN_RESOLVE", request, response);
            return response;
        }
    }

    public OperationResponse soldConfirm(MarkOperationRequest request) {
        synchronized (monitor) {
            cleanupExpiredReservations();
            final String route = "sold-confirm";
            final String operationId = request == null ? null : request.getOperationId();
            final String key = idempotencyKey(route, operationId);
            OperationResponse cached = findIdempotent(route, operationId, key, OperationResponse.class, operationIdempotency);
            if (cached != null) {
                return cached;
            }

            OperationResponse validation = validateMarkOperationRequest(request);
            if (validation != null) {
                rememberIdempotent(route, operationId, key, validation, operationIdempotency);
                recordOperationEvent("SALE_CONFIRM", request, validation, null, null);
                return validation;
            }

            Optional<ReservationRecord> reservation = findReservation(request, ReservationType.SALE);
            if (reservation.isPresent()) {
                MarkRecord mark = marks.get(reservation.get().getMarkCode());
                if (mark == null) {
                    OperationResponse response = OperationResponse.fail(ErrorCode.INVALID_STATE, "Reservation mark was not found.");
                    rememberIdempotent(route, operationId, key, response, operationIdempotency);
                    recordOperationEvent("SALE_CONFIRM", request, response, reservation.get().getMarkCode(), reservation.get().getId());
                    return response;
                }
                mark.setStatus(MarkStatus.SOLD);
                mark.setActiveReservationId(null);
                mark.setLastSaleReceiptId(firstNonBlank(request.getReceiptId(), Integer.toString(Objects.requireNonNullElse(request.getReceiptNumber(), -1))));
                saveMark(mark);
                removeReservation(reservation.get().getId());

                OperationResponse response = OperationResponse.ok("Sale confirmed.");
                rememberIdempotent(route, operationId, key, response, operationIdempotency);
                recordOperationEvent("SALE_CONFIRM", request, response, mark.getMarkCode(), reservation.get().getId());
                return response;
            }

            MarkRecord mark = marks.get(request.getMarkCode());
            if (mark != null && mark.getStatus() == MarkStatus.SOLD) {
                OperationResponse response = OperationResponse.ok("Sale was already confirmed.");
                rememberIdempotent(route, operationId, key, response, operationIdempotency);
                recordOperationEvent("SALE_CONFIRM", request, response, mark.getMarkCode(), request.getReservationId());
                return response;
            }

            OperationResponse response = OperationResponse.fail(ErrorCode.INVALID_STATE, "No active sale reservation found for mark.");
            rememberIdempotent(route, operationId, key, response, operationIdempotency);
            recordOperationEvent("SALE_CONFIRM", request, response, request.getMarkCode(), request.getReservationId());
            return response;
        }
    }

    public OperationResponse saleRelease(MarkOperationRequest request) {
        synchronized (monitor) {
            cleanupExpiredReservations();
            final String route = "sale-release";
            final String operationId = request == null ? null : request.getOperationId();
            final String key = idempotencyKey(route, operationId);
            OperationResponse cached = findIdempotent(route, operationId, key, OperationResponse.class, operationIdempotency);
            if (cached != null) {
                return cached;
            }

            OperationResponse validation = validateMarkOperationRequest(request);
            if (validation != null) {
                rememberIdempotent(route, operationId, key, validation, operationIdempotency);
                recordOperationEvent("SALE_RELEASE", request, validation, null, null);
                return validation;
            }

            Optional<ReservationRecord> reservation = findReservation(request, ReservationType.SALE);
            if (reservation.isPresent()) {
                MarkRecord mark = marks.get(reservation.get().getMarkCode());
                if (mark == null) {
                    OperationResponse response = OperationResponse.fail(ErrorCode.INVALID_STATE, "Reservation mark was not found.");
                    rememberIdempotent(route, operationId, key, response, operationIdempotency);
                    recordOperationEvent("SALE_RELEASE", request, response, reservation.get().getMarkCode(), reservation.get().getId());
                    return response;
                }
                mark.setStatus(MarkStatus.AVAILABLE);
                mark.setActiveReservationId(null);
                saveMark(mark);
                removeReservation(reservation.get().getId());
                OperationResponse response = OperationResponse.ok("Sale reservation released.");
                rememberIdempotent(route, operationId, key, response, operationIdempotency);
                recordOperationEvent("SALE_RELEASE", request, response, mark.getMarkCode(), reservation.get().getId());
                return response;
            }

            MarkRecord mark = marks.get(request.getMarkCode());
            if (mark != null && mark.getStatus() == MarkStatus.AVAILABLE) {
                OperationResponse response = OperationResponse.ok("Sale reservation was already released.");
                rememberIdempotent(route, operationId, key, response, operationIdempotency);
                recordOperationEvent("SALE_RELEASE", request, response, mark.getMarkCode(), request.getReservationId());
                return response;
            }

            OperationResponse response = OperationResponse.fail(ErrorCode.INVALID_STATE, "No active sale reservation found for release.");
            rememberIdempotent(route, operationId, key, response, operationIdempotency);
            recordOperationEvent("SALE_RELEASE", request, response, request.getMarkCode(), request.getReservationId());
            return response;
        }
    }

    public OperationResponse returnConfirm(MarkOperationRequest request) {
        synchronized (monitor) {
            cleanupExpiredReservations();
            final String route = "return-confirm";
            final String operationId = request == null ? null : request.getOperationId();
            final String key = idempotencyKey(route, operationId);
            OperationResponse cached = findIdempotent(route, operationId, key, OperationResponse.class, operationIdempotency);
            if (cached != null) {
                return cached;
            }

            OperationResponse validation = validateMarkOperationRequest(request);
            if (validation != null) {
                rememberIdempotent(route, operationId, key, validation, operationIdempotency);
                recordOperationEvent("RETURN_CONFIRM", request, validation, null, null);
                return validation;
            }

            Optional<ReservationRecord> reservation = findReservation(request, ReservationType.RETURN);
            if (reservation.isPresent()) {
                MarkRecord mark = marks.get(reservation.get().getMarkCode());
                if (mark == null) {
                    OperationResponse response = OperationResponse.fail(ErrorCode.INVALID_STATE, "Reservation mark was not found.");
                    rememberIdempotent(route, operationId, key, response, operationIdempotency);
                    recordOperationEvent("RETURN_CONFIRM", request, response, reservation.get().getMarkCode(), reservation.get().getId());
                    return response;
                }
                mark.setStatus(MarkStatus.AVAILABLE);
                mark.setActiveReservationId(null);
                mark.setLastReturnReceiptId(firstNonBlank(request.getReceiptId(), Integer.toString(Objects.requireNonNullElse(request.getReceiptNumber(), -1))));
                mark.setFifoTs(Instant.now(clock));
                saveMark(mark);
                removeReservation(reservation.get().getId());

                OperationResponse response = OperationResponse.ok("Return confirmed. Mark is available for sale again.");
                rememberIdempotent(route, operationId, key, response, operationIdempotency);
                recordOperationEvent("RETURN_CONFIRM", request, response, mark.getMarkCode(), reservation.get().getId());
                return response;
            }

            MarkRecord mark = marks.get(request.getMarkCode());
            if (mark != null && mark.getStatus() == MarkStatus.AVAILABLE) {
                OperationResponse response = OperationResponse.ok("Return was already confirmed.");
                rememberIdempotent(route, operationId, key, response, operationIdempotency);
                recordOperationEvent("RETURN_CONFIRM", request, response, mark.getMarkCode(), request.getReservationId());
                return response;
            }

            OperationResponse response = OperationResponse.fail(ErrorCode.INVALID_STATE, "No active return reservation found for mark.");
            rememberIdempotent(route, operationId, key, response, operationIdempotency);
            recordOperationEvent("RETURN_CONFIRM", request, response, request.getMarkCode(), request.getReservationId());
            return response;
        }
    }

    public OperationResponse returnRelease(MarkOperationRequest request) {
        synchronized (monitor) {
            cleanupExpiredReservations();
            final String route = "return-release";
            final String operationId = request == null ? null : request.getOperationId();
            final String key = idempotencyKey(route, operationId);
            OperationResponse cached = findIdempotent(route, operationId, key, OperationResponse.class, operationIdempotency);
            if (cached != null) {
                return cached;
            }

            OperationResponse validation = validateMarkOperationRequest(request);
            if (validation != null) {
                rememberIdempotent(route, operationId, key, validation, operationIdempotency);
                recordOperationEvent("RETURN_RELEASE", request, validation, null, null);
                return validation;
            }

            Optional<ReservationRecord> reservation = findReservation(request, ReservationType.RETURN);
            if (reservation.isPresent()) {
                MarkRecord mark = marks.get(reservation.get().getMarkCode());
                if (mark == null) {
                    OperationResponse response = OperationResponse.fail(ErrorCode.INVALID_STATE, "Reservation mark was not found.");
                    rememberIdempotent(route, operationId, key, response, operationIdempotency);
                    recordOperationEvent("RETURN_RELEASE", request, response, reservation.get().getMarkCode(), reservation.get().getId());
                    return response;
                }
                mark.setStatus(MarkStatus.SOLD);
                mark.setActiveReservationId(null);
                saveMark(mark);
                removeReservation(reservation.get().getId());

                OperationResponse response = OperationResponse.ok("Return reservation released.");
                rememberIdempotent(route, operationId, key, response, operationIdempotency);
                recordOperationEvent("RETURN_RELEASE", request, response, mark.getMarkCode(), reservation.get().getId());
                return response;
            }

            MarkRecord mark = marks.get(request.getMarkCode());
            if (mark != null && mark.getStatus() == MarkStatus.SOLD) {
                OperationResponse response = OperationResponse.ok("Return reservation was already released.");
                rememberIdempotent(route, operationId, key, response, operationIdempotency);
                recordOperationEvent("RETURN_RELEASE", request, response, mark.getMarkCode(), request.getReservationId());
                return response;
            }

            OperationResponse response = OperationResponse.fail(ErrorCode.INVALID_STATE, "No active return reservation found for release.");
            rememberIdempotent(route, operationId, key, response, operationIdempotency);
            recordOperationEvent("RETURN_RELEASE", request, response, request.getMarkCode(), request.getReservationId());
            return response;
        }
    }

    public ImportResponse importFull(ImportRequest request) {
        synchronized (monitor) {
            return importInternal(request, true);
        }
    }

    public ImportResponse importDelta(ImportRequest request) {
        synchronized (monitor) {
            return importInternal(request, false);
        }
    }

    public List<MarkRecord> snapshotMarks() {
        synchronized (monitor) {
            return new ArrayList<>(marks.values());
        }
    }

    public HistoryQueryResponse queryHistory(String markCode, Instant from, Instant to, Integer limit) {
        synchronized (monitor) {
            int maxItems = limit == null ? 200 : Math.max(1, Math.min(5000, limit));
            List<MarkingHistoryEvent> filtered = history.stream()
                    .filter(event -> isBlank(markCode) || markCode.equals(event.getMarkCode()))
                    .filter(event -> from == null || !event.getTimestamp().isBefore(from))
                    .filter(event -> to == null || !event.getTimestamp().isAfter(to))
                    .collect(Collectors.toList());

            int total = filtered.size();
            int fromIndex = Math.max(0, total - maxItems);

            HistoryQueryResponse response = new HistoryQueryResponse();
            response.setTotal(total);
            response.setEvents(new ArrayList<>(filtered.subList(fromIndex, total)));
            return response;
        }
    }

    public ReportSummaryResponse summary() {
        synchronized (monitor) {
            ReportSummaryResponse response = new ReportSummaryResponse();
            response.setGeneratedAt(Instant.now(clock));
            response.setTotalMarks(marks.size());
            response.setAvailableMarks(marks.values().stream().filter(m -> m.getStatus() == MarkStatus.AVAILABLE).count());
            response.setReservedMarks(marks.values().stream().filter(m -> m.getStatus() == MarkStatus.RESERVED).count());
            response.setSoldMarks(marks.values().stream().filter(m -> m.getStatus() == MarkStatus.SOLD).count());
            response.setReturnReservedMarks(marks.values().stream().filter(m -> m.getStatus() == MarkStatus.RETURN_RESERVED).count());
            response.setActiveReservations(reservations.size());
            response.setHistoryEvents(history.size());
            response.setSaleConfirmCount(history.stream()
                    .filter(MarkingHistoryEvent::isSuccess)
                    .filter(event -> "SALE_CONFIRM".equals(event.getEventType()))
                    .count());
            response.setReturnConfirmCount(history.stream()
                    .filter(MarkingHistoryEvent::isSuccess)
                    .filter(event -> "RETURN_CONFIRM".equals(event.getEventType()))
                    .count());
            return response;
        }
    }

    @Scheduled(fixedDelayString = "${sbg.marking.cleanup.interval.ms:60000}")
    public void scheduledCleanup() {
        synchronized (monitor) {
            cleanupExpiredReservations();
            cleanupExpiredIdempotencyEntries();
        }
    }

    private ImportResponse importInternal(ImportRequest request, boolean fullMode) {
        cleanupExpiredReservations();
        ImportResponse response = new ImportResponse();
        response.setBatchId(request != null ? request.getBatchId() : null);
        String eventType = fullMode ? "IMPORT_FULL" : "IMPORT_DELTA";

        if (request == null || request.getItems() == null) {
            recordSimpleEvent(eventType, false, ErrorCode.VALIDATION_FAILED, "Import request/items are empty.");
            return response;
        }

        Map<String, ImportMarkItem> incoming = new HashMap<>();
        for (ImportMarkItem item : request.getItems()) {
            if (item == null || isBlank(item.getMarkCode()) || isBlank(item.getProductType()) || (isBlank(item.getItem()) && isBlank(item.getGtin()))) {
                response.setQuarantined(response.getQuarantined() + 1);
                continue;
            }
            incoming.put(item.getMarkCode(), item);
            MarkRecord existing = marks.get(item.getMarkCode());
            if (existing == null) {
                MarkRecord created = toMarkRecord(item);
                upsertMark(created);
                response.setAdded(response.getAdded() + 1);
            } else {
                updateFromImport(existing, item);
                saveMark(existing);
                response.setUpdated(response.getUpdated() + 1);
            }
        }

        if (fullMode) {
            for (String markCode : new ArrayList<>(marks.keySet())) {
                MarkRecord mark = marks.get(markCode);
                if (mark == null) {
                    continue;
                }
                if (!incoming.containsKey(markCode)) {
                    if (mark.getStatus() == MarkStatus.AVAILABLE && mark.getActiveReservationId() == null) {
                        deleteMark(markCode);
                        response.setSkipped(response.getSkipped() + 1);
                    }
                }
            }
        }

        String importMessage = "Import completed: added=" + response.getAdded()
                + ", updated=" + response.getUpdated()
                + ", quarantined=" + response.getQuarantined()
                + ", skipped=" + response.getSkipped();
        recordSimpleEvent(eventType, true, ErrorCode.NONE, importMessage);
        return response;
    }

    private ResolveAndReserveResponse validateResolveRequest(ResolveAndReserveRequest request) {
        if (request == null || isBlank(request.getOperationId())) {
            return hardReject(ErrorCode.VALIDATION_FAILED, "operationId is required.");
        }
        if (request.getQuantity() != 1) {
            return hardReject(ErrorCode.VALIDATION_FAILED, "Only quantity=1 is supported for serial marks.");
        }
        if (request.getProduct() == null || isBlank(request.getProduct().getProductType()) || (isBlank(request.getProduct().getItem()) && isBlank(request.getProduct().getGtin()))) {
            return hardReject(ErrorCode.VALIDATION_FAILED, "productType and one of item/gtin are required.");
        }
        if (isBlank(request.getScannedMark())) {
            return hardReject(ErrorCode.VALIDATION_FAILED, "scannedMark is required.");
        }
        return null;
    }

    private ReturnResolveAndReserveResponse validateReturnResolveRequest(ReturnResolveAndReserveRequest request) {
        if (request == null || isBlank(request.getOperationId())) {
            return failReturn(ErrorCode.VALIDATION_FAILED, "operationId is required.");
        }
        if (request.getProduct() == null || isBlank(request.getProduct().getProductType()) || (isBlank(request.getProduct().getItem()) && isBlank(request.getProduct().getGtin()))) {
            return failReturn(ErrorCode.VALIDATION_FAILED, "productType and one of item/gtin are required.");
        }
        if (isBlank(request.getScannedMark())) {
            return failReturn(ErrorCode.VALIDATION_FAILED, "scannedMark is required.");
        }
        return null;
    }

    private OperationResponse validateMarkOperationRequest(MarkOperationRequest request) {
        if (request == null || isBlank(request.getOperationId())) {
            return OperationResponse.fail(ErrorCode.VALIDATION_FAILED, "operationId is required.");
        }
        if (isBlank(request.getReservationId()) && isBlank(request.getMarkCode())) {
            return OperationResponse.fail(ErrorCode.VALIDATION_FAILED, "reservationId or markCode is required.");
        }
        return null;
    }

    private ResolveAndReserveResponse reserveForSale(MarkRecord mark,
                                                     String operationId,
                                                     MarkSource source,
                                                     ResolveResult result,
                                                     String message) {
        ReservationRecord reservation = createReservation(mark.getMarkCode(), operationId, ReservationType.SALE);
        mark.setStatus(MarkStatus.RESERVED);
        mark.setActiveReservationId(reservation.getId());
        saveMark(mark);

        ResolveAndReserveResponse response = new ResolveAndReserveResponse();
        response.setResult(result);
        response.setAppliedMark(mark.getMarkCode());
        response.setSource(source);
        response.setReservationId(reservation.getId());
        response.setTtlSec(reservationTtl.toSeconds());
        response.setMessage(message);
        return response;
    }

    private ResolveAndReserveResponse rejectNoCandidate(String message) {
        ResolveAndReserveResponse response = new ResolveAndReserveResponse();
        response.setResult(ResolveResult.REJECT_NO_CANDIDATE);
        response.setErrorCode(ErrorCode.NO_CANDIDATE);
        response.setMessage(message);
        return response;
    }

    private ResolveAndReserveResponse hardReject(ErrorCode errorCode, String message) {
        ResolveAndReserveResponse response = new ResolveAndReserveResponse();
        response.setResult(ResolveResult.HARD_REJECT);
        response.setErrorCode(errorCode);
        response.setMessage(message);
        return response;
    }

    private ReturnResolveAndReserveResponse failReturn(ErrorCode errorCode, String message) {
        ReturnResolveAndReserveResponse response = new ReturnResolveAndReserveResponse();
        response.setSuccess(false);
        response.setErrorCode(errorCode);
        response.setMessage(message);
        return response;
    }

    private ReservationRecord createReservation(String markCode, String operationId, ReservationType reservationType) {
        ReservationRecord reservation = new ReservationRecord();
        reservation.setId(UUID.randomUUID().toString());
        reservation.setOperationId(operationId);
        reservation.setMarkCode(markCode);
        reservation.setType(reservationType);
        reservation.setExpiresAt(Instant.now(clock).plus(reservationTtl));
        upsertReservation(reservation);
        return reservation;
    }

    private Optional<ReservationRecord> findReservation(MarkOperationRequest request, ReservationType expectedType) {
        ReservationRecord byId = null;
        if (!isBlank(request.getReservationId())) {
            byId = reservations.get(request.getReservationId());
        }
        if (byId != null) {
            return byId.getType() == expectedType ? Optional.of(byId) : Optional.empty();
        }

        if (isBlank(request.getMarkCode())) {
            return Optional.empty();
        }

        return reservations.values().stream()
                .filter(r -> r.getType() == expectedType)
                .filter(r -> request.getMarkCode().equals(r.getMarkCode()))
                .findFirst();
    }

    private void cleanupExpiredReservations() {
        Instant now = Instant.now(clock);
        for (ReservationRecord reservation : new ArrayList<>(reservations.values())) {
            if (reservation.getExpiresAt() != null && reservation.getExpiresAt().isAfter(now)) {
                continue;
            }
            MarkRecord mark = marks.get(reservation.getMarkCode());
            if (mark != null && reservation.getId().equals(mark.getActiveReservationId())) {
                if (reservation.getType() == ReservationType.SALE) {
                    mark.setStatus(MarkStatus.AVAILABLE);
                } else {
                    mark.setStatus(MarkStatus.SOLD);
                }
                mark.setActiveReservationId(null);
                saveMark(mark);
            }
            removeReservation(reservation.getId());
        }
    }

    private void cleanupExpiredIdempotencyEntries() {
        Instant threshold = Instant.now(clock).minus(idempotencyRetention);
        long deleted = idempotencyEntryRepository.deleteByUpdatedAtBefore(threshold);
        if (deleted > 0) {
            resolveIdempotency.clear();
            returnResolveIdempotency.clear();
            operationIdempotency.clear();
        }
    }

    private void updateFromImport(MarkRecord mark, ImportMarkItem importItem) {
        mark.setItem(importItem.getItem());
        mark.setGtin(importItem.getGtin());
        mark.setProductType(importItem.getProductType());
        mark.setValid(importItem.isValid());
        mark.setBlocked(importItem.isBlocked());

        if (mark.getStatus() == MarkStatus.AVAILABLE && importItem.getStatus() != null) {
            mark.setStatus(importItem.getStatus());
        }

        if (importItem.getFifoTsEpochMs() != null && mark.getStatus() == MarkStatus.AVAILABLE) {
            mark.setFifoTs(Instant.ofEpochMilli(importItem.getFifoTsEpochMs()));
        }
    }

    private MarkRecord toMarkRecord(ImportMarkItem importItem) {
        MarkRecord record = new MarkRecord();
        record.setMarkCode(importItem.getMarkCode());
        record.setItem(importItem.getItem());
        record.setGtin(importItem.getGtin());
        record.setProductType(importItem.getProductType());
        record.setValid(importItem.isValid());
        record.setBlocked(importItem.isBlocked());
        record.setStatus(importItem.getStatus() != null ? importItem.getStatus() : MarkStatus.AVAILABLE);
        Instant fifoTs = importItem.getFifoTsEpochMs() != null
                ? Instant.ofEpochMilli(importItem.getFifoTsEpochMs())
                : Instant.now(clock);
        record.setFifoTs(fifoTs);
        return record;
    }

    private boolean matchesProduct(MarkRecord mark, ProductRef product) {
        if (mark == null || product == null) {
            return false;
        }

        if (!equalsIgnoreCase(mark.getProductType(), product.getProductType())) {
            return false;
        }

        boolean gtinMatch = isBlank(product.getGtin()) || isBlank(mark.getGtin()) || Objects.equals(mark.getGtin(), product.getGtin());
        if (!gtinMatch) {
            return false;
        }

        return isBlank(product.getItem()) || isBlank(mark.getItem()) || Objects.equals(mark.getItem(), product.getItem());
    }

    private boolean eligibleForSale(MarkRecord mark) {
        return mark != null
                && mark.getStatus() == MarkStatus.AVAILABLE
                && mark.isValid()
                && !mark.isBlocked()
                && isBlank(mark.getActiveReservationId());
    }

    private void recordResolveEvent(String eventType, ResolveAndReserveRequest request, ResolveAndReserveResponse response) {
        MarkingHistoryEvent event = new MarkingHistoryEvent();
        event.setTimestamp(Instant.now(clock));
        event.setEventType(eventType);
        event.setOperationId(request == null ? null : request.getOperationId());
        event.setMarkCode(response == null ? null : response.getAppliedMark());
        event.setReservationId(response == null ? null : response.getReservationId());
        fillProduct(event, request == null ? null : request.getProduct());
        event.setShopId(request == null ? null : request.getShopId());
        event.setPosId(request == null ? null : request.getPosId());
        event.setCashierId(request == null ? null : request.getCashierId());
        event.setSuccess(response != null && (response.getResult() == ResolveResult.ACCEPT_AUTO_SELECTED || response.getResult() == ResolveResult.ACCEPT_SCANNED));
        event.setErrorCode(response == null ? ErrorCode.INVALID_STATE : response.getErrorCode());
        event.setMessage(response == null ? "No response generated." : response.getMessage());
        addHistoryEvent(event);
    }

    private void recordReturnResolveEvent(String eventType, ReturnResolveAndReserveRequest request, ReturnResolveAndReserveResponse response) {
        MarkingHistoryEvent event = new MarkingHistoryEvent();
        event.setTimestamp(Instant.now(clock));
        event.setEventType(eventType);
        event.setOperationId(request == null ? null : request.getOperationId());
        event.setMarkCode(response != null && !isBlank(response.getAppliedMark())
                ? response.getAppliedMark()
                : request == null ? null : request.getScannedMark());
        event.setReservationId(response == null ? null : response.getReservationId());
        fillProduct(event, request == null ? null : request.getProduct());
        event.setShopId(request == null ? null : request.getShopId());
        event.setPosId(request == null ? null : request.getPosId());
        event.setCashierId(request == null ? null : request.getCashierId());
        event.setReceiptId(request == null ? null : request.getSaleReceiptId());
        event.setSuccess(response != null && response.isSuccess());
        event.setErrorCode(response == null ? ErrorCode.INVALID_STATE : response.getErrorCode());
        event.setMessage(response == null ? "No response generated." : response.getMessage());
        addHistoryEvent(event);
    }

    private void recordOperationEvent(String eventType, MarkOperationRequest request, OperationResponse response, String markCode, String reservationId) {
        MarkingHistoryEvent event = new MarkingHistoryEvent();
        event.setTimestamp(Instant.now(clock));
        event.setEventType(eventType);
        event.setOperationId(request == null ? null : request.getOperationId());
        event.setMarkCode(firstNonBlank(markCode, request == null ? null : request.getMarkCode()));
        event.setReservationId(firstNonBlank(reservationId, request == null ? null : request.getReservationId()));
        event.setReceiptId(request == null ? null : request.getReceiptId());
        event.setSuccess(response != null && response.isSuccess());
        event.setErrorCode(response == null ? ErrorCode.INVALID_STATE : response.getErrorCode());
        event.setMessage(response == null ? "No response generated." : response.getMessage());
        addHistoryEvent(event);
    }

    private void recordSimpleEvent(String eventType, boolean success, ErrorCode errorCode, String message) {
        MarkingHistoryEvent event = new MarkingHistoryEvent();
        event.setTimestamp(Instant.now(clock));
        event.setEventType(eventType);
        event.setSuccess(success);
        event.setErrorCode(errorCode);
        event.setMessage(message);
        addHistoryEvent(event);
    }

    private void addHistoryEvent(MarkingHistoryEvent event) {
        if (event == null) {
            return;
        }
        history.add(event);
        historyEventRepository.save(toEntity(event));
        while (history.size() > historyMaxSize) {
            history.remove(0);
        }
    }

    private void fillProduct(MarkingHistoryEvent event, ProductRef product) {
        if (event == null || product == null) {
            return;
        }
        event.setProductType(product.getProductType());
        event.setItem(product.getItem());
        event.setGtin(product.getGtin());
    }

    private <T> T findIdempotent(String route,
                                 String operationId,
                                 String key,
                                 Class<T> responseType,
                                 Map<String, T> cache) {
        if (isBlank(operationId)) {
            return null;
        }

        T cached = cache.get(key);
        if (cached != null) {
            return cached;
        }

        Optional<IdempotencyEntryEntity> persisted = idempotencyEntryRepository.findByRouteAndOperationId(route, operationId);
        if (persisted.isEmpty()) {
            return null;
        }

        IdempotencyEntryEntity entry = persisted.get();
        if (!responseType.getName().equals(entry.getResponseType())) {
            return null;
        }

        try {
            T value = objectMapper.readValue(entry.getResponsePayload(), responseType);
            cache.put(key, value);
            return value;
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private <T> void rememberIdempotent(String route,
                                        String operationId,
                                        String key,
                                        T value,
                                        Map<String, T> cache) {
        if (value == null || isBlank(operationId)) {
            return;
        }

        cache.put(key, value);

        String payload;
        try {
            payload = objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return;
        }

        Instant now = Instant.now(clock);
        IdempotencyEntryEntity entry = idempotencyEntryRepository.findByRouteAndOperationId(route, operationId)
                .orElseGet(IdempotencyEntryEntity::new);
        if (entry.getCreatedAt() == null) {
            entry.setCreatedAt(now);
        }
        entry.setRoute(route);
        entry.setOperationId(operationId);
        entry.setResponseType(value.getClass().getName());
        entry.setResponsePayload(payload);
        entry.setUpdatedAt(now);
        idempotencyEntryRepository.save(entry);
    }

    private void upsertMark(MarkRecord mark) {
        if (mark == null || isBlank(mark.getMarkCode())) {
            return;
        }
        marks.put(mark.getMarkCode(), mark);
        markRepository.save(toEntity(mark));
    }

    private void saveMark(MarkRecord mark) {
        upsertMark(mark);
    }

    private void deleteMark(String markCode) {
        if (isBlank(markCode)) {
            return;
        }
        marks.remove(markCode);
        markRepository.deleteById(markCode);
    }

    private void upsertReservation(ReservationRecord reservation) {
        if (reservation == null || isBlank(reservation.getId())) {
            return;
        }
        reservations.put(reservation.getId(), reservation);
        reservationRepository.save(toEntity(reservation));
    }

    private void removeReservation(String reservationId) {
        if (isBlank(reservationId)) {
            return;
        }
        reservations.remove(reservationId);
        reservationRepository.deleteById(reservationId);
    }

    private MarkRecord fromEntity(MarkEntity entity) {
        MarkRecord model = new MarkRecord();
        model.setMarkCode(entity.getMarkCode());
        model.setItem(entity.getItem());
        model.setGtin(entity.getGtin());
        model.setProductType(entity.getProductType());
        model.setValid(entity.isValid());
        model.setBlocked(entity.isBlocked());
        model.setStatus(MarkStatus.valueOf(entity.getStatus()));
        model.setFifoTs(entity.getFifoTs());
        model.setActiveReservationId(entity.getActiveReservationId());
        model.setLastSaleReceiptId(entity.getLastSaleReceiptId());
        model.setLastReturnReceiptId(entity.getLastReturnReceiptId());
        return model;
    }

    private MarkEntity toEntity(MarkRecord model) {
        MarkEntity entity = new MarkEntity();
        entity.setMarkCode(model.getMarkCode());
        entity.setItem(model.getItem());
        entity.setGtin(model.getGtin());
        entity.setProductType(model.getProductType());
        entity.setValid(model.isValid());
        entity.setBlocked(model.isBlocked());
        entity.setStatus(model.getStatus() == null ? MarkStatus.AVAILABLE.name() : model.getStatus().name());
        entity.setFifoTs(model.getFifoTs() == null ? Instant.now(clock) : model.getFifoTs());
        entity.setActiveReservationId(model.getActiveReservationId());
        entity.setLastSaleReceiptId(model.getLastSaleReceiptId());
        entity.setLastReturnReceiptId(model.getLastReturnReceiptId());
        return entity;
    }

    private ReservationRecord fromEntity(ReservationEntity entity) {
        ReservationRecord model = new ReservationRecord();
        model.setId(entity.getId());
        model.setOperationId(entity.getOperationId());
        model.setMarkCode(entity.getMarkCode());
        model.setType(ReservationType.valueOf(entity.getType()));
        model.setExpiresAt(entity.getExpiresAt());
        return model;
    }

    private ReservationEntity toEntity(ReservationRecord model) {
        ReservationEntity entity = new ReservationEntity();
        entity.setId(model.getId());
        entity.setOperationId(model.getOperationId());
        entity.setMarkCode(model.getMarkCode());
        entity.setType(model.getType() == null ? ReservationType.SALE.name() : model.getType().name());
        entity.setExpiresAt(model.getExpiresAt() == null ? Instant.now(clock).plus(reservationTtl) : model.getExpiresAt());
        return entity;
    }

    private MarkingHistoryEvent fromEntity(HistoryEventEntity entity) {
        MarkingHistoryEvent model = new MarkingHistoryEvent();
        model.setTimestamp(entity.getEventTs());
        model.setEventType(entity.getEventType());
        model.setOperationId(entity.getOperationId());
        model.setMarkCode(entity.getMarkCode());
        model.setReservationId(entity.getReservationId());
        model.setProductType(entity.getProductType());
        model.setItem(entity.getItem());
        model.setGtin(entity.getGtin());
        model.setShopId(entity.getShopId());
        model.setPosId(entity.getPosId());
        model.setCashierId(entity.getCashierId());
        model.setReceiptId(entity.getReceiptId());
        model.setSuccess(entity.isSuccess());
        model.setErrorCode(parseErrorCode(entity.getErrorCode()));
        model.setMessage(entity.getMessage());
        return model;
    }

    private HistoryEventEntity toEntity(MarkingHistoryEvent model) {
        HistoryEventEntity entity = new HistoryEventEntity();
        entity.setEventTs(model.getTimestamp() == null ? Instant.now(clock) : model.getTimestamp());
        entity.setEventType(model.getEventType());
        entity.setOperationId(model.getOperationId());
        entity.setMarkCode(model.getMarkCode());
        entity.setReservationId(model.getReservationId());
        entity.setProductType(model.getProductType());
        entity.setItem(model.getItem());
        entity.setGtin(model.getGtin());
        entity.setShopId(model.getShopId());
        entity.setPosId(model.getPosId());
        entity.setCashierId(model.getCashierId());
        entity.setReceiptId(model.getReceiptId());
        entity.setSuccess(model.isSuccess());
        entity.setErrorCode((model.getErrorCode() == null ? ErrorCode.NONE : model.getErrorCode()).name());
        entity.setMessage(model.getMessage());
        return entity;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean equalsIgnoreCase(String left, String right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.equalsIgnoreCase(right);
    }

    private String idempotencyKey(String route, String operationId) {
        return route + ":" + (operationId == null ? "" : operationId);
    }

    private String firstNonBlank(String first, String second) {
        return isBlank(first) ? second : first;
    }

    private ErrorCode parseErrorCode(String value) {
        if (isBlank(value)) {
            return ErrorCode.NONE;
        }
        try {
            return ErrorCode.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return ErrorCode.NONE;
        }
    }
}
