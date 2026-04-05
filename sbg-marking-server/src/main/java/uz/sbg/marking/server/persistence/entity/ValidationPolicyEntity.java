package uz.sbg.marking.server.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "validation_policy")
public class ValidationPolicyEntity {
    @Id
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "reject_unknown_mark", nullable = false)
    private boolean rejectUnknownMark;

    @Column(name = "require_product_match", nullable = false)
    private boolean requireProductMatch;

    @Column(name = "reject_invalid_flag", nullable = false)
    private boolean rejectInvalidFlag;

    @Column(name = "reject_blocked", nullable = false)
    private boolean rejectBlocked;

    @Column(name = "sale_allowed_statuses", nullable = false, length = 256)
    private String saleAllowedStatuses;

    @Column(name = "return_allowed_statuses", nullable = false, length = 256)
    private String returnAllowedStatuses;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public boolean isRejectUnknownMark() {
        return rejectUnknownMark;
    }

    public void setRejectUnknownMark(boolean rejectUnknownMark) {
        this.rejectUnknownMark = rejectUnknownMark;
    }

    public boolean isRequireProductMatch() {
        return requireProductMatch;
    }

    public void setRequireProductMatch(boolean requireProductMatch) {
        this.requireProductMatch = requireProductMatch;
    }

    public boolean isRejectInvalidFlag() {
        return rejectInvalidFlag;
    }

    public void setRejectInvalidFlag(boolean rejectInvalidFlag) {
        this.rejectInvalidFlag = rejectInvalidFlag;
    }

    public boolean isRejectBlocked() {
        return rejectBlocked;
    }

    public void setRejectBlocked(boolean rejectBlocked) {
        this.rejectBlocked = rejectBlocked;
    }

    public String getSaleAllowedStatuses() {
        return saleAllowedStatuses;
    }

    public void setSaleAllowedStatuses(String saleAllowedStatuses) {
        this.saleAllowedStatuses = saleAllowedStatuses;
    }

    public String getReturnAllowedStatuses() {
        return returnAllowedStatuses;
    }

    public void setReturnAllowedStatuses(String returnAllowedStatuses) {
        this.returnAllowedStatuses = returnAllowedStatuses;
    }
}
