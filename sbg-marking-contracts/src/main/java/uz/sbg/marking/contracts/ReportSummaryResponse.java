package uz.sbg.marking.contracts;

import java.time.Instant;

public class ReportSummaryResponse {
    private Instant generatedAt;
    private long totalMarks;
    private long availableMarks;
    private long reservedMarks;
    private long soldMarks;
    private long returnReservedMarks;
    private long activeReservations;
    private long historyEvents;
    private long saleConfirmCount;
    private long returnConfirmCount;

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }

    public long getTotalMarks() {
        return totalMarks;
    }

    public void setTotalMarks(long totalMarks) {
        this.totalMarks = totalMarks;
    }

    public long getAvailableMarks() {
        return availableMarks;
    }

    public void setAvailableMarks(long availableMarks) {
        this.availableMarks = availableMarks;
    }

    public long getReservedMarks() {
        return reservedMarks;
    }

    public void setReservedMarks(long reservedMarks) {
        this.reservedMarks = reservedMarks;
    }

    public long getSoldMarks() {
        return soldMarks;
    }

    public void setSoldMarks(long soldMarks) {
        this.soldMarks = soldMarks;
    }

    public long getReturnReservedMarks() {
        return returnReservedMarks;
    }

    public void setReturnReservedMarks(long returnReservedMarks) {
        this.returnReservedMarks = returnReservedMarks;
    }

    public long getActiveReservations() {
        return activeReservations;
    }

    public void setActiveReservations(long activeReservations) {
        this.activeReservations = activeReservations;
    }

    public long getHistoryEvents() {
        return historyEvents;
    }

    public void setHistoryEvents(long historyEvents) {
        this.historyEvents = historyEvents;
    }

    public long getSaleConfirmCount() {
        return saleConfirmCount;
    }

    public void setSaleConfirmCount(long saleConfirmCount) {
        this.saleConfirmCount = saleConfirmCount;
    }

    public long getReturnConfirmCount() {
        return returnConfirmCount;
    }

    public void setReturnConfirmCount(long returnConfirmCount) {
        this.returnConfirmCount = returnConfirmCount;
    }
}
