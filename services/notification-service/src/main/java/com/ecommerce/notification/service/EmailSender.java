package com.ecommerce.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import jakarta.mail.internet.MimeMessage;
import java.util.Map;

/**
 * EmailSender wraps Spring Mail + Thymeleaf template rendering.
 *
 * TWO-STEP PROCESS:
 * 1. Render the HTML template with Thymeleaf, substituting variables
 * 2. Send the rendered HTML via SMTP (Mailhog in dev, SendGrid in prod)
 *
 * Template path: resources/templates/email/{templateId}.html
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailSender {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    public void send(String to, String subject,
                     String templateId, Map<String, String> variables) {
        try {
            Context context = new Context();
            if (variables != null) {
                variables.forEach(context::setVariable);
            }
            String htmlContent = templateEngine.process("email/" + templateId, context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            helper.setFrom("noreply@ecomplatform.com");

            mailSender.send(message);
            log.info("Email sent to={} templateId={}", to, templateId);

        } catch (Exception e) {
            log.error("Email send failed to={} templateId={}", to, templateId, e);
            throw new RuntimeException("Email sending failed: " + e.getMessage(), e);
        }
    }
}
