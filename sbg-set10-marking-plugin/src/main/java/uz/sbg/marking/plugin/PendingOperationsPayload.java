package uz.sbg.marking.plugin;

import uz.sbg.marking.plugin.dto.MarkOperationRequest;

import java.util.ArrayList;
import java.util.List;

public class PendingOperationsPayload {
    private String endpoint;
    private List<MarkOperationRequest> requests = new ArrayList<MarkOperationRequest>();

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
