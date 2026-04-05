package uz.sbg.marking.contracts;

import java.util.ArrayList;
import java.util.List;

public class AdminAuditQueryResponse {
    private int total;
    private List<AdminAuditEventView> events = new ArrayList<>();

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public List<AdminAuditEventView> getEvents() {
        return events;
    }

    public void setEvents(List<AdminAuditEventView> events) {
        this.events = events;
    }
}
