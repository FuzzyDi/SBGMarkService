package uz.sbg.marking.contracts;

import java.util.ArrayList;
import java.util.List;

public class HistoryQueryResponse {
    private long total;
    private List<MarkingHistoryEvent> events = new ArrayList<>();

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public List<MarkingHistoryEvent> getEvents() {
        return events;
    }

    public void setEvents(List<MarkingHistoryEvent> events) {
        this.events = events;
    }
}
