(function () {
    const defaultJson = {
        batchId: "ui-seed-batch",
        items: [
            {
                markCode: "KM-1001",
                item: "ITEM-1",
                gtin: "GTIN-1",
                productType: "TOBACCO",
                valid: true,
                blocked: false,
                status: "AVAILABLE",
                fifoTsEpochMs: 1000
            },
            {
                markCode: "KM-1002",
                item: "ITEM-1",
                gtin: "GTIN-1",
                productType: "TOBACCO",
                valid: true,
                blocked: false,
                status: "AVAILABLE",
                fifoTsEpochMs: 2000
            }
        ]
    };

    const els = {
        baseUrl: byId("baseUrl"),
        healthBadge: byId("healthBadge"),
        btnHealth: byId("btnHealth"),
        btnSummary: byId("btnSummary"),
        output: byId("responseOutput"),
        notes: byId("sessionNotes"),
        jsonPayload: byId("jsonPayload"),
        excelImportForm: byId("excelImportForm"),
        jsonImportForm: byId("jsonImportForm"),
        saleResolveForm: byId("saleResolveForm"),
        returnResolveForm: byId("returnResolveForm"),
        operationForm: byId("operationForm"),
        fifoForm: byId("fifoForm"),
        historyForm: byId("historyForm"),
        btnHistoryCsv: byId("btnHistoryCsv")
    };

    const session = {
        lastSaleReservationId: "",
        lastSaleMarkCode: "",
        lastReturnReservationId: "",
        lastReturnMarkCode: ""
    };

    init();

    function init() {
        els.baseUrl.value = window.location.origin;
        els.jsonPayload.value = JSON.stringify(defaultJson, null, 2);
        seedOperationIds();
        bindEvents();
        pingHealth();
    }

    function bindEvents() {
        els.btnHealth.addEventListener("click", pingHealth);
        els.btnSummary.addEventListener("click", loadSummary);

        els.excelImportForm.addEventListener("submit", onExcelImport);
        els.jsonImportForm.addEventListener("submit", onJsonImport);
        els.saleResolveForm.addEventListener("submit", onSaleResolve);
        els.returnResolveForm.addEventListener("submit", onReturnResolve);
        els.operationForm.addEventListener("submit", onOperation);
        els.fifoForm.addEventListener("submit", onFifo);
        els.historyForm.addEventListener("submit", onHistory);
        els.btnHistoryCsv.addEventListener("click", onDownloadHistoryCsv);
    }

    function byId(id) {
        return document.getElementById(id);
    }

    function safeBaseUrl() {
        const raw = (els.baseUrl.value || "").trim();
        return raw || window.location.origin;
    }

    async function apiCall(path, options) {
        const url = safeBaseUrl() + path;
        const response = await fetch(url, options || {});
        const contentType = response.headers.get("content-type") || "";

        let body;
        if (contentType.includes("application/json")) {
            body = await response.json();
        } else {
            body = await response.text();
        }

        if (!response.ok) {
            const errorPayload = {
                status: response.status,
                path: path,
                body: body
            };
            renderOutput(errorPayload);
            addNote("Request failed: " + path + " (" + response.status + ")");
            throw new Error("HTTP " + response.status);
        }

        renderOutput(body);
        addNote("Request OK: " + path);
        return body;
    }

    function renderOutput(payload) {
        if (typeof payload === "string") {
            els.output.textContent = payload;
            return;
        }
        try {
            els.output.textContent = JSON.stringify(payload, null, 2);
        } catch (e) {
            els.output.textContent = String(payload);
        }
    }

    function addNote(text) {
        const item = document.createElement("li");
        item.textContent = new Date().toLocaleTimeString() + " - " + text;
        els.notes.prepend(item);
        while (els.notes.children.length > 12) {
            els.notes.removeChild(els.notes.lastChild);
        }
    }

    function setHealth(status, isUp) {
        els.healthBadge.textContent = status;
        els.healthBadge.classList.remove("status-neutral", "status-up", "status-down");
        els.healthBadge.classList.add(isUp ? "status-up" : "status-down");
    }

    function asInt(value) {
        if (value === null || value === undefined) {
            return null;
        }
        const trimmed = String(value).trim();
        if (!trimmed) {
            return null;
        }
        const parsed = Number(trimmed);
        return Number.isFinite(parsed) ? Math.trunc(parsed) : null;
    }

    function asLong(value) {
        return asInt(value);
    }

    function nonEmpty(value) {
        const trimmed = (value || "").trim();
        return trimmed ? trimmed : null;
    }

    function serializeQuery(params) {
        const query = new URLSearchParams();
        Object.keys(params).forEach(function (key) {
            const value = params[key];
            if (value === null || value === undefined || value === "") {
                return;
            }
            query.set(key, String(value));
        });
        const raw = query.toString();
        return raw ? ("?" + raw) : "";
    }

    function operationId(prefix) {
        return prefix + "-" + Date.now();
    }

    function seedOperationIds() {
        byId("saleOperationId").value = operationId("sale-resolve");
        byId("returnOperationId").value = operationId("return-resolve");
        byId("operationId").value = operationId("operation");
    }

    async function pingHealth() {
        try {
            const body = await apiCall("/actuator/health");
            setHealth(body && body.status ? body.status : "UP", true);
        } catch (e) {
            setHealth("DOWN", false);
        }
    }

    async function loadSummary() {
        await apiCall("/api/v1/reports/summary");
    }

    async function onExcelImport(event) {
        event.preventDefault();
        const mode = byId("excelMode").value;
        const batchId = nonEmpty(byId("excelBatchId").value);
        const fileInput = byId("excelFile");
        const file = fileInput.files && fileInput.files[0];

        if (!file) {
            renderOutput({ error: "Please select an Excel file." });
            return;
        }

        const endpoint = mode === "delta" ? "/api/v1/km/import/delta/excel" : "/api/v1/km/import/full/excel";
        const query = serializeQuery({ batchId: batchId });
        const formData = new FormData();
        formData.append("file", file);

        await apiCall(endpoint + query, {
            method: "POST",
            body: formData
        });
    }

    async function onJsonImport(event) {
        event.preventDefault();
        const mode = byId("jsonMode").value;
        const endpoint = mode === "delta" ? "/api/v1/km/import/delta" : "/api/v1/km/import/full";
        let payload;

        try {
            payload = JSON.parse(byId("jsonPayload").value);
        } catch (e) {
            renderOutput({ error: "Invalid JSON payload." });
            return;
        }

        await apiCall(endpoint, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });
    }

    async function onSaleResolve(event) {
        event.preventDefault();
        const payload = {
            operationId: nonEmpty(byId("saleOperationId").value) || operationId("sale-resolve"),
            shopId: nonEmpty(byId("saleShopId").value),
            posId: nonEmpty(byId("salePosId").value),
            cashierId: nonEmpty(byId("saleCashierId").value),
            product: {
                barcode: nonEmpty(byId("saleBarcode").value),
                item: nonEmpty(byId("saleItem").value),
                productType: nonEmpty(byId("saleProductType").value),
                gtin: nonEmpty(byId("saleGtin").value)
            },
            scannedMark: nonEmpty(byId("saleScannedMark").value),
            quantity: asInt(byId("saleQuantity").value) || 1
        };

        const result = await apiCall("/api/v1/marking/resolve-and-reserve", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });

        if (result && result.reservationId) {
            session.lastSaleReservationId = result.reservationId;
            byId("reservationId").value = result.reservationId;
        }
        if (result && result.appliedMark) {
            session.lastSaleMarkCode = result.appliedMark;
            byId("operationMarkCode").value = result.appliedMark;
            byId("returnScannedMark").value = result.appliedMark;
        }
        byId("saleOperationId").value = operationId("sale-resolve");
    }

    async function onReturnResolve(event) {
        event.preventDefault();
        const payload = {
            operationId: nonEmpty(byId("returnOperationId").value) || operationId("return-resolve"),
            shopId: nonEmpty(byId("returnShopId").value),
            posId: nonEmpty(byId("returnPosId").value),
            cashierId: nonEmpty(byId("returnCashierId").value),
            product: {
                item: nonEmpty(byId("returnItem").value),
                productType: nonEmpty(byId("returnProductType").value),
                gtin: nonEmpty(byId("returnGtin").value)
            },
            scannedMark: nonEmpty(byId("returnScannedMark").value) || session.lastSaleMarkCode,
            saleReceiptId: nonEmpty(byId("saleReceiptId").value)
        };

        const result = await apiCall("/api/v1/marking/return-resolve-and-reserve", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });

        if (result && result.reservationId) {
            session.lastReturnReservationId = result.reservationId;
            byId("reservationId").value = result.reservationId;
        }
        if (result && result.appliedMark) {
            session.lastReturnMarkCode = result.appliedMark;
            byId("operationMarkCode").value = result.appliedMark;
        }
        byId("returnOperationId").value = operationId("return-resolve");
    }

    async function onOperation(event) {
        event.preventDefault();
        const endpoint = nonEmpty(byId("operationEndpoint").value) || "sold-confirm";
        const payload = {
            operationId: nonEmpty(byId("operationId").value) || operationId("operation"),
            reservationId: nonEmpty(byId("reservationId").value) || session.lastSaleReservationId || session.lastReturnReservationId,
            markCode: nonEmpty(byId("operationMarkCode").value) || session.lastSaleMarkCode || session.lastReturnMarkCode,
            receiptId: nonEmpty(byId("receiptId").value),
            receiptNumber: asInt(byId("receiptNumber").value),
            shiftNumber: asInt(byId("shiftNumber").value),
            fiscalDocId: asLong(byId("fiscalDocId").value),
            fiscalSign: asLong(byId("fiscalSign").value),
            reason: nonEmpty(byId("operationReason").value)
        };

        await apiCall("/api/v1/marking/" + endpoint, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });

        byId("operationId").value = operationId("operation");
    }

    async function onFifo(event) {
        event.preventDefault();
        const query = serializeQuery({
            productType: nonEmpty(byId("fifoProductType").value),
            item: nonEmpty(byId("fifoItem").value),
            gtin: nonEmpty(byId("fifoGtin").value),
            limit: asInt(byId("fifoLimit").value)
        });
        await apiCall("/api/v1/km/debug/fifo-by-product" + query);
    }

    async function onHistory(event) {
        event.preventDefault();
        const query = buildHistoryQuery();
        await apiCall("/api/v1/reports/history" + query);
    }

    function onDownloadHistoryCsv() {
        const query = buildHistoryQuery();
        const url = safeBaseUrl() + "/api/v1/reports/history.csv" + query;
        window.open(url, "_blank");
        addNote("CSV download requested.");
    }

    function buildHistoryQuery() {
        return serializeQuery({
            limit: asInt(byId("historyLimit").value),
            markCode: nonEmpty(byId("historyMarkCode").value),
            eventType: nonEmpty(byId("historyEventType").value),
            shopId: nonEmpty(byId("historyShopId").value),
            posId: nonEmpty(byId("historyPosId").value),
            cashierId: nonEmpty(byId("historyCashierId").value),
            success: nonEmpty(byId("historySuccess").value),
            from: nonEmpty(byId("historyFrom").value),
            to: nonEmpty(byId("historyTo").value)
        });
    }
})();
