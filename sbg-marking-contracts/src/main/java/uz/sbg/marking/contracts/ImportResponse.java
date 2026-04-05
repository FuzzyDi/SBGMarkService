package uz.sbg.marking.contracts;

public class ImportResponse {
    private String batchId;
    private int added;
    private int updated;
    private int quarantined;
    private int skipped;

    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public int getAdded() {
        return added;
    }

    public void setAdded(int added) {
        this.added = added;
    }

    public int getUpdated() {
        return updated;
    }

    public void setUpdated(int updated) {
        this.updated = updated;
    }

    public int getQuarantined() {
        return quarantined;
    }

    public void setQuarantined(int quarantined) {
        this.quarantined = quarantined;
    }

    public int getSkipped() {
        return skipped;
    }

    public void setSkipped(int skipped) {
        this.skipped = skipped;
    }
}
