package com.wonhoone.misterworld.infrastructure.sms.solapi;

import com.wonhoone.misterworld.application.port.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static com.wonhoone.misterworld.application.port.SmsDeliveryException.Category.*;

/** Backend-local SOLAPI REST adapter. No response bodies, request data or credentials enter exceptions/logs. */
public final class SolapiSmsSender implements SmsSender {
    private final HttpClient http;
    private final URI endpoint;
    private final String senderNumber;
    private final SolapiAuthHeaderFactory authorization;
    private final Duration timeout;
    private final JsonMapper json = JsonMapper.builder().build();

    public SolapiSmsSender(HttpClient http, URI baseUrl, String senderNumber,
                           SolapiAuthHeaderFactory authorization, Duration timeout) {
        this.http = http; this.endpoint = baseUrl.resolve("/messages/v4/send-many/detail");
        this.senderNumber = normalize(senderNumber); this.authorization = authorization; this.timeout = timeout;
    }
    @Override public SmsSendResult send(SmsMessage message) {
        String to = normalize(message.to());
        String body = json.writeValueAsString(Map.of("messages", List.of(
                Map.of("to", to, "from", senderNumber, "text", message.text())), "showMessageList", true));
        var request = HttpRequest.newBuilder(endpoint).timeout(timeout)
                .header("Authorization", authorization.create()).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> response;
        try { response = http.send(request, HttpResponse.BodyHandlers.ofString()); }
        catch (HttpTimeoutException failure) { throw new SmsDeliveryException(TIMEOUT); }
        catch (IOException failure) { throw new SmsDeliveryException(NETWORK); }
        catch (InterruptedException failure) {
            Thread.currentThread().interrupt(); throw new SmsDeliveryException(NETWORK);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw new SmsDeliveryException(HTTP_REJECTED);
        try { return accepted(json.readTree(response.body()), to); }
        catch (SmsDeliveryException safe) { throw safe; }
        catch (RuntimeException failure) { throw new SmsDeliveryException(INVALID_RESPONSE); }
    }
    private SmsSendResult accepted(JsonNode response, String requestedTo) {
        var failed = response.path("failedMessageList");
        if (!failed.isArray()) throw new SmsDeliveryException(INVALID_RESPONSE);
        if (!failed.isEmpty()) throw new SmsDeliveryException(PROVIDER_REJECTED);
        var count = response.path("groupInfo").path("count");
        if (!count.path("registeredSuccess").isIntegralNumber() || count.path("registeredSuccess").asLong() != 1
                || !count.path("registeredFailed").isIntegralNumber() || count.path("registeredFailed").asLong() != 0)
            throw new SmsDeliveryException(INVALID_RESPONSE);
        // send-many/detail's messageList is an array, unlike GET /messages/v4/list's keyed map.
        var messages = response.path("messageList");
        if (!messages.isArray() || messages.size() != 1) throw new SmsDeliveryException(INVALID_RESPONSE);
        var accepted = messages.get(0);
        if (!"2000".equals(accepted.path("statusCode").asString())) throw new SmsDeliveryException(PROVIDER_REJECTED);
        if (accepted.has("to") && !requestedTo.equals(accepted.path("to").asString()))
            throw new SmsDeliveryException(INVALID_RESPONSE);
        var id = accepted.path("messageId");
        if (!id.isMissingNode() && !id.isNull() && (!id.isString() || id.asString().isBlank() || id.asString().length() > 255))
            throw new SmsDeliveryException(INVALID_RESPONSE);
        return new SmsSendResult(id.isString() ? id.asString() : null);
    }
    static String normalize(String contact) {
        String digits = contact.replaceAll("[\\s().-]", "");
        if (!digits.matches("[0-9]+")) throw new SmsDeliveryException(INVALID_CONTACT);
        return digits;
    }
}
