package uz.sbg.marking.contracts;

public class OperationResponse {
    private boolean success;
    private ErrorCode errorCode = ErrorCode.NONE;
    private String message;

    public static OperationResponse ok(String message) {
        OperationResponse response = new OperationResponse();
        response.setSuccess(true);
        response.setMessage(message);
        response.setErrorCode(ErrorCode.NONE);
        return response;
    }

    public static OperationResponse fail(ErrorCode errorCode, String message) {
        OperationResponse response = new OperationResponse();
        response.setSuccess(false);
        response.setErrorCode(errorCode);
        response.setMessage(message);
        return response;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
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
