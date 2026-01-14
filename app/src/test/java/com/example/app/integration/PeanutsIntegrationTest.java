package com.example.app.integration;

import com.example.app.Peanuts;
import com.example.app.PeanutsRepository;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests using Testcontainers for real Postgres and Redis instances.
 * These tests verify the full application stack including database persistence and caching.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class PeanutsIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Container
    static RedisContainer redis = new RedisContainer("redis:7-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        
        // Redis
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.cache.type", () -> "redis");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PeanutsRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Nested
    @DisplayName("Peanuts CRUD Operations")
    class PeanutsCrudOperations {

        @Test
        @DisplayName("create and retrieve peanuts character")
        void createAndRetrievePeanuts() {
            // Create
            Peanuts snoopy = new Peanuts();
            snoopy.setName("Snoopy");
            snoopy.setDescription("Charlie Brown's pet beagle");

            ResponseEntity<Peanuts> createResponse = restTemplate.postForEntity(
                    "/peanuts", snoopy, Peanuts.class);

            assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(createResponse.getBody()).isNotNull();
            assertThat(createResponse.getBody().getId()).isNotNull();
            assertThat(createResponse.getBody().getName()).isEqualTo("Snoopy");

            Long id = createResponse.getBody().getId();

            // Retrieve
            ResponseEntity<Peanuts> getResponse = restTemplate.getForEntity(
                    "/peanuts/" + id, Peanuts.class);

            assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(getResponse.getBody()).isNotNull();
            assertThat(getResponse.getBody().getName()).isEqualTo("Snoopy");
            assertThat(getResponse.getBody().getDescription()).isEqualTo("Charlie Brown's pet beagle");
        }

        @Test
        @DisplayName("retrieve non-existent peanuts returns empty")
        void retrieveNonExistentPeanuts() {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    "/peanuts/99999", String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNullOrEmpty();
        }

        @Test
        @DisplayName("create multiple peanuts characters")
        void createMultiplePeanuts() {
            // Create Charlie Brown
            Peanuts charlie = new Peanuts();
            charlie.setName("Charlie Brown");
            charlie.setDescription("The main character");
            ResponseEntity<Peanuts> charlieResponse = restTemplate.postForEntity(
                    "/peanuts", charlie, Peanuts.class);

            // Create Lucy
            Peanuts lucy = new Peanuts();
            lucy.setName("Lucy");
            lucy.setDescription("Bossy and opinionated");
            ResponseEntity<Peanuts> lucyResponse = restTemplate.postForEntity(
                    "/peanuts", lucy, Peanuts.class);

            // Create Linus
            Peanuts linus = new Peanuts();
            linus.setName("Linus");
            linus.setDescription("Lucy's younger brother");
            ResponseEntity<Peanuts> linusResponse = restTemplate.postForEntity(
                    "/peanuts", linus, Peanuts.class);

            assertThat(charlieResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(lucyResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(linusResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // Verify count in database
            assertThat(repository.count()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Root Endpoint")
    class RootEndpoint {

        @Test
        @DisplayName("returns greeting message")
        void returnsGreeting() {
            ResponseEntity<String> response = restTemplate.getForEntity("/", String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).contains("Hello");
        }

        @Test
        @DisplayName("returns personalized greeting")
        void returnsPersonalizedGreeting() {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    "/?name=Snoopy", String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo("Hello Snoopy!!");
        }
    }

    @Nested
    @DisplayName("Health Endpoints")
    class HealthEndpoints {

        @Test
        @DisplayName("actuator health endpoint returns OK")
        void actuatorHealthReturnsOk() {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    "/actuator/health", Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).containsKey("status");
        }

        @Test
        @DisplayName("prometheus metrics endpoint is available")
        void prometheusMetricsAvailable() {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    "/actuator/prometheus", String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).contains("jvm_");
        }
    }

    @Nested
    @DisplayName("Task Endpoints")
    class TaskEndpoints {

        @Test
        @DisplayName("cpu_task endpoint returns successfully")
        void cpuTaskReturns() {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    "/cpu_task", String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo("cpu_task");
        }

        @Test
        @DisplayName("random_status endpoint returns some status")
        void randomStatusReturns() {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    "/random_status", String.class);

            // Random status can be 200, 300, 400, or 500
            assertThat(response.getStatusCode().value()).isIn(200, 300, 400, 500);
        }
    }

    @Nested
    @DisplayName("Payment Endpoints")
    class PaymentEndpoints {

        @Test
        @DisplayName("payment endpoint accepts requests")
        void paymentEndpointAcceptsRequests() {
            // Payment has random outcomes, we just verify it responds
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    "/payment?amount=99.99", null, Map.class);

            // Should be one of the expected statuses
            assertThat(response.getStatusCode().value())
                    .isIn(200, 400, 402, 500, 503);
        }

        @Test
        @DisplayName("payment gateway health check responds")
        void paymentGatewayHealthCheck() {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    "/health/payment-gateway", Map.class);

            assertThat(response.getStatusCode().value()).isIn(200, 503);
            assertThat(response.getBody()).containsKeys("status", "latency_ms");
        }
    }

    @Nested
    @DisplayName("Caching Behavior")
    class CachingBehavior {

        @Test
        @DisplayName("retrieved peanuts is cached")
        void retrievedPeanutsIsCached() {
            // Create a peanuts character
            Peanuts woodstock = new Peanuts();
            woodstock.setName("Woodstock");
            woodstock.setDescription("Snoopy's best friend");

            ResponseEntity<Peanuts> createResponse = restTemplate.postForEntity(
                    "/peanuts", woodstock, Peanuts.class);
            Long id = createResponse.getBody().getId();

            // First retrieval - should hit database
            ResponseEntity<Peanuts> firstGet = restTemplate.getForEntity(
                    "/peanuts/" + id, Peanuts.class);
            assertThat(firstGet.getBody().getName()).isEqualTo("Woodstock");

            // Second retrieval - should hit cache
            ResponseEntity<Peanuts> secondGet = restTemplate.getForEntity(
                    "/peanuts/" + id, Peanuts.class);
            assertThat(secondGet.getBody().getName()).isEqualTo("Woodstock");

            // Both should return the same data
            assertThat(firstGet.getBody().getId()).isEqualTo(secondGet.getBody().getId());
        }
    }
}
