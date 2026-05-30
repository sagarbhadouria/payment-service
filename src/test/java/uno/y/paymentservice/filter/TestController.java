package uno.y.paymentservice.filter;

import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {
    @GetMapping("/test/correlation-id")
    public String getCorrelationId() {
        return MDC.get("correlationId");
    }
}