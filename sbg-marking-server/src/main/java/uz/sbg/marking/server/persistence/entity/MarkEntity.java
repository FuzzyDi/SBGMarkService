package uz.sbg.marking.server.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "marks")
public class MarkEntity {
    @Id
    @Column(name = "mark_code", nullable = false, length = 256)
    private String markCode;

    @Column(name = "item", length = 128)
    private String item;

    @Column(name = "gtin", length = 64)
    private String gtin;

    @Column(name = "product_type", nullable = false, length = 64)
    private String productType;

    @Column(name = "valid", nullable = false)
    private boolean valid;

    @Column(name = "blocked", nullable = false)
    private boolean blocked;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "fifo_ts", nullable = false)
    private Instant fifoTs;

    @Column(name = "active_reservation_id", length = 64)
    private String activeReservationId;

    @Column(name = "last_sale_receipt_id", length = 128)
    private String lastSaleReceiptId;

    @Column(name = "last_return_receipt_id", length = 128)
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
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
