package uno.y.paymentservice.exception;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ErrorDetails> handleIdempotencyConflict(IdempotencyConflictException ex, WebRequest request) {
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                ex.getMessage(),
                request.getDescription(false)
        );
        return new ResponseEntity<>(errorDetails, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ErrorDetails> handlePaymentNotFound(PaymentNotFoundException ex, WebRequest request) {
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                ex.getMessage(),
                request.getDescription(false)
        );
        return new ResponseEntity<>(errorDetails, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(PaymentProviderException.class)
    public ResponseEntity<ErrorDetails> handlePaymentProvider(PaymentProviderException ex, WebRequest request) {
        // Optionally include provider name in message
        String message = String.format("Provider %s error: %s", ex.getProviderName(), ex.getMessage());
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                message,
                request.getDescription(false)
        );
        // 502 Bad Gateway is appropriate for downstream provider failure
        return new ResponseEntity<>(errorDetails, HttpStatus.BAD_GATEWAY);
    }

    @ExceptionHandler(UnsupportedPaymentMethodException.class)
    public ResponseEntity<ErrorDetails> handleUnsupportedPaymentMethod(UnsupportedPaymentMethodException ex, WebRequest request) {
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                ex.getMessage(),
                request.getDescription(false)
        );
        return new ResponseEntity<>(errorDetails, HttpStatus.BAD_REQUEST);
    }

    // Handle validation errors (MethodArgumentNotValidException)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorDetails> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, WebRequest request) {
        String errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                "Validation Failed: " + errors,
                request.getDescription(false)
        );
        return new ResponseEntity<>(errorDetails, HttpStatus.BAD_REQUEST);
    }

    // Handle IOExceptions
    @ExceptionHandler(IOException.class)
    public ResponseEntity<ErrorDetails> handleIOException(IOException ex, WebRequest request) {
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                "I/O error: " + ex.getMessage(),
                request.getDescription(false)
        );
        return new ResponseEntity<>(errorDetails, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // Handle ConstraintViolationException
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorDetails> handleConstraintViolationException(ConstraintViolationException ex, WebRequest request) {
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                "Constraint violation: " + ex.getMessage(),
                request.getDescription(false)
        );
        return new ResponseEntity<>(errorDetails, HttpStatus.BAD_REQUEST);
    }

    // Handle MissingRequestHeaderException
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorDetails> handleMissingRequestHeaderException(MissingRequestHeaderException ex, WebRequest request) {
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                "Missing request header: " + ex.getHeaderName(),
                request.getDescription(false)
        );
        return new ResponseEntity<>(errorDetails, HttpStatus.BAD_REQUEST);
    }

    // Handle HttpMessageNotReadableException
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorDetails> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex, WebRequest request) {
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                "Malformed JSON request",
                request.getDescription(false)
        );
        return new ResponseEntity<>(errorDetails, HttpStatus.BAD_REQUEST);
    }

    // Catch-all handler for any other exceptions
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDetails> handleAllExceptions(Exception ex, WebRequest request) {
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                "An unexpected internal server error occurred",
                request.getDescription(false)
        );
        return new ResponseEntity<>(errorDetails, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
