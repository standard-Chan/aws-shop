package jeong.awsshop.eventpipeline.productranking.presentation;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RootHealthCheckController {

    @GetMapping("/")
    public ResponseEntity<Void> healthCheck() {
        return ResponseEntity.ok().build();
    }
}
