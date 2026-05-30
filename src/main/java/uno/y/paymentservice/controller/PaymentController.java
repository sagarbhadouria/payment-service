package uno.y.paymentservice.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import uno.y.paymentservice.dto.request.CreatePaymentRequest;
import uno.y.paymentservice.dto.response.CreatePaymentResponse;
import uno.y.paymentservice.dto.response.FetchPaymentResponse;
import uno.y.paymentservice.service.PaymentService;

@RestController
@RequestMapping("/v1")
@Validated
public class PaymentController {
    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * Fetches payment details by payment ID.
     */
    @GetMapping("/payments/{paymentId}")
    public ResponseEntity<FetchPaymentResponse> fetchPaymentById(@PathVariable String paymentId) {
        FetchPaymentResponse response = paymentService.getPaymentById(paymentId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * Creates a new payment.
     */
    @PostMapping("/payments")
    public ResponseEntity<CreatePaymentResponse> createPayment(@Valid @RequestBody CreatePaymentRequest paymentRequest,
                                                               @RequestHeader("Idempotency-Key")
                                                               @NotBlank(message = "Idempotency-Key header is required")
                                                               String idempotencyKey) {
        CreatePaymentResponse payment = paymentService.createPayment(paymentRequest, idempotencyKey);
        return new ResponseEntity<>(payment, HttpStatus.CREATED);
    }
}
