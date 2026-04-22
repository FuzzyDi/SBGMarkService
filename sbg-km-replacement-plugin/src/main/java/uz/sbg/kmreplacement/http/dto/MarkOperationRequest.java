package uz.sbg.kmreplacement.http.dto;

/**
 * Унифицированный payload для fiscalize/cancel-операций:
 * {@code /sold-confirm}, {@code /sale-release},
 * {@code /return-confirm}, {@code /return-release}.
 */
public class MarkOperationRequest {
    private String  operationId;
    private String  reservationId;
    private String  markCode;
    private String  receiptId;
    private Integer receiptNumber;
    private Integer shiftNumber;
    private Long    fiscalDocId;
    private Long    fiscalSign;
    private String  reason;

    public String  getOperationId()    { return operationId; }
    public void    setOperationId(String v)    { this.operationId = v; }
    public String  getReservationId()  { return reservationId; }
    public void    setReservationId(String v)  { this.reservationId = v; }
    public String  getMarkCode()       { return markCode; }
    public void    setMarkCode(String v)       { this.markCode = v; }
    public String  getReceiptId()      { return receiptId; }
    public void    setReceiptId(String v)      { this.receiptId = v; }
    public Integer getReceiptNumber()  { return receiptNumber; }
    public void    setReceiptNumber(Integer v) { this.receiptNumber = v; }
    public Integer getShiftNumber()    { return shiftNumber; }
    public void    setShiftNumber(Integer v)   { this.shiftNumber = v; }
    public Long    getFiscalDocId()    { return fiscalDocId; }
    public void    setFiscalDocId(Long v)      { this.fiscalDocId = v; }
    public Long    getFiscalSign()     { return fiscalSign; }
    public void    setFiscalSign(Long v)       { this.fiscalSign = v; }
    public String  getReason()         { return reason; }
    public void    setReason(String v)         { this.reason = v; }
}
