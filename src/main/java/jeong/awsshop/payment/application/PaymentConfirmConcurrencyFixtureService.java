package jeong.awsshop.payment.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import jeong.awsshop.common.snowflake.SnowflakeIdGenerator;
import jeong.awsshop.order.domain.Order;
import jeong.awsshop.order.domain.OrderLine;
import jeong.awsshop.order.domain.OrderRepository;
import jeong.awsshop.order.domain.OrderStatus;
import jeong.awsshop.payment.domain.Payment;
import jeong.awsshop.payment.domain.PaymentRepository;
import jeong.awsshop.payment.domain.PaymentStatus;
import jeong.awsshop.payment.infrastructure.MockTossPaymentClient;
import jeong.awsshop.payment.infrastructure.tosspayment.dto.TossPaymentConfirmRequest;
import jeong.awsshop.product.domain.Product;
import jeong.awsshop.product.repository.ProductRepository;
import jeong.awsshop.stock.domain.Stock;
import jeong.awsshop.stock.domain.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("dev")
@ConditionalOnProperty(name = "app.payment.toss.mode", havingValue = "mock")
@RequiredArgsConstructor
public class PaymentConfirmConcurrencyFixtureService {

    private static final long TEST_USER_ID = 1L;
    private static final int PRODUCT_ONE_QUANTITY = 2;
    private static final int PRODUCT_TWO_QUANTITY = 1;
    private static final int INITIAL_STOCK_QUANTITY = 100;
    private static final BigDecimal PRODUCT_ONE_PRICE = new BigDecimal("30.00");
    private static final BigDecimal PRODUCT_TWO_PRICE = new BigDecimal("40.00");
    private static final BigDecimal WON_EXCHANGE_RATE = new BigDecimal("1400");

    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final MockTossPaymentClient mockTossPaymentClient;

    /** 같은 결제 승인 요청을 병렬로 보낼 수 있도록 상품, 재고, 주문, 결제 fixture를 생성한다. */
    @Transactional
    public PaymentConfirmConcurrencyFixtureResponse createFixture() {
        Long firstProductId = snowflakeIdGenerator.nextId();
        Long secondProductId = snowflakeIdGenerator.nextId();
        Long paymentId = snowflakeIdGenerator.nextId();

        Product firstProduct = createProduct(firstProductId, "payment-confirm-cas-1", PRODUCT_ONE_PRICE);
        Product secondProduct = createProduct(secondProductId, "payment-confirm-cas-2", PRODUCT_TWO_PRICE);
        productRepository.saveAll(List.of(firstProduct, secondProduct));
        stockRepository.saveAll(List.of(
            Stock.create(firstProduct, INITIAL_STOCK_QUANTITY),
            Stock.create(secondProduct, INITIAL_STOCK_QUANTITY)
        ));

        Order order = Order.create(TEST_USER_ID, List.of(
            OrderLine.create(firstProductId, PRODUCT_ONE_QUANTITY, PRODUCT_ONE_PRICE),
            OrderLine.create(secondProductId, PRODUCT_TWO_QUANTITY, PRODUCT_TWO_PRICE)
        ));
        Order savedOrder = orderRepository.saveAndFlush(order);
        int executingUpdatedCount = orderRepository.updateStatusToExecutingIfAvailable(savedOrder.getId());
        if (executingUpdatedCount != 1) {
            throw new IllegalStateException("결제 승인 동시성 fixture 주문을 EXECUTING 상태로 만들 수 없습니다.");
        }

        LocalDateTime now = LocalDateTime.now();
        Payment payment = Payment.builder()
            .id(paymentId)
            .orderId(savedOrder.getId())
            .status(PaymentStatus.NOT_STARTED)
            .amount(savedOrder.getTotalAmount())
            .createdAt(now)
            .expiresAt(now.plusMinutes(5))
            .build();
        paymentRepository.saveAndFlush(payment);

        mockTossPaymentClient.reset();

        BigDecimal confirmAmount = savedOrder.getTotalAmount().multiply(WON_EXCHANGE_RATE);
        return new PaymentConfirmConcurrencyFixtureResponse(
            String.valueOf(paymentId),
            savedOrder.getId(),
            "mock-payment-key-" + paymentId,
            confirmAmount,
            List.of(String.valueOf(firstProductId), String.valueOf(secondProductId))
        );
    }

    /** 병렬 승인 요청 후 Payment, Order, 재고, Toss mock 호출 수의 최종 상태를 조회한다. */
    @Transactional(readOnly = true)
    public PaymentConfirmConcurrencyResultResponse getResult(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new IllegalArgumentException("결제 fixture를 찾을 수 없습니다. paymentId=" + paymentId));
        Order order = orderRepository.findById(payment.getOrderId())
            .orElseThrow(() -> new IllegalArgumentException("주문 fixture를 찾을 수 없습니다. orderId=" + payment.getOrderId()));
        List<Long> productIds = order.getLines().stream()
            .map(OrderLine::getProductId)
            .toList();
        List<StockSnapshot> stocks = stockRepository.findAllByProductIdIn(productIds).stream()
            .sorted(Comparator.comparing(Stock::getProductId))
            .map(stock -> new StockSnapshot(String.valueOf(stock.getProductId()), stock.getQuantity()))
            .toList();

        return new PaymentConfirmConcurrencyResultResponse(
            String.valueOf(payment.getId()),
            payment.getStatus(),
            order.getId(),
            order.getStatus(),
            mockTossPaymentClient.confirmCount(),
            stocks
        );
    }

    /** mock Toss confirm 호출 횟수와 요청 이력을 반환해 외부 PSP 호출이 1회인지 검증한다. */
    public PaymentConfirmConcurrencyTossStatsResponse getTossStats() {
        List<MockTossConfirmRequestSnapshot> requests = mockTossPaymentClient.confirmRequests().stream()
            .map(MockTossConfirmRequestSnapshot::from)
            .toList();
        return new PaymentConfirmConcurrencyTossStatsResponse(mockTossPaymentClient.confirmCount(), requests);
    }

    /** 결제 승인 동시성 검증 fixture에서 사용할 테스트 상품을 생성한다. */
    private Product createProduct(Long productId, String suffix, BigDecimal price) {
        return Product.builder()
            .id(productId)
            .parentAsin("PAYMENT-CONFIRM-CAS-" + suffix + "-" + productId)
            .title("Payment Confirm CAS Test Product " + suffix)
            .mainCategory("payment-test")
            .price(price)
            .build();
    }

    public record PaymentConfirmConcurrencyFixtureResponse(
        String paymentId,
        Long orderId,
        String paymentKey,
        BigDecimal amount,
        List<String> productIds
    ) {
    }

    public record PaymentConfirmConcurrencyTossStatsResponse(
        int confirmCount,
        List<MockTossConfirmRequestSnapshot> requests
    ) {
    }

    public record PaymentConfirmConcurrencyResultResponse(
        String paymentId,
        PaymentStatus paymentStatus,
        Long orderId,
        OrderStatus orderStatus,
        int tossConfirmCount,
        List<StockSnapshot> stocks
    ) {
    }

    public record MockTossConfirmRequestSnapshot(
        String orderId,
        String paymentKey,
        BigDecimal amount
    ) {

        /** mock Toss 요청 DTO를 검증 응답용 snapshot으로 변환한다. */
        static MockTossConfirmRequestSnapshot from(TossPaymentConfirmRequest request) {
            return new MockTossConfirmRequestSnapshot(
                String.valueOf(request.orderId()),
                request.paymentKey(),
                request.amount()
            );
        }
    }

    public record StockSnapshot(String productId, int quantity) {
    }
}
