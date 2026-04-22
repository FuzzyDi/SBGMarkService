package uz.sbg.kmreplacement.resolver;

/**
 * Иммутабельный контекст запроса к resolver — всё, что нужно бекенду
 * для проверки/подбора замены. Выдран из ExciseValidationRequest, чтобы
 * resolver не зависел от SR10 SDK и легко тестировался.
 */
public final class ResolveContext {

    private final String gtin;
    private final String barcode;
    private final String productType;
    private final int shopNumber;
    private final int posNumber;
    private final String receiptNumber;
    private final boolean refund;

    public ResolveContext(String gtin,
                          String barcode,
                          String productType,
                          int shopNumber,
                          int posNumber,
                          String receiptNumber,
                          boolean refund) {
        this.gtin = gtin;
        this.barcode = barcode;
        this.productType = productType;
        this.shopNumber = shopNumber;
        this.posNumber = posNumber;
        this.receiptNumber = receiptNumber;
        this.refund = refund;
    }

    public String getGtin()          { return gtin; }
    public String getBarcode()       { return barcode; }
    public String getProductType()   { return productType; }
    public int    getShopNumber()    { return shopNumber; }
    public int    getPosNumber()     { return posNumber; }
    public String getReceiptNumber() { return receiptNumber; }
    public boolean isRefund()        { return refund; }
}
