package uz.sbg.marking.server.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import uz.sbg.marking.contracts.FifoByProductResponse;
import uz.sbg.marking.contracts.HistoryQueryResponse;
import uz.sbg.marking.contracts.ImportMarkItem;
import uz.sbg.marking.contracts.ImportRequest;
import uz.sbg.marking.contracts.MarkAdminUpsertRequest;
import uz.sbg.marking.contracts.MarkOperationRequest;
import uz.sbg.marking.contracts.MarkStatus;
import uz.sbg.marking.contracts.OperationResponse;
import uz.sbg.marking.contracts.ProductRef;
import uz.sbg.marking.contracts.ResolveAndReserveRequest;
import uz.sbg.marking.contracts.ResolveAndReserveResponse;
import uz.sbg.marking.contracts.ResolveResult;
import uz.sbg.marking.contracts.ReturnResolveAndReserveRequest;
import uz.sbg.marking.contracts.ReturnResolveAndReserveResponse;
import uz.sbg.marking.contracts.ValidationOperationType;
import uz.sbg.marking.contracts.ValidationPolicy;
import uz.sbg.marking.contracts.ValidationRequest;
import uz.sbg.marking.contracts.ValidationResponse;
import uz.sbg.marking.contracts.ValidationResultCode;
import uz.sbg.marking.server.model.MarkRecord;
import uz.sbg.marking.server.persistence.entity.IdempotencyEntryEntity;
import uz.sbg.marking.server.persistence.repository.IdempotencyEntryRepository;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class MarkingServiceIntegrationTest {

    @Autowired
    private MarkingService markingService;

    @Autowired
    private IdempotencyEntryRepository idempotencyEntryRepository;

    @Test
    void shouldAutoSelectFirstFifoMarkWhenScannedIsAbsent() {
        importMarks(List.of(
                mark("KM-OLD", "ITEM-1", "GTIN-1", "TOBACCO", true, false, MarkStatus.AVAILABLE, 1000L),
                mark("KM-NEW", "ITEM-1", "GTIN-1", "TOBACCO", true, false, MarkStatus.AVAILABLE, 2000L)
        ));

        ResolveAndReserveRequest request = saleResolveRequest("op-sale-1", "NOT-IN-DB", "ITEM-1", "GTIN-1", "TOBACCO");
        ResolveAndReserveResponse response = markingService.resolveAndReserve(request);

        assertThat(response.getResult()).isEqualTo(ResolveResult.ACCEPT_AUTO_SELECTED);
        assertThat(response.getAppliedMark()).isEqualTo("KM-OLD");
        assertThat(response.getReservationId()).isNotBlank();
    }

    @Test
    void shouldReturnMarkToAvailableAfterRefundConfirm() {
        importMarks(List.of(
                mark("KM-1", "ITEM-1", "GTIN-1", "TOBACCO", true, false, MarkStatus.AVAILABLE, 1000L)
        ));

        ResolveAndReserveResponse sale = markingService.resolveAndReserve(
                saleResolveRequest("op-sale-2", "KM-1", "ITEM-1", "GTIN-1", "TOBACCO")
        );
        assertThat(sale.getResult()).isEqualTo(ResolveResult.ACCEPT_SCANNED);

        OperationResponse soldConfirm = markingService.soldConfirm(operation("op-sale-confirm-2", sale.getReservationId(), sale.getAppliedMark(), "RCP-1"));
        assertThat(soldConfirm.isSuccess()).isTrue();

        ReturnResolveAndReserveResponse retReserve = markingService.returnResolveAndReserve(
                returnResolveRequest("op-return-2", "KM-1", "ITEM-1", "GTIN-1", "TOBACCO", "RCP-1")
        );
        assertThat(retReserve.isSuccess()).isTrue();
        assertThat(retReserve.getAppliedMark()).isEqualTo("KM-1");

        OperationResponse retConfirm = markingService.returnConfirm(operation("op-return-confirm-2", retReserve.getReservationId(), retReserve.getAppliedMark(), "RCP-RET-1"));
        assertThat(retConfirm.isSuccess()).isTrue();

        MarkRecord mark = markingService.snapshotMarks().stream()
                .filter(m -> "KM-1".equals(m.getMarkCode()))
                .findFirst()
                .orElseThrow();

        assertThat(mark.getStatus()).isEqualTo(MarkStatus.AVAILABLE);
    }

    @Test
    void shouldBeIdempotentForResolveByOperationId() {
        importMarks(List.of(
                mark("KM-2", "ITEM-2", "GTIN-2", "TOBACCO", true, false, MarkStatus.AVAILABLE, 1000L)
        ));

        ResolveAndReserveRequest request = saleResolveRequest("op-sale-idem-1", "KM-2", "ITEM-2", "GTIN-2", "TOBACCO");

        ResolveAndReserveResponse first = markingService.resolveAndReserve(request);
        ResolveAndReserveResponse second = markingService.resolveAndReserve(request);

        assertThat(first.getResult()).isEqualTo(ResolveResult.ACCEPT_SCANNED);
        assertThat(second.getResult()).isEqualTo(ResolveResult.ACCEPT_SCANNED);
        assertThat(second.getReservationId()).isEqualTo(first.getReservationId());
        assertThat(second.getAppliedMark()).isEqualTo(first.getAppliedMark());
    }

    @Test
    void shouldReturnFifoQueueWithSaleSuitabilityReasons() {
        importMarks(List.of(
                mark("KM-Q-1", "ITEM-QUEUE", "GTIN-QUEUE", "TOBACCO", true, false, MarkStatus.AVAILABLE, 1000L),
                mark("KM-Q-2", "ITEM-QUEUE", "GTIN-QUEUE", "TOBACCO", true, true, MarkStatus.AVAILABLE, 2000L),
                mark("KM-Q-3", "ITEM-QUEUE", "GTIN-QUEUE", "TOBACCO", true, false, MarkStatus.SOLD, 3000L)
        ));

        FifoByProductResponse queue = markingService.debugFifoByProduct("ITEM-QUEUE", "GTIN-QUEUE", "TOBACCO", 10);

        assertThat(queue.getTotal()).isEqualTo(3);
        assertThat(queue.getSelectableCount()).isEqualTo(1);
        assertThat(queue.getFirstSelectableMark()).isEqualTo("KM-Q-1");
        assertThat(queue.getCandidates()).hasSize(3);

        assertThat(queue.getCandidates().get(0).getMarkCode()).isEqualTo("KM-Q-1");
        assertThat(queue.getCandidates().get(0).isSelectable()).isTrue();
        assertThat(queue.getCandidates().get(0).getReason()).isEqualTo("OK");

        assertThat(queue.getCandidates().get(1).getMarkCode()).isEqualTo("KM-Q-2");
        assertThat(queue.getCandidates().get(1).isSelectable()).isFalse();
        assertThat(queue.getCandidates().get(1).getReason()).isEqualTo("BLOCKED_FLAG_TRUE");

        assertThat(queue.getCandidates().get(2).getMarkCode()).isEqualTo("KM-Q-3");
        assertThat(queue.getCandidates().get(2).isSelectable()).isFalse();
        assertThat(queue.getCandidates().get(2).getReason()).isEqualTo("STATUS_SOLD");
    }

    @Test
    void shouldValidateMarkByPolicy() {
        importMarks(List.of(
                mark("KM-V-1", "ITEM-V", "GTIN-V", "TOBACCO", true, false, MarkStatus.AVAILABLE, 1000L),
                mark("KM-V-2", "ITEM-V", "GTIN-V", "TOBACCO", false, false, MarkStatus.AVAILABLE, 2000L)
        ));

        ValidationRequest okRequest = new ValidationRequest();
        okRequest.setOperationType(ValidationOperationType.SALE);
        okRequest.setScannedMark("KM-V-1");
        ProductRef product = new ProductRef();
        product.setProductType("TOBACCO");
        product.setItem("ITEM-V");
        product.setGtin("GTIN-V");
        okRequest.setProduct(product);

        ValidationResponse ok = markingService.validateMark(okRequest);
        assertThat(ok.isSuccess()).isTrue();
        assertThat(ok.getCode()).isEqualTo(ValidationResultCode.OK);

        ValidationRequest invalidFlagRequest = new ValidationRequest();
        invalidFlagRequest.setOperationType(ValidationOperationType.SALE);
        invalidFlagRequest.setScannedMark("KM-V-2");
        invalidFlagRequest.setProduct(product);

        ValidationResponse invalid = markingService.validateMark(invalidFlagRequest);
        assertThat(invalid.isSuccess()).isFalse();
        assertThat(invalid.getCode()).isEqualTo(ValidationResultCode.INVALID_FLAG);
    }

    @Test
    void shouldUpdateValidationPolicyAndApplyRules() {
        importMarks(List.of(
                mark("KM-P-1", "ITEM-P", "GTIN-P", "TOBACCO", false, false, MarkStatus.AVAILABLE, 1000L)
        ));

        ValidationPolicy policy = markingService.getValidationPolicy();
        policy.setRejectInvalidFlag(false);
        policy.setRejectUnknownMark(false);
        policy.setSaleAllowedStatuses(List.of(MarkStatus.AVAILABLE, MarkStatus.SOLD));
        markingService.updateValidationPolicy(policy);

        ValidationRequest request = new ValidationRequest();
        request.setOperationType(ValidationOperationType.SALE);
        request.setScannedMark("KM-P-1");
        ProductRef product = new ProductRef();
        product.setProductType("TOBACCO");
        product.setItem("ITEM-P");
        product.setGtin("GTIN-P");
        request.setProduct(product);

        ValidationResponse response = markingService.validateMark(request);
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo(ValidationResultCode.OK);
    }

    @Test
    void shouldUpsertAndDeleteMarkViaAdminMethods() {
        MarkAdminUpsertRequest create = new MarkAdminUpsertRequest();
        create.setMarkCode("KM-A-1");
        create.setProductType("TOBACCO");
        create.setItem("ITEM-A");
        create.setGtin("GTIN-A");
        create.setValid(true);
        create.setBlocked(false);
        create.setStatus(MarkStatus.AVAILABLE);
        create.setFifoTsEpochMs(1000L);

        OperationResponse created = markingService.adminUpsertMark(create, null);
        assertThat(created.isSuccess()).isTrue();

        MarkAdminUpsertRequest update = new MarkAdminUpsertRequest();
        update.setBlocked(true);
        OperationResponse updated = markingService.adminUpsertMark(update, "KM-A-1");
        assertThat(updated.isSuccess()).isTrue();

        List<MarkRecord> filtered = markingService.adminQueryMarks("TOBACCO", "ITEM-A", "GTIN-A", null, null, true, 10, "KM-A");
        assertThat(filtered).hasSize(1);
        assertThat(filtered.get(0).isBlocked()).isTrue();

        MarkAdminUpsertRequest unlock = new MarkAdminUpsertRequest();
        unlock.setBlocked(false);
        OperationResponse unlocked = markingService.adminUpsertMark(unlock, "KM-A-1");
        assertThat(unlocked.isSuccess()).isTrue();

        OperationResponse deleted = markingService.adminDeleteMark("KM-A-1");
        assertThat(deleted.isSuccess()).isTrue();
        assertThat(markingService.snapshotMarks().stream().noneMatch(m -> "KM-A-1".equals(m.getMarkCode()))).isTrue();
    }

    @Test
    void shouldCleanupOldIdempotencyEntries() {
        IdempotencyEntryEntity oldEntry = new IdempotencyEntryEntity();
        oldEntry.setRoute("resolve");
        oldEntry.setOperationId("op-old-idem");
        oldEntry.setResponseType(OperationResponse.class.getName());
        oldEntry.setResponsePayload("{\"success\":true,\"errorCode\":\"NONE\",\"message\":\"ok\"}");
        oldEntry.setCreatedAt(Instant.now().minusSeconds(172800));
        oldEntry.setUpdatedAt(Instant.now().minusSeconds(172800));
        idempotencyEntryRepository.save(oldEntry);

        assertThat(idempotencyEntryRepository.findByRouteAndOperationId("resolve", "op-old-idem")).isPresent();

        markingService.scheduledCleanup();

        assertThat(idempotencyEntryRepository.findByRouteAndOperationId("resolve", "op-old-idem")).isEmpty();
    }

    @Test
    void shouldFilterHistoryAndExportCsv() {
        importMarks(List.of(
                mark("KM-H-1", "ITEM-H", "GTIN-H", "TOBACCO", true, false, MarkStatus.AVAILABLE, 1000L),
                mark("KM-H-2", "ITEM-H", "GTIN-H", "TOBACCO", true, false, MarkStatus.AVAILABLE, 2000L)
        ));

        ResolveAndReserveRequest first = saleResolveRequest("op-hist-1", "NOT-IN-DB-1", "ITEM-H", "GTIN-H", "TOBACCO");
        first.setShopId("SHOP-A");
        first.setPosId("POS-A");
        first.setCashierId("CASH-A");
        markingService.resolveAndReserve(first);

        ResolveAndReserveRequest second = saleResolveRequest("op-hist-2", "NOT-IN-DB-2", "ITEM-H", "GTIN-H", "TOBACCO");
        second.setShopId("SHOP-B");
        second.setPosId("POS-B");
        second.setCashierId("CASH-B");
        markingService.resolveAndReserve(second);

        HistoryQueryResponse filtered = markingService.queryHistory(
                null,
                null,
                null,
                100,
                "SALE_RESOLVE",
                "SHOP-A",
                null,
                "CASH-A",
                true
        );

        assertThat(filtered.getTotal()).isEqualTo(1);
        assertThat(filtered.getEvents()).hasSize(1);
        assertThat(filtered.getEvents().get(0).getEventType()).isEqualTo("SALE_RESOLVE");
        assertThat(filtered.getEvents().get(0).getShopId()).isEqualTo("SHOP-A");
        assertThat(filtered.getEvents().get(0).getCashierId()).isEqualTo("CASH-A");

        String csv = markingService.exportHistoryCsv(
                null,
                null,
                null,
                100,
                "SALE_RESOLVE",
                "SHOP-A",
                null,
                "CASH-A",
                true
        );

        assertThat(csv).contains("timestamp,eventType,operationId");
        assertThat(csv).contains("SALE_RESOLVE");
        assertThat(csv).contains("SHOP-A");
        assertThat(csv).doesNotContain("SHOP-B");
    }

    private void importMarks(List<ImportMarkItem> items) {
        ImportRequest request = new ImportRequest();
        request.setBatchId("test-batch");
        request.setItems(items);
        markingService.importFull(request);
    }

    private ImportMarkItem mark(String code,
                                String item,
                                String gtin,
                                String productType,
                                boolean valid,
                                boolean blocked,
                                MarkStatus status,
                                long fifoEpochMs) {
        ImportMarkItem m = new ImportMarkItem();
        m.setMarkCode(code);
        m.setItem(item);
        m.setGtin(gtin);
        m.setProductType(productType);
        m.setValid(valid);
        m.setBlocked(blocked);
        m.setStatus(status);
        m.setFifoTsEpochMs(fifoEpochMs);
        return m;
    }

    private ResolveAndReserveRequest saleResolveRequest(String operationId,
                                                        String scannedMark,
                                                        String item,
                                                        String gtin,
                                                        String productType) {
        ResolveAndReserveRequest request = new ResolveAndReserveRequest();
        request.setOperationId(operationId);
        request.setShopId("1");
        request.setPosId("1");
        request.setCashierId("cashier-1");
        request.setScannedMark(scannedMark);
        request.setQuantity(1);

        ProductRef product = new ProductRef();
        product.setItem(item);
        product.setGtin(gtin);
        product.setProductType(productType);
        request.setProduct(product);
        return request;
    }

    private ReturnResolveAndReserveRequest returnResolveRequest(String operationId,
                                                                String scannedMark,
                                                                String item,
                                                                String gtin,
                                                                String productType,
                                                                String saleReceiptId) {
        ReturnResolveAndReserveRequest request = new ReturnResolveAndReserveRequest();
        request.setOperationId(operationId);
        request.setShopId("1");
        request.setPosId("1");
        request.setCashierId("cashier-1");
        request.setScannedMark(scannedMark);
        request.setSaleReceiptId(saleReceiptId);

        ProductRef product = new ProductRef();
        product.setItem(item);
        product.setGtin(gtin);
        product.setProductType(productType);
        request.setProduct(product);
        return request;
    }

    private MarkOperationRequest operation(String operationId, String reservationId, String markCode, String receiptId) {
        MarkOperationRequest request = new MarkOperationRequest();
        request.setOperationId(operationId);
        request.setReservationId(reservationId);
        request.setMarkCode(markCode);
        request.setReceiptId(receiptId);
        return request;
    }
}
