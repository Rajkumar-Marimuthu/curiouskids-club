package com.curiouskids.club.support;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/** Reads messages from Mailpit's REST API (https://mailpit.axllent.org/docs/api-v1/). */
public final class Mailpit {

  private final RestClient http;

  Mailpit(String baseUrl) {
    this.http = RestClient.create(baseUrl);
  }

  /** A received message: addresses, subject and both bodies. */
  public record Message(String from, List<String> to, String subject, String html, String text) {}

  /** All messages to this address, oldest first. */
  public List<Message> messagesTo(String address) {
    return summariesTo(address).map(m -> message(m.path("ID").asString())).toList().reversed();
  }

  /** Waits until this address has received {@code count} messages, then returns them. */
  public List<Message> awaitMessagesTo(String address, int count) {
    await().atMost(Duration.ofSeconds(10)).until(() -> summariesTo(address).count() >= count);
    return messagesTo(address);
  }

  private Stream<JsonNode> summariesTo(String address) {
    JsonNode list = http.get().uri("/api/v1/messages?limit=1000").retrieve().body(JsonNode.class);
    return list.path("messages")
        .valueStream()
        .filter(
            m ->
                m.path("To")
                    .valueStream()
                    .anyMatch(to -> to.path("Address").asString().equalsIgnoreCase(address)));
  }

  private Message message(String id) {
    JsonNode m = http.get().uri("/api/v1/message/{id}", id).retrieve().body(JsonNode.class);
    return new Message(
        m.path("From").path("Address").asString(),
        m.path("To").valueStream().map(to -> to.path("Address").asString()).toList(),
        m.path("Subject").asString(),
        m.path("HTML").asString(),
        m.path("Text").asString());
  }
}
