package uz.sbg.marking.contracts;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class FifoByProductResponse {
    private Instant generatedAt;
    private ProductRef product = new ProductRef();
    private int total;
    private int selectableCount;
    private String firstSelectableMark;
    private String message;
    private List<FifoCandidateView> candidates = new ArrayList<>();

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }

    public ProductRef getProduct() {
        return product;
    }

    public void setProduct(ProductRef product) {
        this.product = product;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getSelectableCount() {
        return selectableCount;
    }

    public void setSelectableCount(int selectableCount) {
        this.selectableCount = selectableCount;
    }

    public String getFirstSelectableMark() {
        return firstSelectableMark;
    }

    public void setFirstSelectableMark(String firstSelectableMark) {
        this.firstSelectableMark = firstSelectableMark;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<FifoCandidateView> getCandidates() {
        return candidates;
    }

    public void setCandidates(List<FifoCandidateView> candidates) {
        this.candidates = candidates;
    }
}
