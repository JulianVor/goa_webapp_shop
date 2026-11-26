package de.goafestival.shop.service.mail;

import de.goafestival.shop.service.branding.Branding;
import de.goafestival.shop.service.branding.GoaWebappBrandingClient;
import de.goafestival.shop.service.medusa.dto.MedusaCompleteCartResult;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Sends the order confirmation promised on checkout-success.html, in the same branded
 * layout goa_webapp's newsletter uses (same SMTP settings/provider - e.g. Brevo - just a
 * separate MAIL_FROM so recipients can tell the two senders apart).
 */
@Service
public class OrderMailService {

    private static final Logger log = LoggerFactory.getLogger(OrderMailService.class);

    private final JavaMailSender mailSender;
    private final ITemplateEngine templateEngine;
    private final GoaWebappBrandingClient brandingClient;
    private final String fromAddress;
    private final boolean enabled;

    public OrderMailService(JavaMailSender mailSender, ITemplateEngine templateEngine,
                             GoaWebappBrandingClient brandingClient,
                             @Value("${app.mail.from:}") String configuredFrom,
                             @Value("${spring.mail.username:}") String mailUsername,
                             @Value("${spring.mail.host:}") String mailHost) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.brandingClient = brandingClient;
        this.fromAddress = !configuredFrom.isBlank() ? configuredFrom
                : !mailUsername.isBlank() ? mailUsername
                : "bestellung@goa-festival.de";
        this.enabled = !mailHost.isBlank();
    }

    /**
     * A failure here must never affect the checkout response the customer already saw -
     * Medusa's own order record is the source of truth, this mail is just a courtesy - so
     * any error (including no MAIL_HOST configured at all) is just logged.
     */
    public void sendOrderConfirmation(MedusaCompleteCartResult.Order order) {
        if (!enabled || order == null || order.email() == null) {
            return;
        }
        try {
            Branding branding = brandingClient.current();
            Context context = new Context();
            context.setVariable("displayId", order.display_id());
            context.setVariable("siteTitle", branding.siteTitle());
            context.setVariable("logoUrl", branding.logoImageUrl());
            context.setVariable("colorPrimary", branding.colorPrimary());
            context.setVariable("colorSecondary", branding.colorSecondary());
            context.setVariable("colorAccent", branding.colorAccent());
            String html = templateEngine.process("email/order-confirmation-email", context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setTo(order.email());
            helper.setFrom(fromAddress);
            helper.setSubject("Deine Bestellung Nr. " + order.display_id());
            helper.setText(html, true);
            mailSender.send(message);
        } catch (MessagingException | MailException e) {
            log.warn("Bestellbestätigung an {} konnte nicht gesendet werden (Bestellung {})",
                    order.email(), order.display_id(), e);
        }
    }
}
