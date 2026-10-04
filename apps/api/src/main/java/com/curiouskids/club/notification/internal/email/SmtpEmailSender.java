package com.curiouskids.club.notification.internal.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/** Sends through SMTP; locally that is Mailpit (http://localhost:8025). */
class SmtpEmailSender implements EmailSender {

  private final JavaMailSender mail;
  private final String from;

  SmtpEmailSender(JavaMailSender mail, String from) {
    this.mail = mail;
    this.from = from;
  }

  @Override
  public void send(RenderedEmail email) {
    MimeMessage message = mail.createMimeMessage();
    try {
      MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
      helper.setFrom(from);
      helper.setTo(email.to());
      helper.setSubject(email.subject());
      helper.setText(email.text(), email.html());
    } catch (MessagingException e) {
      throw new MailPreparationException(e);
    }
    mail.send(message);
  }
}
