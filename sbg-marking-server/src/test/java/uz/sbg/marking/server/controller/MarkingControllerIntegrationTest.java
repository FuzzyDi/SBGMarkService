package uz.sbg.marking.server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import uz.sbg.marking.contracts.ImportMarkItem;
import uz.sbg.marking.contracts.ImportRequest;
import uz.sbg.marking.contracts.MarkStatus;
import uz.sbg.marking.contracts.ProductRef;
import uz.sbg.marking.contracts.ResolveAndReserveRequest;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class MarkingControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void resolveEndpointShouldAutoSelectByFifo() throws Exception {
        ImportRequest importRequest = new ImportRequest();
        importRequest.setBatchId("api-batch-1");
        importRequest.setItems(List.of(
                mark("KM-OLD", 1000L),
                mark("KM-NEW", 2000L)
        ));

        mockMvc.perform(post("/api/v1/km/import/full")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(importRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(2));

        ResolveAndReserveRequest resolveRequest = new ResolveAndReserveRequest();
        resolveRequest.setOperationId("api-op-sale-1");
        resolveRequest.setShopId("1");
        resolveRequest.setPosId("1");
        resolveRequest.setCashierId("cashier-api");
        resolveRequest.setScannedMark("NOT-IN-DB");
        resolveRequest.setQuantity(1);

        ProductRef product = new ProductRef();
        product.setProductType("TOBACCO");
        product.setItem("ITEM-1");
        product.setGtin("GTIN-1");
        resolveRequest.setProduct(product);

        mockMvc.perform(post("/api/v1/marking/resolve-and-reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resolveRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("ACCEPT_AUTO_SELECTED"))
                .andExpect(jsonPath("$.appliedMark").value("KM-OLD"))
                .andExpect(jsonPath("$.reservationId").isNotEmpty());
    }

    @Test
    void reportsEndpointsShouldReturnSummaryAndHistory() throws Exception {
        ImportRequest importRequest = new ImportRequest();
        importRequest.setBatchId("api-batch-2");
        importRequest.setItems(List.of(mark("KM-RPT-1", 1000L)));

        mockMvc.perform(post("/api/v1/km/import/full")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(importRequest)))
                .andExpect(status().isOk());

        ResolveAndReserveRequest resolveRequest = new ResolveAndReserveRequest();
        resolveRequest.setOperationId("api-op-sale-2");
        resolveRequest.setShopId("1");
        resolveRequest.setPosId("1");
        resolveRequest.setCashierId("cashier-api");
        resolveRequest.setScannedMark("KM-RPT-1");
        resolveRequest.setQuantity(1);

        ProductRef product = new ProductRef();
        product.setProductType("TOBACCO");
        product.setItem("ITEM-1");
        product.setGtin("GTIN-1");
        resolveRequest.setProduct(product);

        mockMvc.perform(post("/api/v1/marking/resolve-and-reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resolveRequest)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/reports/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMarks").value(1))
                .andExpect(jsonPath("$.historyEvents").value(org.hamcrest.Matchers.greaterThan(0)));

        mockMvc.perform(get("/api/v1/reports/history").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.events").isArray());
    }

    @Test
    void fifoByProductEndpointShouldReturnQueueAndReasons() throws Exception {
        ImportRequest importRequest = new ImportRequest();
        importRequest.setBatchId("api-batch-3");
        importRequest.setItems(List.of(
                mark("KM-FIFO-1", 1000L),
                mark("KM-FIFO-2", 2000L)
        ));
        importRequest.getItems().get(1).setBlocked(true);

        mockMvc.perform(post("/api/v1/km/import/full")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(importRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(2));

        mockMvc.perform(get("/api/v1/km/debug/fifo-by-product")
                        .param("productType", "TOBACCO")
                        .param("item", "ITEM-1")
                        .param("gtin", "GTIN-1")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.selectableCount").value(1))
                .andExpect(jsonPath("$.firstSelectableMark").value("KM-FIFO-1"))
                .andExpect(jsonPath("$.candidates[0].markCode").value("KM-FIFO-1"))
                .andExpect(jsonPath("$.candidates[0].selectable").value(true))
                .andExpect(jsonPath("$.candidates[0].reason").value("OK"))
                .andExpect(jsonPath("$.candidates[1].markCode").value("KM-FIFO-2"))
                .andExpect(jsonPath("$.candidates[1].selectable").value(false))
                .andExpect(jsonPath("$.candidates[1].reason").value("BLOCKED_FLAG_TRUE"));
    }

    private ImportMarkItem mark(String code, long fifoTsEpochMs) {
        ImportMarkItem item = new ImportMarkItem();
        item.setMarkCode(code);
        item.setItem("ITEM-1");
        item.setGtin("GTIN-1");
        item.setProductType("TOBACCO");
        item.setValid(true);
        item.setBlocked(false);
        item.setStatus(MarkStatus.AVAILABLE);
        item.setFifoTsEpochMs(fifoTsEpochMs);
        return item;
    }
}
