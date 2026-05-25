package jeong.awsshop.order.application;

import jeong.awsshop.order.domain.Order;
import jeong.awsshop.order.domain.OrderRepository;
import jeong.awsshop.order.presentation.dto.OrderSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    /**
     * 현재는 임시 주문을 생성한다.
     * <p>
     * TODO 추후 오케스트레이션
     * 1. user 정보 검증 및 조회
     * 2. user의 장바구니 조회
     * 3. 장바구니 기준 product 정보 수집
     * 4. 가격/재고/배송 정보 검증
     * 5. 주문 생성 및 저장
     */
    @Transactional
    public OrderSummaryResponse createOrder() {

        // TODO : USER 정보 조회
        Long TEMP_USER_ID = 1L;

        // TODO : USER의 장바구니 조회

        // TODO : 장바구니 기준 product 정보 수집

        // TODO : 가격/재고/배송 정보 검증

        // TODO : 주문 생성 및 저장
        Order savedOrder = orderRepository.save(Order.createTemporary(TEMP_USER_ID));

        return OrderSummaryResponse.from(savedOrder);
    }

    /**
     * 주문 조회
     */
    @Transactional(readOnly = true)
    public OrderSummaryResponse getOrder(Long id) {
        Order order = orderRepository.findById(id)
            .orElseThrow(
                () -> new IllegalArgumentException("[Order] Order not found with id: " + id));

        return OrderSummaryResponse.from(order);
    }

}
