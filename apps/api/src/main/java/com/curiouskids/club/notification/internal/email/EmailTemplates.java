package com.curiouskids.club.notification.internal.email;

import com.curiouskids.club.notification.EmailType;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Renders an {@link EmailType} from {@code templates/email/<type>.html} and {@code .txt}. Values
 * are escaped in HTML. A {@code path} payload value becomes {@code link}, a full URL on the web
 * app.
 */
@Component
public class EmailTemplates {

  private static final Locale EN_GB = Locale.UK;

  private final TemplateEngine html = engine(TemplateMode.HTML, ".html");
  private final TemplateEngine text = engine(TemplateMode.TEXT, ".txt");
  private final String appBaseUrl;

  EmailTemplates(NotificationProperties properties) {
    this.appBaseUrl = properties.appBaseUrl().toString().replaceAll("/+$", "");
  }

  public RenderedEmail render(EmailType type, String to, Map<String, String> payload) {
    Map<String, Object> variables = new HashMap<>(payload);
    String subject = subject(type);
    variables.put("subject", subject);
    if (payload.containsKey("path")) {
      variables.put("link", appBaseUrl + payload.get("path"));
    }
    Context context = new Context(EN_GB, variables);
    String name = "email/" + type.name().toLowerCase(Locale.ROOT).replace('_', '-');
    return new RenderedEmail(to, subject, html.process(name, context), text.process(name, context));
  }

  private static String subject(EmailType type) {
    return switch (type) {
      case VERIFY_EMAIL -> "Please confirm your email for Curiouskids Club";
      case PASSWORD_RESET -> "Reset your Curiouskids Club password";
      case STAFF_INVITATION -> "You're invited to help run Curiouskids Club";
    };
  }

  private static TemplateEngine engine(TemplateMode mode, String suffix) {
    ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix("templates/");
    resolver.setSuffix(suffix);
    resolver.setTemplateMode(mode);
    resolver.setCharacterEncoding("UTF-8");
    resolver.setCacheable(true);
    TemplateEngine engine = new TemplateEngine();
    engine.setTemplateResolver(resolver);
    return engine;
  }
}
