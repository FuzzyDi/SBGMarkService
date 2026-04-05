package uz.sbg.marking.server.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "admin_audit_events")
public class AdminAuditEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_ts", nullable = false)
    private Instant eventTs;

    @Column(name = "actor_user", length = 128)
    private String actorUser;

    @Column(name = "actor_role", length = 32)
    private String actorRole;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Column(name = "endpoint", length = 256)
    private String endpoint;

    @Column(name = "request_id", length = 128)
    private String requestId;

    @Column(name = "remote_addr", length = 128)
    private String remoteAddr;

    @Column(name = "target_mark_code", length = 512)
    private String targetMarkCode;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Column(name = "message", length = 1024)
    private String message;

    @Lob
    @Column(name = "payload_json")
    private String payloadJson;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Instant getEventTs() {
        return eventTs;
    }

    public void setEventTs(Instant eventTs) {
        this.eventTs = eventTs;
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
