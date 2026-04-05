package uz.sbg.marking.contracts;

public class ValidationRequest {
    private ValidationOperationType operationType;
    private ProductRef product;
    private String scannedMark;

    public ValidationOperationType getOperationType() {
        return operationType;
    }

    public void setOperationType(ValidationOperationType operationType) {
        this.operationType = operationType;
    }

    public ProductRef getProduct() {
        return product;
    }

    public void setProduct(ProductRef product) {
        this.product = product;
    }

    public String getScannedMark() {
        return scannedMark;
    }

    public void setScannedMark(String scannedMark) {
        this.scannedMark = scannedMark;
    }
}
