package uz.sbg.marking.plugin.dto;

public class ResolveAndReserveRequest {
    private String operationId;
    private String shopId;
    private String posId;
    private String cashierId;
    private ProductRef product;
    private String scannedMark;
    private int quantity = 1;

    public String getOperationId() { return operationId; }
    public void setOperationId(String operationId) { this.operationId = operationId; }

    public String getShopId() { return shopId; }
    public void setShopId(String shopId) { this.shopId = shopId; }

    public String getPosId() { return posId; }
    public void setPosId(String posId) { this.posId = posId; }

    public String getCashierId() { return cashierId; }
    public void setCashierId(String cashierId) { this.cashierId = cashierId; }

    public ProductRef getProduct() { return product; }
    public void setProduct(ProductRef product) { this.product = product; }

    public String getScannedMark() { return scannedMark; }
    public void setScannedMark(String scannedMark) { this.scannedMark = scannedMark; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
