package uz.sbg.marking.contracts;

public class MarkOperationRequest {
    private String operationId;
    private String reservationId;
    private String markCode;
    private String receiptId;
    private Integer receiptNumber;
    private Integer shiftNumber;
    private Long fiscalDocId;
    private Long fiscalSign;
    private String reason;

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public String getReservationId() {
        return reservationId;
    }

    public void setReservationId(String reservationId) {
        this.reservationId = reservationId;
    }

    public String getMarkCode() {
        return markCode;
    }

    public void setMarkCode(String markCode) {
        this.markCode = markCode;
    }

    public String getReceiptId() {
        return receiptId;
    }

    public void setReceiptId(String receiptId) {
        this.receiptId = receiptId;
    }

    public Integer getReceiptNumber() {
        return receiptNumber;
    }

    public void setReceiptNumber(Integer receiptNumber) {
        this.receiptNumber = receiptNumber;
    }

    public Integer getShiftNumber() {
        return shiftNumber;
    }

    public void setShiftNumber(Integer shiftNumber) {
        this.shiftNumber = shiftNumber;
    }

    public Long getFiscalDocId() {
        return fiscalDocId;
    }

    public void setFiscalDocId(Long fiscalDocId) {
        this.fiscalDocId = fiscalDocId;
    }

    public Long getFiscalSign() {
        return fiscalSign;
    }

    public void setFiscalSign(Long fiscalSign) {
        this.fiscalSign = fiscalSign;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
