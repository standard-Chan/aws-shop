package jeong.awsshop.payment.exception.infrastructure;

import jeong.awsshop.payment.exception.PaymentException;

public class InventoryLookupException extends PaymentException {

    public InventoryLookupException(Long productId, Throwable cause) {
        super("[Payment] 재고 조회에 실패했습니다. productId=" + productId, cause);
    }
}
