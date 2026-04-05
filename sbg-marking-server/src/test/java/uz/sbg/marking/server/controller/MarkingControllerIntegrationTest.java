package uz.sbg.marking.server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import uz.sbg.marking.contracts.ImportMarkItem;
import uz.sbg.marking.contracts.ImportRequest;
import uz.sbg.marking.contracts.MarkAdminUpsertRequest;
import uz.sbg.marking.contracts.MarkStatus;
import uz.sbg.marking.contracts.ProductRef;
import uz.sbg.marking.contracts.ResolveAndReserveRequest;
import uz.sbg.marking.contracts.ValidationOperationType;
import uz.sbg.marking.contracts.ValidationPolicy;
import uz.sbg.marking.contracts.ValidationRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
    void reportsHistoryShouldSupportFiltersAndCsvExport() throws Exception {
        ImportRequest importRequest = new ImportRequest();
        importRequest.setBatchId("api-batch-4");
        importRequest.setItems(List.of(mark("KM-HIST-1", 1000L)));

        mockMvc.perform(post("/api/v1/km/import/full")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(importRequest)))
                .andExpect(status().isOk());

        ResolveAndReserveRequest resolveRequest = new ResolveAndReserveRequest();
        resolveRequest.setOperationId("api-op-sale-4");
        resolveRequest.setShopId("SHOP-CSV");
        resolveRequest.setPosId("POS-CSV");
        resolveRequest.setCashierId("CASH-CSV");
        resolveRequest.setScannedMark("NOT-IN-POOL");
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
                .andExpect(jsonPath("$.result").value("ACCEPT_AUTO_SELECTED"));

        mockMvc.perform(get("/api/v1/reports/history")
                        .param("eventType", "SALE_RESOLVE")
                        .param("cashierId", "CASH-CSV")
                        .param("success", "true")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.events[0].eventType").value("SALE_RESOLVE"))
                .andExpect(jsonPath("$.events[0].cashierId").value("CASH-CSV"));

        mockMvc.perform(get("/api/v1/reports/history.csv")
                        .param("eventType", "SALE_RESOLVE")
                        .param("cashierId", "CASH-CSV")
                        .param("success", "true")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("sbg-marking-history.csv")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("SALE_RESOLVE")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("CASH-CSV")));
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

    @Test
    void excelImportEndpointShouldImportAndBeUsedForResolve() throws Exception {
        byte[] excel = createExcelFile(new String[][]{
                {"markCode", "item", "gtin", "productType", "valid", "blocked", "status", "fifoTsEpochMs"},
                {"KM-XL-1", "ITEM-XL", "GTIN-XL", "TOBACCO", "true", "false", "AVAILABLE", "1000"},
                {"KM-XL-2", "ITEM-XL", "GTIN-XL", "TOBACCO", "1", "0", "AVAILABLE", "2000"}
        });

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "km-import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excel
        );

        mockMvc.perform(multipart("/api/v1/km/import/full/excel")
                        .file(file)
                        .param("batchId", "excel-batch-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchId").value("excel-batch-1"))
                .andExpect(jsonPath("$.added").value(2));

        ResolveAndReserveRequest resolveRequest = new ResolveAndReserveRequest();
        resolveRequest.setOperationId("api-op-sale-xl-1");
        resolveRequest.setShopId("1");
        resolveRequest.setPosId("1");
        resolveRequest.setCashierId("cashier-xl");
        resolveRequest.setScannedMark("NOT-IN-POOL");
        resolveRequest.setQuantity(1);

        ProductRef product = new ProductRef();
        product.setProductType("TOBACCO");
        product.setItem("ITEM-XL");
        product.setGtin("GTIN-XL");
        resolveRequest.setProduct(product);

        mockMvc.perform(post("/api/v1/marking/resolve-and-reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resolveRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("ACCEPT_AUTO_SELECTED"))
                .andExpect(jsonPath("$.appliedMark").value("KM-XL-1"));
    }

    @Test
    void excelImportEndpointShouldReturnBadRequestForInvalidFile() throws Exception {
        MockMultipartFile invalidFile = new MockMultipartFile(
                "file",
                "invalid.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "not-an-excel".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/km/import/full/excel")
                        .file(invalidFile))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminMarkEndpointsShouldCreateUpdateListDelete() throws Exception {
        MarkAdminUpsertRequest create = new MarkAdminUpsertRequest();
        create.setMarkCode("KM-ADM-1");
        create.setItem("ITEM-ADM");
        create.setGtin("GTIN-ADM");
        create.setProductType("TOBACCO");
        create.setValid(true);
        create.setBlocked(false);
        create.setStatus(MarkStatus.AVAILABLE);
        create.setFifoTsEpochMs(1000L);

        mockMvc.perform(post("/api/v1/admin/marks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/v1/admin/marks")
                        .param("markCodeLike", "KM-ADM")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].markCode").value("KM-ADM-1"));

        MarkAdminUpsertRequest update = new MarkAdminUpsertRequest();
        update.setBlocked(true);
        mockMvc.perform(put("/api/v1/admin/marks/KM-ADM-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        MarkAdminUpsertRequest unlock = new MarkAdminUpsertRequest();
        unlock.setBlocked(false);
        mockMvc.perform(put("/api/v1/admin/marks/KM-ADM-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unlock)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(delete("/api/v1/admin/marks/KM-ADM-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void validationEndpointsShouldCheckAndUpdatePolicy() throws Exception {
        ImportRequest importRequest = new ImportRequest();
        importRequest.setBatchId("api-batch-val-1");
        importRequest.setItems(List.of(mark("KM-VAL-1", 1000L)));
        importRequest.getItems().get(0).setValid(false);

        mockMvc.perform(post("/api/v1/km/import/full")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(importRequest)))
                .andExpect(status().isOk());

        ValidationRequest validationRequest = new ValidationRequest();
        validationRequest.setOperationType(ValidationOperationType.SALE);
        validationRequest.setScannedMark("KM-VAL-1");
        ProductRef product = new ProductRef();
        product.setProductType("TOBACCO");
        product.setItem("ITEM-1");
        product.setGtin("GTIN-1");
        validationRequest.setProduct(product);

        mockMvc.perform(post("/api/v1/validation/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validationRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_FLAG"));

        ValidationPolicy policy = new ValidationPolicy();
        policy.setRejectUnknownMark(true);
        policy.setRequireProductMatch(true);
        policy.setRejectBlocked(true);
        policy.setRejectInvalidFlag(false);
        policy.setSaleAllowedStatuses(List.of(MarkStatus.AVAILABLE));
        policy.setReturnAllowedStatuses(List.of(MarkStatus.SOLD));

        mockMvc.perform(put("/api/v1/validation/policy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(policy)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rejectInvalidFlag").value(false));

        mockMvc.perform(post("/api/v1/validation/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validationRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"));
    }

    @Test
    void adminAuditEndpointShouldReturnAdminEvents() throws Exception {
        MarkAdminUpsertRequest create = new MarkAdminUpsertRequest();
        create.setMarkCode("KM-AUD-1");
        create.setItem("ITEM-AUD");
        create.setGtin("GTIN-AUD");
        create.setProductType("TOBACCO");
        create.setValid(true);
        create.setBlocked(false);
        create.setStatus(MarkStatus.AVAILABLE);
        create.setFifoTsEpochMs(1000L);

        mockMvc.perform(post("/api/v1/admin/marks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/v1/admin/audit")
                        .param("action", "ADMIN_MARK_UPSERT")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.events[0].action").value("ADMIN_MARK_UPSERT"));
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

    private byte[] createExcelFile(String[][] rows) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("km");
            for (int rowIndex = 0; rowIndex < rows.length; rowIndex++) {
                Row row = sheet.createRow(rowIndex);
                String[] values = rows[rowIndex];
                for (int colIndex = 0; colIndex < values.length; colIndex++) {
                    row.createCell(colIndex).setCellValue(values[colIndex]);
                }
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
