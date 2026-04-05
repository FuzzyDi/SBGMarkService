package uz.sbg.marking.plugin;

import java.util.ArrayList;
import java.util.List;

import uz.sbg.marking.contracts.MarkOperationRequest;

public class PendingOperationsPayload {
    private String endpoint;
    private List<MarkOperationRequest> requests = new ArrayList<>();

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public List<MarkOperationRequest> getRequests() {
        return requests;
    }

    public void setRequests(List<MarkOperationRequest> requests) {
        this.requests = requests;
    }
}
