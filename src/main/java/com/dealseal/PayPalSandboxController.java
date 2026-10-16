package com.dealseal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sandbox-only checkout demonstration. Agreement approval is NOT payment consent.
 * Never expose credentials to browsers or enable live payments with these demo identities.
 */
@RestController
@RequestMapping("/api/paypal/sandbox")
public class PayPalSandboxController {
    public record CreateOrderRequest(UUID agreementId, int version, String amount) {}
    public record OrderView(String orderId, String status, String approvalUrl, String amount, String currency) {}
    private record OrderRecord(UUID agreementId, int version, String amount) {}

    private static final String BASE = "https://api-m.sandbox.paypal.com";
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper;
    private final AgreementApprovalController agreements;
    private final LockedConsentController lockedConsent;
    private final Map<String, OrderRecord> orders = new ConcurrentHashMap<>();

    @Value("${paypal.sandbox.client-id:}")
    private String clientId;
    @Value("${paypal.sandbox.client-secret:}")
    private String clientSecret;

    public PayPalSandboxController(ObjectMapper mapper, AgreementApprovalController agreements, LockedConsentController lockedConsent) {
        this.mapper = mapper;
        this.agreements = agreements;
        this.lockedConsent = lockedConsent;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("configured", configured(), "mode", "sandbox-only", "currency", "USD");
    }

    private boolean configured() {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }

    private String token() throws Exception {
        if (!configured()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Configure paypal.sandbox.client-id and paypal.sandbox.client-secret on the backend");
        String basic = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE + "/v1/oauth2/token"))
                .timeout(Duration.ofSeconds(25))
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials")).build();
        JsonNode result = send(request);
        String token = result.path("access_token").asText();
        if (token.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "PayPal did not return an access token");
        return token;
    }

    private JsonNode send(HttpRequest request) throws Exception {
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "PayPal Sandbox request failed (HTTP " + response.statusCode() + ")");
        return mapper.readTree(response.body());
    }

    private void requireApproved(UUID agreementId, int version) {
        if (agreementId == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agreement ID required");
        var agreement = agreements.read(agreementId);
        if (agreement.version() != version)
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Agreement version changed");
        lockedConsent.requireBoth(agreementId, version);
    }

    @PostMapping("/orders")
    public OrderView create(@RequestBody CreateOrderRequest input) {
        if (input == null || input.amount() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Explicit payment amount required");
        BigDecimal amount;
        try { amount = new BigDecimal(input.amount()).setScale(2, java.math.RoundingMode.UNNECESSARY); }
        catch (NumberFormatException | ArithmeticException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a USD amount with at most two decimals");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0 || amount.compareTo(new BigDecimal("10000.00")) > 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sandbox amount must be between $0.01 and $10,000");
        requireApproved(input.agreementId(), input.version());
        try {
            String value = amount.toPlainString();
            Map<String, Object> payload = Map.of("intent", "CAPTURE", "purchase_units", List.of(Map.of(
                    "reference_id", input.agreementId().toString(),
                    "description", "DealSeal demo payment for agreement version " + input.version(),
                    "amount", Map.of("currency_code", "USD", "value", value))));
            HttpRequest request = HttpRequest.newBuilder(URI.create(BASE + "/v2/checkout/orders"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + token())
                    .header("Content-Type", "application/json")
                    .header("PayPal-Request-Id", UUID.randomUUID().toString())
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload))).build();
            JsonNode result = send(request);
            String id = result.path("id").asText();
            String approvalUrl = "";
            for (JsonNode link : result.path("links")) {
                if (Set.of("approve", "payer-action").contains(link.path("rel").asText())) {
                    approvalUrl = link.path("href").asText();
                    break;
                }
            }
            if (id.isBlank() || !approvalUrl.startsWith("https://www.sandbox.paypal.com/"))
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "PayPal Sandbox did not return an approval URL");
            orders.put(id, new OrderRecord(input.agreementId(), input.version(), value));
            return new OrderView(id, result.path("status").asText(), approvalUrl, value, "USD");
        } catch (ResponseStatusException e) { throw e; }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "PayPal Sandbox order creation failed"); }
    }

    @PostMapping("/orders/{orderId}/capture")
    public OrderView capture(@PathVariable String orderId, @RequestBody(required = false) Map<String, Object> ignored) {
        OrderRecord order = orders.get(orderId);
        if (order == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown demo order");
        requireApproved(order.agreementId(), order.version());
        try {
            // Check the payer actually approved this order; never trust a browser callback alone.
            HttpRequest get = HttpRequest.newBuilder(URI.create(BASE + "/v2/checkout/orders/" +
                    URLEncoder.encode(orderId, StandardCharsets.UTF_8)))
                    .timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + token()).GET().build();
            JsonNode details = send(get);
            if ("COMPLETED".equals(details.path("status").asText()))
                return new OrderView(orderId, "COMPLETED", "", order.amount(), "USD");
            if (!"APPROVED".equals(details.path("status").asText()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Buyer must approve this order in PayPal Sandbox first");
            JsonNode unit = details.path("purchase_units").path(0);
            if (!order.agreementId().toString().equals(unit.path("reference_id").asText()) ||
                    !order.amount().equals(unit.path("amount").path("value").asText()) ||
                    !"USD".equals(unit.path("amount").path("currency_code").asText()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "PayPal order details do not match this agreement");
            HttpRequest capture = HttpRequest.newBuilder(URI.create(BASE + "/v2/checkout/orders/" +
                    URLEncoder.encode(orderId, StandardCharsets.UTF_8) + "/capture"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + token())
                    .header("Content-Type", "application/json")
                    .header("PayPal-Request-Id", UUID.nameUUIDFromBytes(("capture:" + orderId).getBytes(StandardCharsets.UTF_8)).toString())
                    .POST(HttpRequest.BodyPublishers.ofString("{}")).build();
            JsonNode result = send(capture);
            return new OrderView(orderId, result.path("status").asText(), "", order.amount(), "USD");
        } catch (ResponseStatusException e) { throw e; }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "PayPal Sandbox capture failed"); }
    }
}
