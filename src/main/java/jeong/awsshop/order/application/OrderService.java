package jeong.awsshop.order.application;

import java.util.Optional;
import jeong.awsshop.order.domain.Order;
import jeong.awsshop.order.domain.OrderRepository;
import jeong.awsshop.order.presentation.dto.OrderSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    /** 주문 조회 */
    public OrderSummaryResponse getOrder(Long id) {
            Order order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("[Order] Order not found with id: " + id));

            return new OrderSummaryResponse(order.getId(), order.getTotalAmount());
    }

}
