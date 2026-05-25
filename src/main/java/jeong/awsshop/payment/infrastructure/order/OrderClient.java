package jeong.awsshop.payment.infrastructure.order;

import jeong.awsshop.payment.exception.infrastructure.PaymentOrderLookupException;
import jeong.awsshop.payment.infrastructure.order.dto.OrderSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class OrderClient {

    private final RestClient orderRestClient;

    /**
     * order server로 부터 order id에 해당하는 주문 정보를 받아온다
     */
    public OrderSummary getOrder(Long orderId) {
        OrderSummary response = orderRestClient.get()
            .uri("/api/orders/{orderId}", orderId)
            .retrieve()
            .body(OrderSummary.class);

        if (response == null) {
            throw new PaymentOrderLookupException(orderId);
        }

        return response;
    }
}
