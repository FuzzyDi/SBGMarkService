package uz.sbg.marking.contracts;

import java.util.ArrayList;
import java.util.List;

public class ImportRequest {
    private String batchId;
    private List<ImportMarkItem> items = new ArrayList<>();

    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public List<ImportMarkItem> getItems() {
        return items;
    }

    public void setItems(List<ImportMarkItem> items) {
        this.items = items;
    }
}
