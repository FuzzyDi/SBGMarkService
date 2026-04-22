package uz.sbg.kmreplacement.http.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ReturnResolveAndReserveResponse {
    private boolean success;
    private String reservationId;
    private String appliedMark;
    private ErrorCode errorCode = ErrorCode.NONE;
    private String message;

    public boolean   isSuccess()            { return success; }
    public void      setSuccess(boolean v)  { this.success = v; }
    public String    getReservationId()     { return reservationId; }
    public void      setReservationId(String v) { this.reservationId = v; }
    public String    getAppliedMark()       { return appliedMark; }
    public void      setAppliedMark(String v)   { this.appliedMark = v; }
    public ErrorCode getErrorCode()         { return errorCode; }
    public void      setErrorCode(ErrorCode v)  { this.errorCode = v; }
    public String    getMessage()           { return message; }
    public void      setMessage(String v)       { this.message = v; }
}
