package uz.sbg.marking.server.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;
import uz.sbg.marking.contracts.AdminAuditQueryResponse;
import uz.sbg.marking.contracts.FifoByProductResponse;
import uz.sbg.marking.contracts.HistoryQueryResponse;
import uz.sbg.marking.contracts.ImportRequest;
import uz.sbg.marking.contracts.ImportResponse;
import uz.sbg.marking.contracts.MarkAdminUpsertRequest;
import uz.sbg.marking.contracts.MarkOperationRequest;
import uz.sbg.marking.contracts.MarkStatus;
import uz.sbg.marking.contracts.OperationResponse;
import uz.sbg.marking.contracts.ReportSummaryResponse;
import uz.sbg.marking.contracts.ResolveAndReserveRequest;
import uz.sbg.marking.contracts.ResolveAndReserveResponse;
import uz.sbg.marking.contracts.ReturnResolveAndReserveRequest;
import uz.sbg.marking.contracts.ReturnResolveAndReserveResponse;
import uz.sbg.marking.contracts.ValidationPolicy;
import uz.sbg.marking.contracts.ValidationRequest;
import uz.sbg.marking.contracts.ValidationResponse;
import uz.sbg.marking.server.model.MarkRecord;
import uz.sbg.marking.server.service.AdminAuditService;
import uz.sbg.marking.server.service.MarkingExcelImportService;
import uz.sbg.marking.server.service.MarkingService;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class MarkingController {
    private final MarkingService markingService;
    private final MarkingExcelImportService excelImportService;
    private final AdminAuditService adminAuditService;

    public MarkingController(MarkingService markingService,
                             MarkingExcelImportService excelImportService,
                             AdminAuditService adminAuditService) {
        this.markingService = markingService;
        this.excelImportService = excelImportService;
        this.adminAuditService = adminAuditService;
    }

    @PostMapping("/marking/resolve-and-reserve")
    public ResponseEntity<ResolveAndReserveResponse> resolveAndReserve(@RequestBody ResolveAndReserveRequest request) {
        return ResponseEntity.ok(markingService.resolveAndReserve(request));
    }

    @PostMapping("/marking/return-resolve-and-reserve")
    public ResponseEntity<ReturnResolveAndReserveResponse> returnResolveAndReserve(@RequestBody ReturnResolveAndReserveRequest request) {
        return ResponseEntity.ok(markingService.returnResolveAndReserve(request));
    }

    @PostMapping("/marking/sold-confirm")
    public ResponseEntity<OperationResponse> soldConfirm(@RequestBody MarkOperationRequest request) {
        return ResponseEntity.ok(markingService.soldConfirm(request));
    }

    @PostMapping("/marking/sale-release")
    public ResponseEntity<OperationResponse> saleRelease(@RequestBody MarkOperationRequest request) {
        return ResponseEntity.ok(markingService.saleRelease(request));
    }

    @PostMapping("/marking/return-confirm")
    public ResponseEntity<OperationResponse> returnConfirm(@RequestBody MarkOperationRequest request) {
        return ResponseEntity.ok(markingService.returnConfirm(request));
    }

    @PostMapping("/marking/return-release")
    public ResponseEntity<OperationResponse> returnRelease(@RequestBody MarkOperationRequest request) {
        return ResponseEntity.ok(markingService.returnRelease(request));
    }

    @PostMapping("/km/import/full")
    public ResponseEntity<ImportResponse> importFull(@RequestBody ImportRequest request,
                                                     HttpServletRequest httpRequest) {
        ImportResponse response = markingService.importFull(request);
        adminAuditService.record(httpRequest, "ADMIN_IMPORT_FULL", null, true,
                "Import full completed: added=" + response.getAdded() + ", updated=" + response.getUpdated(),
                request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/km/import/delta")
    public ResponseEntity<ImportResponse> importDelta(@RequestBody ImportRequest request,
                                                      HttpServletRequest httpRequest) {
        ImportResponse response = markingService.importDelta(request);
        adminAuditService.record(httpRequest, "ADMIN_IMPORT_DELTA", null, true,
                "Import delta completed: added=" + response.getAdded() + ", updated=" + response.getUpdated(),
                request);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/km/import/full/excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportResponse> importFullExcel(@RequestParam("file") MultipartFile file,
                                                          @RequestParam(name = "batchId", required = false) String batchId,
                                                          HttpServletRequest httpRequest) {
        ImportRequest request = parseExcelRequest(file, batchId);
        ImportResponse response = markingService.importFull(request);
        adminAuditService.record(httpRequest, "ADMIN_IMPORT_FULL_EXCEL", null, true,
                "Import full Excel completed: added=" + response.getAdded() + ", updated=" + response.getUpdated(),
                request);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/km/import/delta/excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportResponse> importDeltaExcel(@RequestParam("file") MultipartFile file,
                                                           @RequestParam(name = "batchId", required = false) String batchId,
                                                           HttpServletRequest httpRequest) {
        ImportRequest request = parseExcelRequest(file, batchId);
        ImportResponse response = markingService.importDelta(request);
        adminAuditService.record(httpRequest, "ADMIN_IMPORT_DELTA_EXCEL", null, true,
                "Import delta Excel completed: added=" + response.getAdded() + ", updated=" + response.getUpdated(),
                request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/km/debug/marks")
    public ResponseEntity<List<MarkRecord>> debugMarks() {
        return ResponseEntity.ok(markingService.snapshotMarks());
    }

    @GetMapping("/admin/marks")
    public ResponseEntity<List<MarkRecord>> adminMarks(@RequestParam(name = "productType", required = false) String productType,
                                                       @RequestParam(name = "item", required = false) String item,
                                                       @RequestParam(name = "gtin", required = false) String gtin,
                                                       @RequestParam(name = "status", required = false) MarkStatus status,
                                                       @RequestParam(name = "valid", required = false) Boolean valid,
                                                       @RequestParam(name = "blocked", required = false) Boolean blocked,
                                                       @RequestParam(name = "limit", required = false) Integer limit,
                                                       @RequestParam(name = "markCodeLike", required = false) String markCodeLike) {
        return ResponseEntity.ok(markingService.adminQueryMarks(productType, item, gtin, status, valid, blocked, limit, markCodeLike));
    }

    @PostMapping("/admin/marks")
    public ResponseEntity<OperationResponse> adminCreateMark(@RequestBody MarkAdminUpsertRequest request,
                                                             HttpServletRequest httpRequest) {
        OperationResponse response = markingService.adminUpsertMark(request, null);
        adminAuditService.record(httpRequest, "ADMIN_MARK_UPSERT",
                request == null ? null : request.getMarkCode(),
                response.isSuccess(), response.getMessage(), request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/admin/marks/{markCode}")
    public ResponseEntity<OperationResponse> adminUpdateMark(@PathVariable("markCode") String markCode,
                                                             @RequestBody MarkAdminUpsertRequest request,
                                                             HttpServletRequest httpRequest) {
        OperationResponse response = markingService.adminUpsertMark(request, markCode);
        adminAuditService.record(httpRequest, "ADMIN_MARK_UPSERT",
                markCode,
                response.isSuccess(), response.getMessage(), request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/admin/marks/{markCode}")
    public ResponseEntity<OperationResponse> adminDeleteMark(@PathVariable("markCode") String markCode,
                                                             HttpServletRequest httpRequest) {
        OperationResponse response = markingService.adminDeleteMark(markCode);
        adminAuditService.record(httpRequest, "ADMIN_MARK_DELETE",
                markCode,
                response.isSuccess(), response.getMessage(), null);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/admin/audit")
    public ResponseEntity<AdminAuditQueryResponse> adminAudit(@RequestParam(name = "limit", required = false) Integer limit,
                                                              @RequestParam(name = "action", required = false) String action,
                                                              @RequestParam(name = "success", required = false) Boolean success,
                                                              @RequestParam(name = "targetMarkCode", required = false) String targetMarkCode,
                                                              @RequestParam(name = "actorUser", required = false) String actorUser) {
        return ResponseEntity.ok(adminAuditService.query(limit, action, success, targetMarkCode, actorUser));
    }

    @PostMapping("/validation/check")
    public ResponseEntity<ValidationResponse> validationCheck(@RequestBody ValidationRequest request) {
        return ResponseEntity.ok(markingService.validateMark(request));
    }

    @GetMapping("/validation/policy")
    public ResponseEntity<ValidationPolicy> validationPolicy() {
        return ResponseEntity.ok(markingService.getValidationPolicy());
    }

    @PutMapping("/validation/policy")
    public ResponseEntity<ValidationPolicy> updateValidationPolicy(@RequestBody ValidationPolicy request,
                                                                   HttpServletRequest httpRequest) {
        ValidationPolicy response = markingService.updateValidationPolicy(request);
        adminAuditService.record(httpRequest, "VALIDATION_POLICY_UPDATE",
                null,
                true, "Validation policy updated.", request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/km/debug/fifo-by-product")
    public ResponseEntity<FifoByProductResponse> debugFifoByProduct(@RequestParam(name = "item", required = false) String item,
                                                                    @RequestParam(name = "gtin", required = false) String gtin,
                                                                    @RequestParam(name = "productType", required = false) String productType,
                                                                    @RequestParam(name = "limit", required = false) Integer limit) {
        return ResponseEntity.ok(markingService.debugFifoByProduct(item, gtin, productType, limit));
    }

    @GetMapping("/reports/summary")
    public ResponseEntity<ReportSummaryResponse> summary() {
        return ResponseEntity.ok(markingService.summary());
    }

    @GetMapping("/reports/history")
    public ResponseEntity<HistoryQueryResponse> history(@RequestParam(name = "markCode", required = false) String markCode,
                                                        @RequestParam(name = "from", required = false) Instant from,
                                                        @RequestParam(name = "to", required = false) Instant to,
                                                        @RequestParam(name = "limit", required = false) Integer limit,
                                                        @RequestParam(name = "eventType", required = false) String eventType,
                                                        @RequestParam(name = "shopId", required = false) String shopId,
                                                        @RequestParam(name = "posId", required = false) String posId,
                                                        @RequestParam(name = "cashierId", required = false) String cashierId,
                                                        @RequestParam(name = "success", required = false) Boolean success) {
        return ResponseEntity.ok(markingService.queryHistory(markCode, from, to, limit, eventType, shopId, posId, cashierId, success));
    }

    @GetMapping(value = "/reports/history.csv", produces = "text/csv")
    public ResponseEntity<String> historyCsv(@RequestParam(name = "markCode", required = false) String markCode,
                                             @RequestParam(name = "from", required = false) Instant from,
                                             @RequestParam(name = "to", required = false) Instant to,
                                             @RequestParam(name = "limit", required = false) Integer limit,
                                             @RequestParam(name = "eventType", required = false) String eventType,
                                             @RequestParam(name = "shopId", required = false) String shopId,
                                             @RequestParam(name = "posId", required = false) String posId,
                                             @RequestParam(name = "cashierId", required = false) String cashierId,
                                             @RequestParam(name = "success", required = false) Boolean success) {
        String csv = markingService.exportHistoryCsv(markCode, from, to, limit, eventType, shopId, posId, cashierId, success);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"sbg-marking-history.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    private ImportRequest parseExcelRequest(MultipartFile file, String batchId) {
        try {
            return excelImportService.parseImportRequest(file, batchId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }
}
