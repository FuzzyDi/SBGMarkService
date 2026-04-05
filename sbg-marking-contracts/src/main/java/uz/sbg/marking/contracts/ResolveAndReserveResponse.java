package uz.sbg.marking.contracts;

public class ResolveAndReserveResponse {
    private ResolveResult result;
    private String appliedMark;
    private MarkSource source;
    private String reservationId;
    private long ttlSec;
    private ErrorCode errorCode = ErrorCode.NONE;
    private String message;

    public ResolveResult getResult() {
        return result;
    }

    public void setResult(ResolveResult result) {
        this.result = result;
    }

    public String getAppliedMark() {
        return appliedMark;
    }

    public void setAppliedMark(String appliedMark) {
        this.appliedMark = appliedMark;
    }

    public MarkSource getSource() {
        return source;
    }

    public void setSource(MarkSource source) {
        this.source = source;
    }

    public String getReservationId() {
        return reservationId;
    }

    public void setReservationId(String reservationId) {
        this.reservationId = reservationId;
    }

    public long getTtlSec() {
        return ttlSec;
    }

    public void setTtlSec(long ttlSec) {
        this.ttlSec = ttlSec;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(ErrorCode errorCode) {
        this.errorCode = errorCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
