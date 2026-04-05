package uz.sbg.marking.contracts;

import java.time.Instant;

public class AdminAuditEventView {
    private long id;
    private Instant timestamp;
    private String actorUser;
    private String actorRole;
    private String action;
    private String endpoint;
    private String requestId;
    private String remoteAddr;
    private String targetMarkCode;
    private boolean success;
    private String message;
    private String payloadJson;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getActorUser() {
        return actorUser;
    }

    public void setActorUser(String actorUser) {
        this.actorUser = actorUser;
    }

    public String getActorRole() {
        return actorRole;
    }

    public void setActorRole(String actorRole) {
        this.actorRole = actorRole;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getRemoteAddr() {
        return remoteAddr;
    }

    public void setRemoteAddr(String remoteAddr) {
        this.remoteAddr = remoteAddr;
    }

    public String getTargetMarkCode() {
        return targetMarkCode;
    }

    public void setTargetMarkCode(String targetMarkCode) {
        this.targetMarkCode = targetMarkCode;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }
}
