package com.curiouskids.club.notification.internal.email;

import java.nio.charset.StandardCharsets;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

/**
 * Sends through Amazon SES. The region comes from {@code AWS_REGION} and credentials from the ECS
 * task role, both through the AWS SDK's default providers.
 */
class SesEmailSender implements EmailSender {

  private static final String UTF_8 = StandardCharsets.UTF_8.name();

  private final SesV2Client ses;
  private final String from;

  SesEmailSender(SesV2Client ses, String from) {
    this.ses = ses;
    this.from = from;
  }

  @Override
  public void send(RenderedEmail email) {
    ses.sendEmail(
        SendEmailRequest.builder()
            .fromEmailAddress(from)
            .destination(d -> d.toAddresses(email.to()))
            .content(
                c ->
                    c.simple(
                        m ->
                            m.subject(s -> s.data(email.subject()).charset(UTF_8))
                                .body(
                                    b ->
                                        b.text(t -> t.data(email.text()).charset(UTF_8))
                                            .html(h -> h.data(email.html()).charset(UTF_8)))))
            .build());
  }
}
