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

    /**
     * order server에 해당 id 주문의 상태를 COMPLETED로 갱신한다.
     * 사용 시기 : 결제 처리가 완료된 시점
     */
    public OrderSummary updateCompleteOrder(Long orderId) {
        OrderSummary response = orderRestClient.post()
            .uri("/api/orders/{orderId}/success", orderId)
            .retrieve()
            .body(OrderSummary.class);

        if (response == null) {
            throw new PaymentOrderLookupException(orderId, "Order 상태를 COMPLETED 로 갱신하지 못 했습니다.");
        }

        return response;
    }

    public OrderSummary updateCancelOrder(Long orderId) {
        OrderSummary response = orderRestClient.post()
            .uri("/api/orders/{orderId}/fail", orderId)
            .retrieve()
            .body(OrderSummary.class);

        if (response == null) {
            throw new PaymentOrderLookupException(orderId, "Order 상태를 CANCELED 로 갱신하지 못 했습니다.");
        }

        return response;
    }

    public OrderSummary updatePendingOrder(Long orderId) {
        OrderSummary response = orderRestClient.post()
            .uri("/api/orders/{orderId}/pending", orderId)
            .retrieve()
            .body(OrderSummary.class);

        if (response == null) {
            throw new PaymentOrderLookupException(orderId, "Order 상태를 PENDING 으로 갱신하지 못 했습니다.");
        }

        return response;
    }
}
