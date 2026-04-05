package uz.sbg.marking.contracts;

public class MarkAdminUpsertRequest {
    private String markCode;
    private String item;
    private String gtin;
    private String productType;
    private Boolean valid;
    private Boolean blocked;
    private MarkStatus status;
    private Long fifoTsEpochMs;

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
}
