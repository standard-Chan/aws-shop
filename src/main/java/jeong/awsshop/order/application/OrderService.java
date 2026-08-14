package jeong.awsshop.order.application;

import jeong.awsshop.order.domain.Order;
import jeong.awsshop.order.domain.OrderLine;
import jeong.awsshop.order.domain.OrderRepository;
import jeong.awsshop.order.domain.OrderStatus;
import jeong.awsshop.order.exception.OrderAlreadyCanceledException;
import jeong.awsshop.order.exception.OrderAlreadyCompletedException;
import jeong.awsshop.order.exception.OrderAlreadyExecutingException;
import jeong.awsshop.order.exception.OrderExpiredException;
import jeong.awsshop.order.exception.OrderInsufficientStockException;
import jeong.awsshop.order.exception.OrderNotFoundException;
import jeong.awsshop.order.exception.OrderProductNotFoundException;
import jeong.awsshop.order.exception.OrderStockNotFoundException;
import jeong.awsshop.order.presentation.dto.CreateOrderItemRequest;
import jeong.awsshop.order.presentation.dto.CreateOrderRequest;
import jeong.awsshop.order.presentation.dto.OrderSummaryResponse;
import jeong.awsshop.product.domain.Product;
import jeong.awsshop.product.repository.ProductRepository;
import jeong.awsshop.stock.domain.Stock;
import jeong.awsshop.stock.domain.StockRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final StockRepository stockRepository;

    @Transactional
    public OrderSummaryResponse createOrder(CreateOrderRequest request) {
        Long TEMP_USER_ID = 1L;
        Map<Long, Integer> requestedItems = aggregateItems(request.items());
        Map<Long, Product> products = findProducts(requestedItems);
        validateStocks(requestedItems);
        List<OrderLine> lines = requestedItems.entrySet().stream()
            .map(entry -> OrderLine.create(
                entry.getKey(),
                entry.getValue(),
                products.get(entry.getKey()).getPrice()
            ))
            .toList();

        Order savedOrder = orderRepository.save(Order.create(TEMP_USER_ID, lines));

        return OrderSummaryResponse.from(savedOrder);
    }

    private Map<Long, Integer> aggregateItems(List<CreateOrderItemRequest> items) {
        Map<Long, Integer> requestedItems = new LinkedHashMap<>();
        for (CreateOrderItemRequest item : items) {
            requestedItems.merge(item.productId(), item.quantity(), Integer::sum);
        }
        return requestedItems;
    }

    private Map<Long, Product> findProducts(Map<Long, Integer> requestedItems) {
        Map<Long, Product> products = productRepository.findAllById(requestedItems.keySet()).stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));
        requestedItems.keySet().stream()
            .filter(productId -> !products.containsKey(productId))
            .findFirst()
            .ifPresent(productId -> {
                throw new OrderProductNotFoundException(productId);
            });
        return products;
    }

    private void validateStocks(Map<Long, Integer> requestedItems) {
        Map<Long, Stock> stocks = stockRepository.findAllByProductIdIn(requestedItems.keySet()).stream()
            .collect(Collectors.toMap(Stock::getProductId, Function.identity()));
        for (Map.Entry<Long, Integer> entry : requestedItems.entrySet()) {
            Long productId = entry.getKey();
            int requestedQuantity = entry.getValue();
            Stock stock = stocks.get(productId);
            if (stock == null) {
                throw new OrderStockNotFoundException(productId);
            }
            if (stock.isInsufficientFor(requestedQuantity)) {
                throw new OrderInsufficientStockException(productId, requestedQuantity, stock.getQuantity());
            }
        }
    }

    /**
     * 주문 조회
     */
    @Transactional(readOnly = true)
    public OrderSummaryResponse getOrder(Long id) {
        Order order = getOrderEntity(id);

        return OrderSummaryResponse.from(order);
    }

    /**
     * 주문을 executing 상태로 전환
     */
    @Transactional
    public OrderSummaryResponse executingOrder(Long id) {
        int updatedCount = orderRepository.updateStatusToExecutingIfAvailable(id);
        if (updatedCount == 1) {
            return OrderSummaryResponse.from(getOrderEntity(id));
        }

        Order order = getOrderEntity(id);
        if (order.getStatus() == OrderStatus.EXECUTING) {
            throw new OrderAlreadyExecutingException(id);
        }
        if (order.getStatus() == OrderStatus.COMPLETED) {
            throw new OrderAlreadyCompletedException(id);
        }
        if (order.getStatus() == OrderStatus.CANCELED) {
            throw new OrderAlreadyCanceledException(id);
        }
        if (order.getStatus() == OrderStatus.EXPIRED) {
            throw new OrderExpiredException(id);
        }
        throw new IllegalStateException("[Order] Failed to update order to EXECUTING. id=" + id);
    }

    /**
     * 결제 처리 실패 후 주문을 pending 상태로 전환
     */
    @Transactional
    public OrderSummaryResponse pendingOrder(Long id) {
        Order order = getOrderEntity(id);

        order.pending();
        return OrderSummaryResponse.from(order);
    }

    /**
     * 주문 성공 상태 갱신
     */
    @Transactional
    public OrderSummaryResponse completeOrder(Long id) {
        Order order = getOrderEntity(id);

        order.complete();
        return OrderSummaryResponse.from(order);
    }

    /**
     * 주문 실패 상태 갱신
     */
    @Transactional
    public OrderSummaryResponse cancelOrder(Long id) {
        Order order = getOrderEntity(id);

        order.cancel();
        return OrderSummaryResponse.from(order);
    }

    private Order getOrderEntity(Long id) {
        return orderRepository.findById(id)
            .orElseThrow(() -> new OrderNotFoundException(id));
    }

}
