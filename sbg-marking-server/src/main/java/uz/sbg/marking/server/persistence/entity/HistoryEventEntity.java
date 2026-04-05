package uz.sbg.marking.server.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "history_events")
public class HistoryEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_ts", nullable = false)
    private Instant eventTs;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "operation_id", length = 128)
    private String operationId;

    @Column(name = "mark_code", length = 256)
    private String markCode;

    @Column(name = "reservation_id", length = 64)
    private String reservationId;

    @Column(name = "product_type", length = 64)
    private String productType;

    @Column(name = "item", length = 128)
    private String item;

    @Column(name = "gtin", length = 64)
    private String gtin;

    @Column(name = "shop_id", length = 32)
    private String shopId;

    @Column(name = "pos_id", length = 32)
    private String posId;

    @Column(name = "cashier_id", length = 128)
    private String cashierId;

    @Column(name = "receipt_id", length = 128)
    private String receiptId;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Column(name = "error_code", nullable = false, length = 64)
    private String errorCode;

    @Column(name = "message")
    private String message;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Instant getEventTs() {
        return eventTs;
    }

    public void setEventTs(Instant eventTs) {
        this.eventTs = eventTs;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public String getMarkCode() {
        return markCode;
    }

    public void setMarkCode(String markCode) {
        this.markCode = markCode;
    }

    public String getReservationId() {
        return reservationId;
    }

    public void setReservationId(String reservationId) {
        this.reservationId = reservationId;
    }

    public String getProductType() {
        return productType;
    }

    public void setProductType(String productType) {
        this.productType = productType;
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

    public String getShopId() {
        return shopId;
    }

    public void setShopId(String shopId) {
        this.shopId = shopId;
    }

    public String getPosId() {
        return posId;
    }

    public void setPosId(String posId) {
        this.posId = posId;
    }

    public String getCashierId() {
        return cashierId;
    }

    public void setCashierId(String cashierId) {
        this.cashierId = cashierId;
    }

    public String getReceiptId() {
        return receiptId;
    }

    public void setReceiptId(String receiptId) {
        this.receiptId = receiptId;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
