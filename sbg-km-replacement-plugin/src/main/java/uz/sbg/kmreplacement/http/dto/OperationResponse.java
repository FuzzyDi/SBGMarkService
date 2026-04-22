package uz.sbg.kmreplacement.http.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OperationResponse {
    private boolean success;
    private ErrorCode errorCode = ErrorCode.NONE;
    private String message;

    public boolean   isSuccess()           { return success; }
    public void      setSuccess(boolean v) { this.success = v; }
    public ErrorCode getErrorCode()        { return errorCode; }
    public void      setErrorCode(ErrorCode v) { this.errorCode = v; }
    public String    getMessage()          { return message; }
    public void      setMessage(String v)  { this.message = v; }
}
