package uz.sbg.marking.contracts;

public class ValidationResponse {
    private boolean success;
    private ValidationResultCode code = ValidationResultCode.OK;
    private String message;
    private String markCode;
    private MarkStatus markStatus;
    private Boolean valid;
    private Boolean blocked;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public ValidationResultCode getCode() {
        return code;
    }

    public void setCode(ValidationResultCode code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getMarkCode() {
        return markCode;
    }

    public void setMarkCode(String markCode) {
        this.markCode = markCode;
    }

    public MarkStatus getMarkStatus() {
        return markStatus;
    }

    public void setMarkStatus(MarkStatus markStatus) {
        this.markStatus = markStatus;
    }

    public Boolean getValid() {
        return valid;
    }

    public void setValid(Boolean valid) {
        this.valid = valid;
    }

    public Boolean getBlocked() {
        return blocked;
    }

    public void setBlocked(Boolean blocked) {
        this.blocked = blocked;
    }
}
