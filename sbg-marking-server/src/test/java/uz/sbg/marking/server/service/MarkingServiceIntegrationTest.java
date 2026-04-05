package uz.sbg.marking.server.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import uz.sbg.marking.contracts.ImportMarkItem;
import uz.sbg.marking.contracts.ImportRequest;
import uz.sbg.marking.contracts.MarkOperationRequest;
import uz.sbg.marking.contracts.MarkStatus;
import uz.sbg.marking.contracts.OperationResponse;
import uz.sbg.marking.contracts.ProductRef;
import uz.sbg.marking.contracts.ResolveAndReserveRequest;
import uz.sbg.marking.contracts.ResolveAndReserveResponse;
import uz.sbg.marking.contracts.ResolveResult;
import uz.sbg.marking.contracts.ReturnResolveAndReserveRequest;
import uz.sbg.marking.contracts.ReturnResolveAndReserveResponse;
import uz.sbg.marking.server.model.MarkRecord;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class MarkingServiceIntegrationTest {

    @Autowired
    private MarkingService markingService;

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
