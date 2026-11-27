package de.goafestival.shop.service.mail;

import de.goafestival.shop.domain.ShopSettings;
import de.goafestival.shop.service.ShopSettingsService;
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

import java.math.BigDecimal;
import java.util.List;

/**
 * Sends the order confirmation promised on checkout-success.html, in the same branded
 * layout goa_webapp's newsletter uses (same SMTP settings/provider - e.g. Brevo - just a
 * separate MAIL_FROM so recipients can tell the two senders apart). Subject/body are
 * admin-editable (see /admin/bestellbestaetigung), same pattern as goa_webapp's
 * newsletter confirmation mail.
 */
@Service
public class OrderMailService {

    private static final Logger log = LoggerFactory.getLogger(OrderMailService.class);

    /** The literal tokens admins type into the body/subject, replaced before sending. */
    private static final String ORDER_NUMBER_PLACEHOLDER = "{{bestellnummer}}";
    private static final String ORDER_ITEMS_PLACEHOLDER = "{{bestellungen}}";

    private static final String DEFAULT_SUBJECT = "Deine Bestellung Nr. " + ORDER_NUMBER_PLACEHOLDER;
    private static final String DEFAULT_BODY =
            "<p>Hallo,</p><p>danke für deine Bestellung! Wir haben sie erhalten und bereiten sie vor.</p>"
                    + "<p>Deine Bestellnummer: <strong>" + ORDER_NUMBER_PLACEHOLDER + "</strong></p>"
                    + ORDER_ITEMS_PLACEHOLDER
                    + "<p>Bei Fragen antworte einfach auf diese E-Mail.</p>";

    private final JavaMailSender mailSender;
    private final ITemplateEngine templateEngine;
    private final GoaWebappBrandingClient brandingClient;
    private final ShopSettingsService shopSettingsService;
    private final String fromAddress;
    private final String mailHost;
    private final String shopBaseUrl;
    private final boolean enabled;

    public OrderMailService(JavaMailSender mailSender, ITemplateEngine templateEngine,
                             GoaWebappBrandingClient brandingClient, ShopSettingsService shopSettingsService,
                             @Value("${app.mail.from:}") String configuredFrom,
                             @Value("${spring.mail.username:}") String mailUsername,
                             @Value("${spring.mail.host:}") String mailHost,
                             @Value("${app.shop.base-url:}") String shopBaseUrl) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.brandingClient = brandingClient;
        this.shopSettingsService = shopSettingsService;
        this.fromAddress = !configuredFrom.isBlank() ? configuredFrom
                : !mailUsername.isBlank() ? mailUsername
                : "bestellung@goa-festival.de";
        this.mailHost = mailHost;
        this.shopBaseUrl = shopBaseUrl;
        this.enabled = !mailHost.isBlank();
    }

    /** Whether an SMTP server is configured at all - sendOrderConfirmation is a no-op otherwise. */
    public boolean isEnabled() {
        return enabled;
    }

    /** The admin-configured subject/body, or a sensible default if nothing was saved yet. */
    public String getConfirmationSubject() {
        String subject = shopSettingsService.get().getOrderConfirmationSubject();
        return (subject != null && !subject.isBlank()) ? subject : DEFAULT_SUBJECT;
    }

    public String getConfirmationBody() {
        String body = shopSettingsService.get().getOrderConfirmationBody();
        return (body != null && !body.isBlank()) ? body : DEFAULT_BODY;
    }

    public void updateConfirmationTemplate(String subject, String body) {
        shopSettingsService.updateOrderConfirmationTemplate(subject, body);
    }

    /**
     * Renders the exact branded HTML a real send would produce, for the admin's preview step -
     * with a made-up order (number, one example item) standing in for the real thing.
     */
    public String previewHtml(String htmlBody) {
        Branding branding = brandingClient.current();
        String content = htmlBody
                .replace(ORDER_NUMBER_PLACEHOLDER, "1001")
                .replace(ORDER_ITEMS_PLACEHOLDER, itemsTableHtml(sampleOrder(), branding))
                + legalFooterHtml();
        return renderEmailHtml(content, branding);
    }

    /**
     * Sends a made-up order confirmation to fromAddress (MAIL_FROM, or MAIL_USERNAME if that's
     * blank) - unlike sendOrderConfirmation, this is a deliberate SMTP diagnostic: it never
     * swallows a failure. The admin sees the exact underlying error (e.g. Brevo's own "535
     * Authentication failed") on the page, and the full exception (with stack trace) is logged
     * at ERROR for anyone checking the server logs afterwards.
     *
     * @return the address the test mail was sent to
     * @throws IllegalStateException no SMTP server or no recipient address is configured
     * @throws TestMailException     sending itself failed - getMessage() is the root cause's
     *                                message, suitable for showing directly in the admin UI
     */
    public String sendTestMail() {
        if (!enabled) {
            throw new IllegalStateException("Kein Mailserver konfiguriert - MAIL_HOST ist leer.");
        }
        try {
            Branding branding = brandingClient.current();
            MedusaCompleteCartResult.Order sample = sampleOrder();
            String subject = getConfirmationSubject().replace(ORDER_NUMBER_PLACEHOLDER, "TEST-1001") + " (Testmail)";
            String content = getConfirmationBody()
                    .replace(ORDER_NUMBER_PLACEHOLDER, "TEST-1001")
                    .replace(ORDER_ITEMS_PLACEHOLDER, itemsTableHtml(sample, branding))
                    + legalFooterHtml();
            String html = renderEmailHtml(content, branding);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setTo(fromAddress);
            helper.setFrom(fromAddress);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            return fromAddress;
        } catch (MessagingException | MailException e) {
            log.error("Test-Mail an {} konnte nicht gesendet werden (MAIL_HOST={})", fromAddress, mailHost, e);
            throw new TestMailException(fromAddress, rootMessage(e));
        }
    }

    /** Thrown only by sendTestMail() - see its own javadoc for why this one doesn't just log and move on. */
    public static class TestMailException extends RuntimeException {
        private final String recipient;

        public TestMailException(String recipient, String message) {
            super(message);
            this.recipient = recipient;
        }

        public String getRecipient() {
            return recipient;
        }
    }

    /** The deepest cause's message - e.g. the SMTP server's own rejection reason, not Spring's wrapper text around it. */
    private static String rootMessage(Throwable e) {
        Throwable current = e;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() != null ? current.getMessage() : current.toString();
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
            String orderNumber = String.valueOf(order.display_id());
            String subject = getConfirmationSubject().replace(ORDER_NUMBER_PLACEHOLDER, orderNumber);
            String content = getConfirmationBody()
                    .replace(ORDER_NUMBER_PLACEHOLDER, orderNumber)
                    .replace(ORDER_ITEMS_PLACEHOLDER, itemsTableHtml(order, branding))
                    + legalFooterHtml();
            String html = renderEmailHtml(content, branding);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setTo(order.email());
            helper.setFrom(fromAddress);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (MessagingException | MailException e) {
            log.warn("Bestellbestätigung an {} konnte nicht gesendet werden (Bestellung {})",
                    order.email(), order.display_id(), e);
        }
    }

    /**
     * Fixed, non-admin-editable block appended after the admin's own body text: seller info,
     * full AGB/Widerrufsbelehrung text (not just a link - both are only linked to from the
     * admin-edited body otherwise), the Muster-Widerrufsformular, and the legal-text version
     * each refers to (see ShopSettingsService's version bump on save) so it's traceable which
     * fassung applied at order time. Deliberately built here rather than left for the admin to
     * paste in - it must survive even if the admin never updates the body text again.
     */
    private String legalFooterHtml() {
        ShopSettings settings = shopSettingsService.get();
        Context context = new Context();
        context.setVariable("paymentStatusText", "Zahlung erhalten.");
        context.setVariable("companyName", settings.getCompanyName());
        context.setVariable("companyStreet", settings.getCompanyStreet());
        context.setVariable("companyPostalCode", settings.getCompanyPostalCode());
        context.setVariable("companyCity", settings.getCompanyCity());
        context.setVariable("companyCountry", settings.getCompanyCountry());
        context.setVariable("contactEmail", settings.getContactEmail());
        context.setVariable("agbText", settings.getAgbText());
        context.setVariable("agbVersion", settings.getAgbVersion());
        context.setVariable("agbVersionDate", settings.getAgbVersionDate());
        context.setVariable("widerrufText", settings.getWiderrufText());
        context.setVariable("widerrufVersion", settings.getWiderrufVersion());
        context.setVariable("widerrufVersionDate", settings.getWiderrufVersionDate());
        context.setVariable("widerrufErklaerenUrl", shopBaseUrl.isBlank() ? null : shopBaseUrl + "/widerruf-erklaeren");
        return templateEngine.process("email/order-legal-footer", context);
    }

    /** Renders just the items/price table - empty if the order has no items to show. */
    private String itemsTableHtml(MedusaCompleteCartResult.Order order, Branding branding) {
        if (order.items() == null || order.items().isEmpty()) {
            return "";
        }
        Context context = new Context();
        context.setVariable("items", order.items());
        context.setVariable("currency", order.currency_code());
        context.setVariable("shippingTotal", order.shipping_total());
        context.setVariable("total", order.total());
        context.setVariable("colorAccent", branding.colorAccent());
        return templateEngine.process("email/order-items-table", context);
    }

    private String renderEmailHtml(String contentHtml, Branding branding) {
        Context context = new Context();
        context.setVariable("contentHtml", contentHtml);
        context.setVariable("siteTitle", branding.siteTitle());
        context.setVariable("logoUrl", branding.logoImageUrl());
        context.setVariable("backgroundUrl", branding.backgroundImageUrl());
        context.setVariable("colorPrimary", branding.colorPrimary());
        context.setVariable("colorSecondary", branding.colorSecondary());
        context.setVariable("colorAccent", branding.colorAccent());
        return templateEngine.process("email/order-confirmation-email", context);
    }

    private static MedusaCompleteCartResult.Order sampleOrder() {
        MedusaCompleteCartResult.OrderItem item = new MedusaCompleteCartResult.OrderItem(
                "Festival-Shirt", "M", BigDecimal.ONE, new BigDecimal("15.00"), new BigDecimal("15.00"));
        return new MedusaCompleteCartResult.Order("order_preview", 1001L, "preview@example.com",
                new BigDecimal("19.99"), new BigDecimal("4.99"), "eur", List.of(item));
    }
}
