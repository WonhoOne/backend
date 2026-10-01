package com.wonhoone.misterworld.infrastructure.sms.solapi;

import com.wonhoone.misterworld.application.port.*;
import com.sun.net.httpserver.*;
import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(15)
class SolapiSmsSenderTests {
    static final String ACCEPTED = """
            {"failedMessageList":[],"groupInfo":{"count":{"registeredSuccess":1,"registeredFailed":0}},
             "messageList":[{"messageId":"provider-message-1","statusCode":"2000"}]}
            """;
    HttpServer server;
    URI base;
    final AtomicReference<String> body = new AtomicReference<>();
    final AtomicReference<String> path = new AtomicReference<>();
    final AtomicReference<String> method = new AtomicReference<>();
    final AtomicReference<String> auth = new AtomicReference<>();
    final AtomicReference<String> contentType = new AtomicReference<>();
    final CountDownLatch release = new CountDownLatch(1);

    @BeforeEach void startLocalServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        server.start();
    }
    @AfterEach void stopServer() { release.countDown(); server.stop(0); }
    void respond(int status, String response) {
        server.createContext("/messages/v4/send-many/detail", exchange -> {
            record(exchange);
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var stream = exchange.getResponseBody()) { stream.write(bytes); }
        });
    }
    void record(HttpExchange exchange) throws IOException {
        path.set(exchange.getRequestURI().getPath()); method.set(exchange.getRequestMethod());
        auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
        contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
        body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
    }
    SolapiSmsSender sender(Duration timeout) {
        return new SolapiSmsSender(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(),
                base, "010-0000-0000", new SolapiAuthHeaderFactory("dummy-key", "dummy-secret",
                Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC)), timeout);
    }
    SmsMessage message() { return new SmsMessage("010-1234 5678", "[미스터월드] 출발 확정 제주 여행 2026-11-01 ~ 2026-11-05"); }

    @Test void requestUsesEndpointPostHmacJsonNormalizedPhonesAndUnmodifiedText() {
        respond(200, ACCEPTED);
        assertThat(sender(Duration.ofSeconds(3)).send(message()).providerMessageId()).isEqualTo("provider-message-1");
        assertThat(path.get()).isEqualTo("/messages/v4/send-many/detail");
        assertThat(method.get()).isEqualTo("POST");
        assertThat(auth.get()).startsWith("HMAC-SHA256 apiKey=dummy-key, date=").doesNotContain("dummy-secret");
        assertThat(contentType.get()).isEqualTo("application/json");
        var json = JsonMapper.builder().build().readTree(body.get());
        assertThat(json.path("showMessageList").asBoolean()).isTrue();
        assertThat(json.path("messages").size()).isEqualTo(1);
        var sent = json.path("messages").get(0);
        assertThat(sent.path("to").asString()).isEqualTo("01012345678");
        assertThat(sent.path("from").asString()).isEqualTo("01000000000");
        assertThat(sent.path("text").asString()).isEqualTo(message().text());
        assertThat(sent.has("type")).isFalse();
    }
    @ParameterizedTest @ValueSource(ints = {400, 401, 403, 429, 500})
    void non2xxIncludingAuthenticationFailureIsSafeDeliveryFailure(int status) {
        respond(status, "sensitive response dummy-secret 01012345678");
        var error = assertThrows(SmsDeliveryException.class, () -> sender(Duration.ofSeconds(3)).send(message()));
        assertThat(error.category()).isEqualTo(SmsDeliveryException.Category.HTTP_REJECTED);
        assertThat(error.getMessage()).doesNotContain("dummy-secret", "01012345678", "sensitive");
        assertThat(error.getCause()).isNull();
    }
    @Test void failedMessageListIsRejectedEvenWithHttpSuccess() {
        respond(200, ACCEPTED.replace("\"failedMessageList\":[]", "\"failedMessageList\":[{\"statusCode\":\"3040\"}]"));
        assertThat(assertThrows(SmsDeliveryException.class, () -> sender(Duration.ofSeconds(3)).send(message())).category())
                .isEqualTo(SmsDeliveryException.Category.PROVIDER_REJECTED);
    }
    @Test void unexpectedAcceptanceStatusIsRejected() {
        respond(200, ACCEPTED.replace("2000", "3000"));
        assertThat(assertThrows(SmsDeliveryException.class, () -> sender(Duration.ofSeconds(3)).send(message())).category())
                .isEqualTo(SmsDeliveryException.Category.PROVIDER_REJECTED);
    }
    static Stream<String> invalidResponses() {
        return Stream.of("not-json sensitive data", "null", "{}", "{\"failedMessageList\":[]}",
                ACCEPTED.replace("\"registeredSuccess\":1", "\"registeredSuccess\":0"),
                ACCEPTED.replace("\"registeredFailed\":0", "\"registeredFailed\":1"),
                ACCEPTED.replace("\"registeredSuccess\":1", "\"registeredSuccess\":\"1\""),
                ACCEPTED.replace("\"messageId\":\"provider-message-1\"", "\"messageId\":123"),
                ACCEPTED.replace("\"messageId\":\"provider-message-1\"", "\"messageId\":\"\""),
                ACCEPTED.replace("\"statusCode\":\"2000\"", "\"statusCode\":\"2000\",\"to\":\"wrong-target\""),
                ACCEPTED.replace("\"messageList\":[{\"messageId\":\"provider-message-1\",\"statusCode\":\"2000\"}]", "\"messageList\":[]"));
    }
    @ParameterizedTest @MethodSource("invalidResponses")
    void malformedOrUnexpectedResponseIsNeverTreatedAsSent(String response) {
        respond(200, response);
        var error = assertThrows(SmsDeliveryException.class, () -> sender(Duration.ofSeconds(3)).send(message()));
        assertThat(error.category()).isEqualTo(SmsDeliveryException.Category.INVALID_RESPONSE);
        assertThat(error.getMessage()).isEqualTo("INVALID_RESPONSE"); assertThat(error.getCause()).isNull();
    }
    @Test void acceptedResponseWithoutOptionalMessageIdCanStillBeRecorded() {
        respond(200, ACCEPTED.replace("\"messageId\":\"provider-message-1\",", ""));
        assertThat(sender(Duration.ofSeconds(3)).send(message()).providerMessageId()).isNull();
    }
    @Test void networkFailureIsTranslatedWithoutRawCause() {
        server.stop(0);
        var error = assertThrows(SmsDeliveryException.class, () -> sender(Duration.ofSeconds(1)).send(message()));
        assertThat(error.category()).isEqualTo(SmsDeliveryException.Category.NETWORK); assertThat(error.getCause()).isNull();
    }
    @Test void requestTimeoutIsTranslatedWithoutRawCause() {
        server.createContext("/messages/v4/send-many/detail", exchange -> {
            record(exchange);
            try { release.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        var error = assertThrows(SmsDeliveryException.class, () -> sender(Duration.ofMillis(150)).send(message()));
        release.countDown();
        assertThat(error.category()).isEqualTo(SmsDeliveryException.Category.TIMEOUT); assertThat(error.getCause()).isNull();
    }
    @Test void invalidStoredContactIsRetryableFailureWithoutInventingPublicValidation() {
        var error = assertThrows(SmsDeliveryException.class, () -> sender(Duration.ofSeconds(1))
                .send(new SmsMessage("not-a-phone", "confirmation")));
        assertThat(error.category()).isEqualTo(SmsDeliveryException.Category.INVALID_CONTACT);
        assertThat(body.get()).isNull();
    }
    @Test void messageDiagnosticRepresentationRedactsContactAndText() {
        assertThat(message().toString()).isEqualTo("SmsMessage[redacted]");
    }
}
