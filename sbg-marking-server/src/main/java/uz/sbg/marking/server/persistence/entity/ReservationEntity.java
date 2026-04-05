package uz.sbg.marking.server.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "reservations")
public class ReservationEntity {
    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "operation_id", nullable = false, length = 128)
    private String operationId;

    @Column(name = "mark_code", nullable = false, length = 256)
    private String markCode;

    @Column(name = "type", nullable = false, length = 32)
    private String type;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public String getMarkCode() {
        return markCode;
    }

    public void setMarkCode(String markCode) {
        this.markCode = markCode;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
