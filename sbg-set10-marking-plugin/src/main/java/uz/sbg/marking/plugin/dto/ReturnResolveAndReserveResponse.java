package uz.sbg.marking.plugin.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ReturnResolveAndReserveResponse {
    private boolean success;
    private String reservationId;
    private String appliedMark;
    private ErrorCode errorCode = ErrorCode.NONE;
    private String message;

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getReservationId() { return reservationId; }
    public void setReservationId(String reservationId) { this.reservationId = reservationId; }

    public String getAppliedMark() { return appliedMark; }
    public void setAppliedMark(String appliedMark) { this.appliedMark = appliedMark; }

    public ErrorCode getErrorCode() { return errorCode; }
    public void setErrorCode(ErrorCode errorCode) { this.errorCode = errorCode; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
