package com.credlearn.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

@Component
public class GoogleCalendarGateway {
  private static final String EVENTS =
      "https://www.googleapis.com/calendar/v3/calendars/primary/events";
  private final RestClient http;

  public GoogleCalendarGateway() {
    this(GoogleConnection.httpClient());
  }

  GoogleCalendarGateway(RestClient http) {
    this.http = http;
  }

  public record Result(String status, String url, String error) {}

  public Result ensure(
      String token, String eventId, String requestId, String title, Instant start, Instant end) {
    JsonNode event;
    try {
      event = get(token, eventId);
    } catch (HttpClientErrorException.NotFound absent) {
      try {
        event =
            http.post()
                .uri(EVENTS + "?conferenceDataVersion=1&sendUpdates=none")
                .headers(h -> h.setBearerAuth(token))
                .body(
                    Map.of(
                        "id",
                        eventId,
                        "summary",
                        "CredLearn · " + title,
                        "description",
                        "Peer learning session arranged through CredLearn.",
                        "start",
                        Map.of("dateTime", start.toString(), "timeZone", "Asia/Kolkata"),
                        "end",
                        Map.of("dateTime", end.toString(), "timeZone", "Asia/Kolkata"),
                        "conferenceData",
                        Map.of(
                            "createRequest",
                            Map.of(
                                "requestId",
                                requestId,
                                "conferenceSolutionKey",
                                Map.of("type", "hangoutsMeet")))))
                .retrieve()
                .body(JsonNode.class);
      } catch (HttpClientErrorException.Conflict duplicate) {
        event = get(token, eventId);
      }
    }
    if (event == null || "cancelled".equals(event.path("status").asText()))
      return new Result(
          "ERROR", null, "The Google event is unavailable. Check the organizer’s Google Calendar.");
    var conference = event.path("conferenceData");
    if ("failure"
        .equals(conference.path("createRequest").path("status").path("statusCode").asText()))
      return new Result(
          "ERROR",
          null,
          "Google could not create conferencing for this event. Check your Google account’s Meet"
              + " eligibility.");
    for (var entry : conference.path("entryPoints")) {
      var url = entry.path("uri").asText();
      if ("video".equals(entry.path("entryPointType").asText())
          && url.matches("https://meet\\.google\\.com/[a-z]{3}-[a-z]{4}-[a-z]{3}"))
        return new Result("READY", url, "");
    }
    return new Result(
        "PENDING", null, "Google is preparing the conference. Check again in a moment.");
  }

  private JsonNode get(String token, String id) {
    return http.get()
        .uri(EVENTS + "/" + id)
        .headers(h -> h.setBearerAuth(token))
        .retrieve()
        .body(JsonNode.class);
  }
}
