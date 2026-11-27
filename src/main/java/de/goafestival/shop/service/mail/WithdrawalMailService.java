package de.goafestival.shop.service.mail;

import de.goafestival.shop.domain.WithdrawalRequest;
import de.goafestival.shop.service.branding.Branding;
import de.goafestival.shop.service.branding.GoaWebappBrandingClient;
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

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Sends the two mails around an electronically declared Widerruf (§356a BGB): an immediate
 * confirmation to the customer (with what they submitted, for their own records) and a
 * notification to the shop's contact address so it actually gets handled. Same branded
 * shell/SMTP setup as OrderMailService, kept as its own small service since the content and
 * triggering event are unrelated to an order being placed.
 */
@Service
public class WithdrawalMailService {

    private static final Logger log = LoggerFactory.getLogger(WithdrawalMailService.class);

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm 'Uhr'", Locale.GERMANY).withZone(ZoneId.of("Europe/Berlin"));

    private final JavaMailSender mailSender;
    private final ITemplateEngine templateEngine;
    private final GoaWebappBrandingClient brandingClient;
    private final String fromAddress;
    private final boolean enabled;

    public WithdrawalMailService(JavaMailSender mailSender, ITemplateEngine templateEngine,
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

    public void sendCustomerConfirmation(WithdrawalRequest request) {
        if (!enabled || request.getEmail() == null) {
            return;
        }
        try {
            send(request.getEmail(), "Deine Widerrufserklärung ist bei uns eingegangen",
                    "email/widerruf-kunde-content", request);
        } catch (MessagingException | MailException e) {
            log.warn("Widerrufsbestätigung an {} konnte nicht gesendet werden (Widerruf-ID {})",
                    request.getEmail(), request.getId(), e);
        }
    }

    public void sendMerchantNotification(WithdrawalRequest request, String contactEmail) {
        if (!enabled || contactEmail == null || contactEmail.isBlank()) {
            return;
        }
        try {
            send(contactEmail, "Neue Widerrufserklärung eingegangen",
                    "email/widerruf-shop-content", request);
        } catch (MessagingException | MailException e) {
            log.warn("Widerrufs-Benachrichtigung an {} konnte nicht gesendet werden (Widerruf-ID {})",
                    contactEmail, request.getId(), e);
        }
    }

    private void send(String to, String subject, String contentTemplate, WithdrawalRequest request)
            throws MessagingException {
        Branding branding = brandingClient.current();
        Context contentContext = new Context();
        contentContext.setVariable("customerName", request.getCustomerName());
        contentContext.setVariable("orderReference", request.getOrderReference());
        contentContext.setVariable("email", request.getEmail());
        contentContext.setVariable("note", request.getNote());
        contentContext.setVariable("receivedAtFormatted", TIMESTAMP_FORMAT.format(request.getReceivedAt()));
        String contentHtml = templateEngine.process(contentTemplate, contentContext);

        Context shellContext = new Context();
        shellContext.setVariable("contentHtml", contentHtml);
        shellContext.setVariable("siteTitle", branding.siteTitle());
        shellContext.setVariable("logoUrl", branding.logoImageUrl());
        shellContext.setVariable("backgroundUrl", branding.backgroundImageUrl());
        shellContext.setVariable("colorPrimary", branding.colorPrimary());
        shellContext.setVariable("colorSecondary", branding.colorSecondary());
        shellContext.setVariable("colorAccent", branding.colorAccent());
        // Shared generic branded shell (see OrderMailService) - purely driven by contentHtml,
        // nothing order-specific baked into the layout itself.
        String html = templateEngine.process("email/order-confirmation-email", shellContext);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
        helper.setTo(to);
        helper.setFrom(fromAddress);
        helper.setSubject(subject);
        helper.setText(html, true);
        mailSender.send(message);
    }
}
