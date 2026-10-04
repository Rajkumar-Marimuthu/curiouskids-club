package com.curiouskids.club.notification.internal.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

/** SES is an external system, so it is mocked here; SMTP is tested against a real Mailpit. */
class SesEmailSenderTest {

  @Test
  @DisplayName("FR-NOT-01: SES receives the subject, HTML body and text alternative")
  void sendsBothParts() {
    SesV2Client ses = mock(SesV2Client.class);
    ArgumentCaptor<SendEmailRequest> request = ArgumentCaptor.forClass(SendEmailRequest.class);

    new SesEmailSender(ses, "Club <club@example.org>")
        .send(new RenderedEmail("a@example.com", "Hello", "<p>Hi</p>", "Hi"));

    verify(ses).sendEmail(request.capture());
    assertThat(request.getValue().fromEmailAddress()).isEqualTo("Club <club@example.org>");
    assertThat(request.getValue().destination().toAddresses()).containsExactly("a@example.com");
    Message message = request.getValue().content().simple();
    assertThat(message.subject().data()).isEqualTo("Hello");
    assertThat(message.body().html().data()).isEqualTo("<p>Hi</p>");
    assertThat(message.body().text().data()).isEqualTo("Hi");
    assertThat(message.body().text().charset()).isEqualTo("UTF-8");
  }

  @Test
  @DisplayName("FR-NOT-03: an SES error reaches the outbox so it can retry")
  void errorsPropagate() {
    SesV2Client ses = mock(SesV2Client.class);
    when(ses.sendEmail(any(SendEmailRequest.class)))
        .thenThrow(new IllegalStateException("throttled"));

    assertThatThrownBy(
            () ->
                new SesEmailSender(ses, "club@example.org")
                    .send(new RenderedEmail("a@example.com", "s", "h", "t")))
        .isInstanceOf(IllegalStateException.class);
  }
}
