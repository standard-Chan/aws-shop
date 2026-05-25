package jeong.awsshop.order.presentation;

import jeong.awsshop.order.application.OrderService;
import jeong.awsshop.order.presentation.dto.OrderSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public OrderSummaryResponse createOrder() {
        return orderService.createOrder();
    }

    @GetMapping("{id}")
    public OrderSummaryResponse getOrderById(@PathVariable("id") Long id) {
        return orderService.getOrder(id);
    }

    @PostMapping("{id}/pending")
    public OrderSummaryResponse pendingOrder(@PathVariable("id") Long id) {
        return orderService.pendingOrder(id);
    }

    @PostMapping("{id}/success")
    public OrderSummaryResponse completeOrder(@PathVariable("id") Long id) {
        return orderService.completeOrder(id);
    }

    @PostMapping("{id}/fail")
    public OrderSummaryResponse cancelOrder(@PathVariable("id") Long id) {
        return orderService.cancelOrder(id);
    }

}
