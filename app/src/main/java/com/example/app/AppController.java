package com.example.app;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.apache.http.client.fluent.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

@RestController
public class AppController {

    private static final Logger logger = LoggerFactory.getLogger(AppController.class);
    private static final Random RANDOM = new Random();

    private final PeanutsService service;

    public AppController(PeanutsService service) {
        this.service = service;
    }

    @GetMapping("/")
    public String root(@RequestParam(value = "name", defaultValue = "World") String name,
                       @RequestHeader HttpHeaders headers) {
        logger.debug("Request headers: {}", headers);
        logger.info("Hello {}!!", name);
        return String.format("Hello %s!!", name);
    }

    @GetMapping("/io_task")
    public String ioTask() throws InterruptedException {
        Thread.sleep(1000);
        logger.info("io_task");
        return "io_task";
    }

    @GetMapping("/cpu_task")
    public String cpuTask() {
        for (int i = 0; i < 100; i++) {
            int tmp = i * i * i;
        }
        logger.info("cpu_task");
        return "cpu_task";
    }

    @GetMapping("/random_sleep")
    public String randomSleep() throws InterruptedException {
        Thread.sleep((int) (Math.random() / 5 * 10000));
        logger.info("random_sleep");
        return "random_sleep";
    }

    @GetMapping("/random_status")
    public String randomStatus(HttpServletResponse response) {
        List<Integer> statusCodes = Arrays.asList(200, 200, 300, 400, 500);
        Random rand = new Random();
        int randomStatus = statusCodes.get(rand.nextInt(statusCodes.size()));
        response.setStatus(randomStatus);
        logger.info("random_status: {}", randomStatus);
        return "random_status";
    }

    @GetMapping("/chain")
    public String chain() throws IOException {
        String targetOneHost = System.getenv().getOrDefault("TARGET_ONE_HOST", "localhost");
        String targetTwoHost = System.getenv().getOrDefault("TARGET_TWO_HOST", "localhost");
        logger.debug("chain is starting");
        Request.Get("http://localhost:8080/")
                .execute().returnContent();
        Request.Get(String.format("http://%s:8080/io_task", targetOneHost))
                .execute().returnContent();
        Request.Get(String.format("http://%s:8080/cpu_task", targetTwoHost))
                .execute().returnContent();
        logger.debug("chain is finished");
        return "chain";
    }

    @GetMapping("/error_test")
    public String errorTest() throws Exception {
        throw new Exception("Error test");
    }

    /**
     * Simulates a payment endpoint with random failures for alerting demos.
     * - 70% success (200)
     * - 10% bad request (400) - invalid payment data
     * - 10% payment declined (402)
     * - 5% timeout (503) - upstream timeout
     * - 5% internal error (500)
     */
    @PostMapping("/payment")
    public ResponseEntity<Map<String, Object>> processPayment(
            @RequestParam(defaultValue = "100.00") double amount) {

        // Simulate processing time (50-500ms)
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(50, 500));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        int outcome = RANDOM.nextInt(100);

        if (outcome < 70) {
            // Success
            logger.info("Payment processed successfully: amount={}", amount);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "transactionId", "TXN-" + System.currentTimeMillis(),
                    "amount", amount
            ));
        } else if (outcome < 80) {
            // Bad request
            logger.warn("Payment failed - invalid data: amount={}", amount);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payment data");
        } else if (outcome < 90) {
            // Payment declined
            logger.warn("Payment declined: amount={}", amount);
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "Payment declined by issuer");
        } else if (outcome < 95) {
            // Timeout/Service unavailable
            logger.error("Payment gateway timeout: amount={}", amount);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Payment gateway timeout");
        } else {
            // Internal error
            logger.error("Payment processing error: amount={}", amount);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Internal payment error");
        }
    }

    /**
     * Health check endpoint that occasionally degrades.
     * Useful for testing alerts on service health.
     */
    @GetMapping("/health/payment-gateway")
    public ResponseEntity<Map<String, Object>> paymentGatewayHealth() {
        // 90% healthy, 10% degraded
        if (RANDOM.nextInt(100) < 90) {
            return ResponseEntity.ok(Map.of(
                    "status", "healthy",
                    "latency_ms", ThreadLocalRandom.current().nextInt(10, 50)
            ));
        } else {
            logger.warn("Payment gateway health check degraded");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "status", "degraded",
                            "latency_ms", ThreadLocalRandom.current().nextInt(1000, 5000)
                    ));
        }
    }

    @GetMapping("/peanuts/{id}")
    public Peanuts getPeanutsById(@PathVariable Long id) {
        logger.info("Get Peanuts Character by id: {}", id);
        return service.getPeanutsById(id);
    }

    @PostMapping("/peanuts")
    public Peanuts savePeanuts(@Valid @RequestBody Peanuts peanuts) {
        logger.info("Create Peanuts Character: {}", peanuts.getName());
        return service.savePeanuts(peanuts);
    }
}
