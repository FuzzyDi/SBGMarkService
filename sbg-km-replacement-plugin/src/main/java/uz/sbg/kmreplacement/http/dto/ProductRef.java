package uz.sbg.kmreplacement.http.dto;

/**
 * Идентификация товара для backend'а маркировки.
 *
 * <p>На сервере приоритет сопоставления (см. CLAUDE.md §Backend):
 * {@code gtin} → {@code barcode} → {@code productType} → {@code item}.</p>
 */
public class ProductRef {
    private String barcode;
    private String item;
    private String productType;
    private String gtin;

    public String getBarcode()       { return barcode; }
    public void   setBarcode(String v) { this.barcode = v; }
    public String getItem()          { return item; }
    public void   setItem(String v)  { this.item = v; }
    public String getProductType()   { return productType; }
    public void   setProductType(String v) { this.productType = v; }
    public String getGtin()          { return gtin; }
    public void   setGtin(String v)  { this.gtin = v; }
}
