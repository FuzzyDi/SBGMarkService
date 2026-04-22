package uz.sbg.kmreplacement.state;

import uz.sbg.kmreplacement.util.Strings;

/**
 * Ключ корреляции межвызовной записи замены.
 *
 * <p>Формат: {@code shop|pos|receipt|product}, где:
 * <ul>
 *   <li>{@code shop}    — номер магазина (POSInfo.getShopNumber);</li>
 *   <li>{@code pos}     — номер кассы (POSInfo.getPOSNumber);</li>
 *   <li>{@code receipt} — номер чека (Receipt.getNumber); если недоступен, "-";</li>
 *   <li>{@code product} — GTIN из MarkInfo, иначе barcode, иначе "-".</li>
 * </ul></p>
 *
 * <p>Почему ключом является {@code (receipt, product)}, а не {@code (receipt)}:
 * в одном чеке может быть несколько разных товаров, каждый со своим КМ. По одному
 * товару в одном чеке разрешена ровно одна активная замена.</p>
 */
public final class CorrelationKey {

    private final String raw;

    private CorrelationKey(String raw) {
        this.raw = raw;
    }

    public static CorrelationKey of(int shop, int pos, String receipt, String product) {
        String r = Strings.isBlank(receipt) ? "-" : receipt;
        String pr = Strings.isBlank(product) ? "-" : product;
        return new CorrelationKey(shop + "|" + pos + "|" + r + "|" + pr);
    }

    public String asString() { return raw; }

    @Override
    public String toString() { return raw; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CorrelationKey)) return false;
        return raw.equals(((CorrelationKey) o).raw);
    }

    @Override
    public int hashCode() { return raw.hashCode(); }
}
