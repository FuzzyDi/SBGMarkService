package uz.sbg.kmreplacement.http.dto;

public class ResolveAndReserveRequest {
    private String operationId;
    private String shopId;
    private String posId;
    private String cashierId;
    private ProductRef product;
    private String scannedMark;
    private int quantity = 1;

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
    public int    getQuantity()           { return quantity; }
    public void   setQuantity(int v)      { this.quantity = v; }
}
