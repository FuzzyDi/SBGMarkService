package uz.sbg.marking.contracts;

import java.util.ArrayList;
import java.util.List;

public class ValidationPolicy {
    private boolean rejectUnknownMark = true;
    private boolean requireProductMatch = true;
    private boolean rejectInvalidFlag = true;
    private boolean rejectBlocked = true;
    private List<MarkStatus> saleAllowedStatuses = new ArrayList<>(List.of(MarkStatus.AVAILABLE));
    private List<MarkStatus> returnAllowedStatuses = new ArrayList<>(List.of(MarkStatus.SOLD));

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

    public List<MarkStatus> getSaleAllowedStatuses() {
        return saleAllowedStatuses;
    }

    public void setSaleAllowedStatuses(List<MarkStatus> saleAllowedStatuses) {
        this.saleAllowedStatuses = saleAllowedStatuses;
    }

    public List<MarkStatus> getReturnAllowedStatuses() {
        return returnAllowedStatuses;
    }

    public void setReturnAllowedStatuses(List<MarkStatus> returnAllowedStatuses) {
        this.returnAllowedStatuses = returnAllowedStatuses;
    }
}
