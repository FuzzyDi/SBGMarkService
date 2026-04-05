package uz.sbg.marking.server.model;

import uz.sbg.marking.contracts.MarkStatus;

import java.time.Instant;

public class MarkRecord {
    private String markCode;
    private String item;
    private String gtin;
    private String productType;
    private boolean valid;
    private boolean blocked;
    private MarkStatus status;
    private Instant fifoTs;
    private String activeReservationId;
    private String lastSaleReceiptId;
    private String lastReturnReceiptId;

    public String getMarkCode() {
        return markCode;
    }

    public void setMarkCode(String markCode) {
        this.markCode = markCode;
    }

    public String getItem() {
        return item;
    }

    public void setItem(String item) {
        this.item = item;
    }

    public String getGtin() {
        return gtin;
    }

    public void setGtin(String gtin) {
        this.gtin = gtin;
    }

    public String getProductType() {
        return productType;
    }

    public void setProductType(String productType) {
        this.productType = productType;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    public MarkStatus getStatus() {
        return status;
    }

    public void setStatus(MarkStatus status) {
        this.status = status;
    }

    public Instant getFifoTs() {
        return fifoTs;
    }

    public void setFifoTs(Instant fifoTs) {
        this.fifoTs = fifoTs;
    }

    public String getActiveReservationId() {
        return activeReservationId;
    }

    public void setActiveReservationId(String activeReservationId) {
        this.activeReservationId = activeReservationId;
    }

    public String getLastSaleReceiptId() {
        return lastSaleReceiptId;
    }

    public void setLastSaleReceiptId(String lastSaleReceiptId) {
        this.lastSaleReceiptId = lastSaleReceiptId;
    }

    public String getLastReturnReceiptId() {
        return lastReturnReceiptId;
    }

    public void setLastReturnReceiptId(String lastReturnReceiptId) {
        this.lastReturnReceiptId = lastReturnReceiptId;
    }
}
