package uz.sbg.kmreplacement.state;

/**
 * Межвызовная запись о замене КМ. Изменяемая, но модифицируется только
 * изнутри StateMachine — снаружи её клиенты только читают.
 *
 * <p>Ключ хранения: {@code (correlationKey, attemptIndex)}. {@code attemptIndex}
 * — 1-based порядковый номер попытки замены в пределах одного
 * {@code correlationKey} (т.е. в пределах одной тройки shop|pos|receipt|gtin).
 * Это позволяет отслеживать несколько одинаковых товаров в одном чеке — по
 * своей записи на каждую позицию (например, 4 кока-колы с 4 разными КМ).</p>
 */
public final class ReplacementState {

    private final CorrelationKey correlationKey;
    private final int attemptIndex;
    private final String originalKm;
    private String replacementKm;
    private final String barcode;
    private final String productType;
    private Status status;
    private final long createdAtMs;
    private long expiresAtMs;
    private int attemptCount;
    private String lastRejectReason;
    private final int receiptNumber;   // для очистки при фискализации/отмене чека
    private String reservationId;      // id резерва на backend (null для stub)

    public ReplacementState(CorrelationKey correlationKey,
                            int attemptIndex,
                            String originalKm,
                            String replacementKm,
                            String barcode,
                            String productType,
                            Status status,
                            long createdAtMs,
                            long expiresAtMs,
                            int attemptCount,
                            int receiptNumber) {
        this.correlationKey = correlationKey;
        this.attemptIndex   = attemptIndex;
        this.originalKm     = originalKm;
        this.replacementKm  = replacementKm;
        this.barcode        = barcode;
        this.productType    = productType;
        this.status         = status;
        this.createdAtMs    = createdAtMs;
        this.expiresAtMs    = expiresAtMs;
        this.attemptCount   = attemptCount;
        this.receiptNumber  = receiptNumber;
    }

    public CorrelationKey getCorrelationKey() { return correlationKey; }
    public int    getAttemptIndex()           { return attemptIndex; }
    public String getOriginalKm()             { return originalKm; }
    public String getReplacementKm()          { return replacementKm; }
    public String getBarcode()                { return barcode; }
    public String getProductType()            { return productType; }
    public Status getStatus()                 { return status; }
    public long   getCreatedAtMs()            { return createdAtMs; }
    public long   getExpiresAtMs()            { return expiresAtMs; }
    public int    getAttemptCount()           { return attemptCount; }
    public String getLastRejectReason()       { return lastRejectReason; }
    public int    getReceiptNumber()          { return receiptNumber; }
    public String getReservationId()          { return reservationId; }

    public void setStatus(Status s)                     { this.status = s; }
    public void setReplacementKm(String km)             { this.replacementKm = km; }
    public void setExpiresAtMs(long t)                  { this.expiresAtMs = t; }
    public void incrementAttemptCount()                 { this.attemptCount++; }
    public void setLastRejectReason(String r)           { this.lastRejectReason = r; }
    public void setReservationId(String rid)            { this.reservationId = rid; }

    public boolean isExpired(long nowMs) {
        return nowMs >= expiresAtMs;
    }

    public boolean isTerminal() {
        return status == Status.REPLACEMENT_ACCEPTED
                || status == Status.EXPIRED
                || status == Status.FAILED;
    }

    @Override
    public String toString() {
        return "ReplacementState{" + correlationKey + "#" + attemptIndex
                + ", original=" + abbreviate(originalKm)
                + ", replacement=" + abbreviate(replacementKm)
                + ", status=" + status
                + ", attempts=" + attemptCount
                + ", expiresAt=" + expiresAtMs + "}";
    }

    private static String abbreviate(String km) {
        if (km == null) return "null";
        if (km.length() <= 20) return km;
        return km.substring(0, 17) + "...";
    }
}
