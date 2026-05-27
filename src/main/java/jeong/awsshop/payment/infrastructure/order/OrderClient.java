package jeong.awsshop.payment.infrastructure.order;

import jeong.awsshop.payment.exception.infrastructure.PaymentOrderAlreadyExecutingException;
import jeong.awsshop.payment.exception.infrastructure.PaymentOrderLookupException;
import jeong.awsshop.payment.infrastructure.order.dto.OrderSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

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
     * order server로 부터 order 상태를 EXECUTING으로 갱신 한다
     * 사용 시기 : 해당 order의 status != EXECUTING 인 경우에만 성공을 반환하고
     * EXECUTING인 경우, 이미 결제 처리중인 Payment가 있다고 판단할 것.
     */
    public OrderSummary updateExecutingStatus(Long orderId) {
        OrderSummary response;
        try {
            response = orderRestClient.post()
                .uri("/api/orders/{orderId}/executing", orderId)
                .retrieve()
                .body(OrderSummary.class);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode() == HttpStatus.CONFLICT) {
                throw new PaymentOrderAlreadyExecutingException(orderId, exception);
            }
            throw new PaymentOrderLookupException(orderId, exception);
        }

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
