package jeong.awsshop.eventpipeline.monolith.presentation;

import jakarta.validation.Valid;
import jeong.awsshop.eventpipeline.monolith.application.UserBehaviorEventService;
import jeong.awsshop.eventpipeline.monolith.presentation.dto.AddToCartEventRequest;
import jeong.awsshop.eventpipeline.monolith.presentation.dto.EventAcceptedResponse;
import jeong.awsshop.eventpipeline.monolith.presentation.dto.ProductViewEventRequest;
import jeong.awsshop.eventpipeline.monolith.presentation.dto.PurchaseEventRequest;
import jeong.awsshop.eventpipeline.monolith.presentation.dto.SearchEventRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/event-pipeline/events")
public class UserBehaviorEventController {

    private final UserBehaviorEventService userBehaviorEventService;

    public UserBehaviorEventController(UserBehaviorEventService userBehaviorEventService) {
        this.userBehaviorEventService = userBehaviorEventService;
    }

    @PostMapping("/search")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public EventAcceptedResponse search(@Valid @RequestBody SearchEventRequest request) {
        return userBehaviorEventService.recordSearch(request);
    }

    @PostMapping("/product-view")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public EventAcceptedResponse productView(@Valid @RequestBody ProductViewEventRequest request) {
        return userBehaviorEventService.recordProductView(request);
    }

    @PostMapping("/add-to-cart")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public EventAcceptedResponse addToCart(@Valid @RequestBody AddToCartEventRequest request) {
        return userBehaviorEventService.recordAddToCart(request);
    }

    @PostMapping("/purchase")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public EventAcceptedResponse purchase(@Valid @RequestBody PurchaseEventRequest request) {
        return userBehaviorEventService.recordPurchase(request);
    }
}
