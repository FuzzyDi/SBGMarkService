package uz.sbg.marking.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import uz.sbg.marking.plugin.dto.ErrorCode;
import uz.sbg.marking.plugin.dto.MarkOperationRequest;
import uz.sbg.marking.plugin.dto.OperationResponse;
import uz.sbg.marking.plugin.dto.ProductRef;
import uz.sbg.marking.plugin.dto.ResolveAndReserveRequest;
import uz.sbg.marking.plugin.dto.ResolveAndReserveResponse;
import uz.sbg.marking.plugin.dto.ResolveResult;
import uz.sbg.marking.plugin.dto.ReturnResolveAndReserveResponse;

import static org.junit.Assert.*;

/**
 * Проверяет сериализацию/десериализацию DTO через Jackson.
 * Гарантирует, что JSON-контракт с backend не сломан.
 */
public class DtoSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    // ----- ResolveAndReserveRequest -----

    @Test
    public void testResolveRequestSerialization() throws Exception {
        ResolveAndReserveRequest req = new ResolveAndReserveRequest();
        req.setOperationId("SALE-1-1-5-42-999");
        req.setShopId("1");
        req.setPosId("2");
        req.setCashierId("cashier-001");
        req.setScannedMark("0104600727070879215VwMfL4gC5VTz");

        ProductRef product = new ProductRef();
        product.setBarcode("4600727070879");
        product.setGtin("04600727070879");
        product.setProductType("TOBACCO");
        product.setItem("ART-001");
        req.setProduct(product);

        String json = mapper.writeValueAsString(req);
        assertNotNull(json);
        assertTrue("должен содержать scannedMark", json.contains("scannedMark"));
        assertTrue("должен содержать operationId", json.contains("operationId"));
        assertTrue("должен содержать barcode", json.contains("barcode"));
        assertTrue("должен содержать gtin", json.contains("gtin"));

        // Десериализация обратно
        ResolveAndReserveRequest decoded = mapper.readValue(json, ResolveAndReserveRequest.class);
        assertEquals("0104600727070879215VwMfL4gC5VTz", decoded.getScannedMark());
        assertEquals("1", decoded.getShopId());
        assertEquals("04600727070879", decoded.getProduct().getGtin());
    }

    // ----- ResolveAndReserveResponse -----

    @Test
    public void testAcceptScannedResponseDeserialization() throws Exception {
        String json = "{\"result\":\"ACCEPT_SCANNED\",\"reservationId\":\"res-001\",\"appliedMark\":null,\"errorCode\":\"NONE\",\"message\":null,\"ttlSec\":300}";
        ResolveAndReserveResponse resp = mapper.readValue(json, ResolveAndReserveResponse.class);
        assertEquals(ResolveResult.ACCEPT_SCANNED, resp.getResult());
        assertEquals("res-001", resp.getReservationId());
        assertNull(resp.getAppliedMark());
        assertEquals(ErrorCode.NONE, resp.getErrorCode());
    }

    @Test
    public void testAcceptAutoSelectedResponseDeserialization() throws Exception {
        String json = "{\"result\":\"ACCEPT_AUTO_SELECTED\",\"reservationId\":\"res-002\",\"appliedMark\":\"0104600727070879215AUTO000000001\",\"errorCode\":\"NONE\",\"message\":null}";
        ResolveAndReserveResponse resp = mapper.readValue(json, ResolveAndReserveResponse.class);
        assertEquals(ResolveResult.ACCEPT_AUTO_SELECTED, resp.getResult());
        assertEquals("0104600727070879215AUTO000000001", resp.getAppliedMark());
        assertEquals("res-002", resp.getReservationId());
    }

    @Test
    public void testRejectResponseDeserialization() throws Exception {
        String json = "{\"result\":\"HARD_REJECT\",\"reservationId\":null,\"appliedMark\":null,\"errorCode\":\"INVALID_MARK_FOR_PRODUCT\",\"message\":\"Mark does not match product\"}";
        ResolveAndReserveResponse resp = mapper.readValue(json, ResolveAndReserveResponse.class);
        assertEquals(ResolveResult.HARD_REJECT, resp.getResult());
        assertEquals(ErrorCode.INVALID_MARK_FOR_PRODUCT, resp.getErrorCode());
        assertEquals("Mark does not match product", resp.getMessage());
    }

    // ----- ReturnResolveAndReserveResponse -----

    @Test
    public void testReturnSuccessDeserialization() throws Exception {
        String json = "{\"success\":true,\"reservationId\":\"ret-res-001\",\"appliedMark\":\"MARK001\",\"errorCode\":\"NONE\",\"message\":null}";
        ReturnResolveAndReserveResponse resp = mapper.readValue(json, ReturnResolveAndReserveResponse.class);
        assertTrue(resp.isSuccess());
        assertEquals("ret-res-001", resp.getReservationId());
        assertEquals("MARK001", resp.getAppliedMark());
    }

    @Test
    public void testReturnFailDeserialization() throws Exception {
        String json = "{\"success\":false,\"reservationId\":null,\"appliedMark\":null,\"errorCode\":\"ALREADY_RETURNED\",\"message\":\"Already returned\"}";
        ReturnResolveAndReserveResponse resp = mapper.readValue(json, ReturnResolveAndReserveResponse.class);
        assertFalse(resp.isSuccess());
        assertEquals(ErrorCode.ALREADY_RETURNED, resp.getErrorCode());
    }

    // ----- OperationResponse -----

    @Test
    public void testOperationResponseSuccess() throws Exception {
        String json = "{\"success\":true,\"errorCode\":\"NONE\",\"message\":\"OK\"}";
        OperationResponse resp = mapper.readValue(json, OperationResponse.class);
        assertTrue(resp.isSuccess());
        assertEquals(ErrorCode.NONE, resp.getErrorCode());
    }

    @Test
    public void testOperationResponseFailure() throws Exception {
        String json = "{\"success\":false,\"errorCode\":\"INTERNAL_ERROR\",\"message\":\"Something went wrong\"}";
        OperationResponse resp = mapper.readValue(json, OperationResponse.class);
        assertFalse(resp.isSuccess());
        assertEquals(ErrorCode.INTERNAL_ERROR, resp.getErrorCode());
        assertEquals("Something went wrong", resp.getMessage());
    }

    // ----- MarkOperationRequest -----

    @Test
    public void testMarkOperationRequestSerialization() throws Exception {
        MarkOperationRequest req = new MarkOperationRequest();
        req.setOperationId("sold-confirm-1-1-5-42-999");
        req.setReservationId("res-001");
        req.setMarkCode("0104600727070879215VwMfL4gC5VTz");
        req.setReceiptId("42");
        req.setReceiptNumber(42);
        req.setShiftNumber(5);
        req.setFiscalDocId(12345L);
        req.setFiscalSign(9876543210L);

        String json = mapper.writeValueAsString(req);
        assertTrue(json.contains("reservationId"));
        assertTrue(json.contains("markCode"));
        assertTrue(json.contains("fiscalDocId"));

        MarkOperationRequest decoded = mapper.readValue(json, MarkOperationRequest.class);
        assertEquals("res-001", decoded.getReservationId());
        assertEquals("0104600727070879215VwMfL4gC5VTz", decoded.getMarkCode());
        assertEquals(Long.valueOf(12345L), decoded.getFiscalDocId());
    }

    // ----- PendingOperationsPayload -----

    @Test
    public void testPendingOperationsPayloadRoundTrip() throws Exception {
        MarkOperationRequest req = new MarkOperationRequest();
        req.setReservationId("res-001");
        req.setMarkCode("MARK001");
        req.setOperationId("op-001");

        PendingOperationsPayload payload = new PendingOperationsPayload();
        payload.setEndpoint("sold-confirm");
        payload.getRequests().add(req);

        String json = mapper.writeValueAsString(payload);
        assertTrue(json.contains("sold-confirm"));
        assertTrue(json.contains("MARK001"));

        PendingOperationsPayload decoded = mapper.readValue(json, PendingOperationsPayload.class);
        assertEquals("sold-confirm", decoded.getEndpoint());
        assertEquals(1, decoded.getRequests().size());
        assertEquals("MARK001", decoded.getRequests().get(0).getMarkCode());
    }

    // ----- Unknown fields tolerance -----

    @Test
    public void testUnknownFieldsInResponseAreIgnored() throws Exception {
        // Backend может добавить новые поля — плагин не должен падать
        String json = "{\"result\":\"ACCEPT_SCANNED\",\"reservationId\":\"res-001\",\"newUnknownField\":\"someValue\",\"errorCode\":\"NONE\"}";
        ResolveAndReserveResponse resp = mapper.readValue(json, ResolveAndReserveResponse.class);
        assertEquals(ResolveResult.ACCEPT_SCANNED, resp.getResult());
    }
}
