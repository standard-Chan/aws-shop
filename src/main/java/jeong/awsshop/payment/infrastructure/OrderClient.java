package jeong.awsshop.payment.infrastructure;

import jeong.awsshop.payment.domain.dto.OrderSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderClient {

    /**
     * order server로 부터 order id에 해당하는 주문 정보를 받아온다
     */
    public OrderSummary getOrder(Long orderId) {
        // order server로 부터 order id 에 해당하는 주문을 받아온다.

        return OrderSummary.createTempOrderSummary();
    }

}
