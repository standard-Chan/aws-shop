package jeong.awsshop.stock.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jeong.awsshop.product.domain.Product;
import jeong.awsshop.stock.exception.InsufficientStockException;
import jeong.awsshop.stock.exception.InvalidStockQuantityException;
import jeong.awsshop.stock.exception.StockQuantityOverflowException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "stock")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Getter
public class Stock implements Persistable<Long> {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int quantity;

    @Override
    public Long getId() {
        return productId;
    }

    @Override
    public boolean isNew() {
        return true;
    }

    public static Stock create(Product product, int initialQuantity) {
        validateQuantity(initialQuantity);

        return Stock.builder()
            .productId(product.getId())
            .product(product)
            .quantity(initialQuantity)
            .build();
    }

    public void decrease(int quantity) {
        validateQuantity(quantity);
        if (this.quantity < quantity) {
            throw new InsufficientStockException(this.productId, quantity, this.quantity);
        }
        this.quantity -= quantity;
    }

    public void increase(int quantity) {
        validateQuantity(quantity);
        if (Integer.MAX_VALUE - this.quantity < quantity) {
            throw new StockQuantityOverflowException(this.productId, this.quantity, quantity);
        }
        this.quantity += quantity;
    }

    public boolean isInsufficientFor(int quantity) {
        validateQuantity(quantity);
        return this.quantity < quantity;
    }

    private static void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new InvalidStockQuantityException(quantity);
        }
    }
}
