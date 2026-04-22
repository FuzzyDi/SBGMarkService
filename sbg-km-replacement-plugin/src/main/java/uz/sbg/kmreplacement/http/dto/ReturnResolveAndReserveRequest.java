package uz.sbg.kmreplacement.http.dto;

public class ReturnResolveAndReserveRequest {
    private String operationId;
    private String shopId;
    private String posId;
    private String cashierId;
    private ProductRef product;
    private String scannedMark;
    private String saleReceiptId;

    public String getOperationId()        { return operationId; }
    public void   setOperationId(String v){ this.operationId = v; }
    public String getShopId()             { return shopId; }
    public void   setShopId(String v)     { this.shopId = v; }
    public String getPosId()              { return posId; }
    public void   setPosId(String v)      { this.posId = v; }
    public String getCashierId()          { return cashierId; }
    public void   setCashierId(String v)  { this.cashierId = v; }
    public ProductRef getProduct()        { return product; }
    public void   setProduct(ProductRef v){ this.product = v; }
    public String getScannedMark()        { return scannedMark; }
    public void   setScannedMark(String v){ this.scannedMark = v; }
    public String getSaleReceiptId()      { return saleReceiptId; }
    public void   setSaleReceiptId(String v) { this.saleReceiptId = v; }
}
