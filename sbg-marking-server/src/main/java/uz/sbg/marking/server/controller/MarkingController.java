package uz.sbg.marking.server.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.sbg.marking.contracts.FifoByProductResponse;
import uz.sbg.marking.contracts.HistoryQueryResponse;
import uz.sbg.marking.contracts.ImportRequest;
import uz.sbg.marking.contracts.ImportResponse;
import uz.sbg.marking.contracts.MarkOperationRequest;
import uz.sbg.marking.contracts.OperationResponse;
import uz.sbg.marking.contracts.ReportSummaryResponse;
import uz.sbg.marking.contracts.ResolveAndReserveRequest;
import uz.sbg.marking.contracts.ResolveAndReserveResponse;
import uz.sbg.marking.contracts.ReturnResolveAndReserveRequest;
import uz.sbg.marking.contracts.ReturnResolveAndReserveResponse;
import uz.sbg.marking.server.model.MarkRecord;
import uz.sbg.marking.server.service.MarkingService;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class MarkingController {
    private final MarkingService markingService;

    public MarkingController(MarkingService markingService) {
        this.markingService = markingService;
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
    public ResponseEntity<ImportResponse> importFull(@RequestBody ImportRequest request) {
        return ResponseEntity.ok(markingService.importFull(request));
    }

    @PostMapping("/km/import/delta")
    public ResponseEntity<ImportResponse> importDelta(@RequestBody ImportRequest request) {
        return ResponseEntity.ok(markingService.importDelta(request));
    }

    @GetMapping("/km/debug/marks")
    public ResponseEntity<List<MarkRecord>> debugMarks() {
        return ResponseEntity.ok(markingService.snapshotMarks());
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
                                                        @RequestParam(name = "limit", required = false) Integer limit) {
        return ResponseEntity.ok(markingService.queryHistory(markCode, from, to, limit));
    }
}
