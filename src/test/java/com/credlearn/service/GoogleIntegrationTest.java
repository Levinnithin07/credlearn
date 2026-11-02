package com.credlearn.service;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.credlearn.exception.RuleViolation;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/** All responses here are simulated contracts; these tests do not verify a real Google link. */
class GoogleIntegrationTest {
  private static final String EVENTS =
      "https://www.googleapis.com/calendar/v3/calendars/primary/events";

  @Test
  void calendarCreatesScheduledConferenceAndUsesExistingEventOnRetry() {
    var builder = RestClient.builder();
    var server = MockRestServiceServer.bindTo(builder).build();
    var gateway = new GoogleCalendarGateway(builder.build());
    server
        .expect(requestTo(EVENTS + "/abc123"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withResourceNotFound());
    server
        .expect(requestTo(EVENTS + "?conferenceDataVersion=1&sendUpdates=none"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer test-token"))
        .andExpect(jsonPath("$.id").value("abc123"))
        .andExpect(jsonPath("$.conferenceData.createRequest.requestId").value("request-id"))
        .andExpect(
            jsonPath("$.conferenceData.createRequest.conferenceSolutionKey.type")
                .value("hangoutsMeet"))
        .andExpect(jsonPath("$.start.dateTime").value("2026-10-25T10:00:00Z"))
        .andExpect(jsonPath("$.attendees").doesNotExist())
        .andRespond(
            withSuccess(
                "{\"conferenceData\":{\"createRequest\":{\"status\":{\"statusCode\":\"pending\"}}}}",
                MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(EVENTS + "/abc123"))
        .andRespond(
            withSuccess(
                "{\"conferenceData\":{\"entryPoints\":[{\"entryPointType\":\"video\",\"uri\":\"https://meet.google.com/abc-defg-hij\"}]}}",
                MediaType.APPLICATION_JSON));
    var start = Instant.parse("2026-10-25T10:00:00Z");
    assertThat(
            gateway
                .ensure(
                    "test-token", "abc123", "request-id", "Java", start, start.plusSeconds(3600))
                .status())
        .isEqualTo("PENDING");
    assertThat(
            gateway
                .ensure(
                    "test-token", "abc123", "request-id", "Java", start, start.plusSeconds(3600))
                .status())
        .isEqualTo("READY");
    server.verify();
  }

  @Test
  void calendarNeverAcceptsAnArbitraryLink() {
    var builder = RestClient.builder();
    var server = MockRestServiceServer.bindTo(builder).build();
    var gateway = new GoogleCalendarGateway(builder.build());
    server
        .expect(requestTo(EVENTS + "/abc123"))
        .andRespond(
            withSuccess(
                "{\"conferenceData\":{\"entryPoints\":[{\"entryPointType\":\"video\",\"uri\":\"https://meet.google.com.evil.example/abc-defg-hij\"}]}}",
                MediaType.APPLICATION_JSON));
    var result =
        gateway.ensure(
            "token",
            "abc123",
            "request-id",
            "Java",
            Instant.now(),
            Instant.now().plusSeconds(3600));
    assertThat(result.url()).isNull();
    assertThat(result.status()).isEqualTo("PENDING");
    server.verify();
  }

  @Test
  void oauthUsesPkceMinimalScopeAndRejectsWrongStateAndReplay() {
    var service = new GoogleConnection();
    ReflectionTestUtils.setField(service, "clientId", "test-client");
    ReflectionTestUtils.setField(service, "clientSecret", "test-secret");
    ReflectionTestUtils.setField(service, "callback", "http://127.0.0.1:8080/google/callback");
    var session = new MockHttpSession();
    var uri = service.begin(session, 1L, 12L);
    var query = UriComponentsBuilder.fromUriString(uri).build().getQueryParams();
    assertThat(query.getFirst("code_challenge_method")).isEqualTo("S256");
    assertThat(query.getFirst("code_challenge")).hasSize(43);
    assertThat(
            java.net.URLDecoder.decode(
                query.getFirst("scope"), java.nio.charset.StandardCharsets.UTF_8))
        .isEqualTo("openid https://www.googleapis.com/auth/calendar.events.owned");
    assertThatThrownBy(() -> service.finish(session, 1L, "wrong-state", "code", null))
        .isInstanceOf(RuleViolation.class);
    assertThatThrownBy(() -> service.finish(session, 1L, query.getFirst("state"), "code", null))
        .isInstanceOf(RuleViolation.class);
    assertThat(service.connected(session, 1L)).isFalse();
  }

  @Test
  void oauthRejectsDifferentLocalUserAndExpiredAttempts() {
    var service = new GoogleConnection();
    ReflectionTestUtils.setField(service, "clientId", "client");
    ReflectionTestUtils.setField(service, "clientSecret", "secret");
    ReflectionTestUtils.setField(service, "callback", "http://127.0.0.1:8080/google/callback");
    var session = new MockHttpSession();
    var query =
        UriComponentsBuilder.fromUriString(service.begin(session, 1L, 12L))
            .build()
            .getQueryParams();
    assertThatThrownBy(() -> service.finish(session, 2L, query.getFirst("state"), "code", null))
        .isInstanceOf(RuleViolation.class);
    session.setAttribute(
        "CREDLEARN_GOOGLE_ATTEMPT",
        new GoogleConnection.Attempt(
            1L, 12L, "state", "verifier", Instant.now().minusSeconds(700)));
    assertThatThrownBy(() -> service.finish(session, 1L, "state", "code", null))
        .isInstanceOf(RuleViolation.class);
  }
}
