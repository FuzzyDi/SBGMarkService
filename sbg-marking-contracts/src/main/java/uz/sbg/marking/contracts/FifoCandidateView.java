package uz.sbg.marking.contracts;

public class FifoCandidateView {
    private int queuePosition;
    private String markCode;
    private String item;
    private String gtin;
    private String productType;
    private boolean valid;
    private boolean blocked;
    private MarkStatus status;
    private Long fifoTsEpochMs;
    private boolean selectable;
    private String reason;

    public int getQueuePosition() {
        return queuePosition;
    }

    public void setQueuePosition(int queuePosition) {
        this.queuePosition = queuePosition;
    }

    public String getMarkCode() {
        return markCode;
    }

    public void setMarkCode(String markCode) {
        this.markCode = markCode;
    }

    public String getItem() {
        return item;
    }

    public void setItem(String item) {
        this.item = item;
    }

    public String getGtin() {
        return gtin;
    }

    public void setGtin(String gtin) {
        this.gtin = gtin;
    }

    public String getProductType() {
        return productType;
    }

    public void setProductType(String productType) {
        this.productType = productType;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    public MarkStatus getStatus() {
        return status;
    }

    public void setStatus(MarkStatus status) {
        this.status = status;
    }

    public Long getFifoTsEpochMs() {
        return fifoTsEpochMs;
    }

    public void setFifoTsEpochMs(Long fifoTsEpochMs) {
        this.fifoTsEpochMs = fifoTsEpochMs;
    }

    public boolean isSelectable() {
        return selectable;
    }

    public void setSelectable(boolean selectable) {
        this.selectable = selectable;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
