package uz.sbg.kmreplacement.http.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ResolveAndReserveResponse {
    private ResolveResult result;
    private String appliedMark;
    private MarkSource source;
    private String reservationId;
    private long ttlSec;
    private ErrorCode errorCode = ErrorCode.NONE;
    private String message;
    private List<String> availableMarks;

    public ResolveResult getResult()        { return result; }
    public void          setResult(ResolveResult v) { this.result = v; }
    public String        getAppliedMark()   { return appliedMark; }
    public void          setAppliedMark(String v)   { this.appliedMark = v; }
    public MarkSource    getSource()        { return source; }
    public void          setSource(MarkSource v)    { this.source = v; }
    public String        getReservationId() { return reservationId; }
    public void          setReservationId(String v) { this.reservationId = v; }
    public long          getTtlSec()        { return ttlSec; }
    public void          setTtlSec(long v)          { this.ttlSec = v; }
    public ErrorCode     getErrorCode()     { return errorCode; }
    public void          setErrorCode(ErrorCode v)  { this.errorCode = v; }
    public String        getMessage()       { return message; }
    public void          setMessage(String v)       { this.message = v; }
    public List<String>  getAvailableMarks(){ return availableMarks; }
    public void          setAvailableMarks(List<String> v) { this.availableMarks = v; }
}
