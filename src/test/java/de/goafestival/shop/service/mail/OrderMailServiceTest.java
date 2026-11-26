package de.goafestival.shop.service.mail;

import de.goafestival.shop.domain.ShopSettings;
import de.goafestival.shop.service.ShopSettingsService;
import de.goafestival.shop.service.branding.Branding;
import de.goafestival.shop.service.branding.GoaWebappBrandingClient;
import de.goafestival.shop.service.medusa.dto.MedusaCompleteCartResult;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.ITemplateEngine;

import java.math.BigDecimal;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OrderMailServiceTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final ITemplateEngine templateEngine = mock(ITemplateEngine.class);
    private final GoaWebappBrandingClient brandingClient = mock(GoaWebappBrandingClient.class);
    private final ShopSettingsService shopSettingsService = mock(ShopSettingsService.class);

    @Test
    void doesNothingWithoutMailHostConfigured() {
        OrderMailService service = service(/* mailHost */ "");

        service.sendOrderConfirmation(order());

        verifyNoInteractions(mailSender, templateEngine, brandingClient, shopSettingsService);
    }

    @Test
    void doesNothingForANullOrOrderlessEmailOrder() {
        OrderMailService service = service("smtp-relay.brevo.com");

        service.sendOrderConfirmation(null);
        service.sendOrderConfirmation(new MedusaCompleteCartResult.Order(
                "order_1", 1001L, null, BigDecimal.TEN, BigDecimal.ZERO, "eur", List.of()));

        verifyNoInteractions(mailSender, templateEngine, brandingClient, shopSettingsService);
    }

    @Test
    void sendsTheConfirmationWhenConfigured() throws Exception {
        OrderMailService service = service("smtp-relay.brevo.com");
        when(shopSettingsService.get()).thenReturn(new ShopSettings());
        when(brandingClient.current()).thenReturn(fallbackBranding());
        when(templateEngine.process(eq("email/order-confirmation-email"), any())).thenReturn("<html></html>");
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);

        service.sendOrderConfirmation(order());

        verify(mailSender).send(message);
        assertEquals("test@example.com", message.getAllRecipients()[0].toString());
        assertEquals("Deine Bestellung Nr. 1001", message.getSubject());
    }

    @Test
    void usesTheAdminConfiguredSubjectAndSubstitutesTheOrderNumberPlaceholder() throws Exception {
        OrderMailService service = service("smtp-relay.brevo.com");
        ShopSettings settings = new ShopSettings();
        settings.setOrderConfirmationSubject("Danke für Bestellung {{bestellnummer}}!");
        settings.setOrderConfirmationBody("<p>Nr. {{bestellnummer}}</p>");
        when(shopSettingsService.get()).thenReturn(settings);
        when(brandingClient.current()).thenReturn(fallbackBranding());
        when(templateEngine.process(eq("email/order-confirmation-email"), any())).thenReturn("<html></html>");
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);

        service.sendOrderConfirmation(order());

        assertEquals("Danke für Bestellung 1001!", message.getSubject());
    }

    @Test
    void rendersTheItemsTableWhenTheOrderHasItems() throws Exception {
        OrderMailService service = service("smtp-relay.brevo.com");
        ShopSettings settings = new ShopSettings();
        settings.setOrderConfirmationBody("<p>{{bestellungen}}</p>");
        when(shopSettingsService.get()).thenReturn(settings);
        when(brandingClient.current()).thenReturn(fallbackBranding());
        when(templateEngine.process(eq("email/order-items-table"), any())).thenReturn("<table>rows</table>");
        when(templateEngine.process(eq("email/order-confirmation-email"), any())).thenReturn("<html></html>");
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getDefaultInstance(new Properties())));

        service.sendOrderConfirmation(orderWithItems());

        verify(templateEngine).process(eq("email/order-items-table"), any());
    }

    @Test
    void skipsTheItemsTableWhenTheOrderHasNoItems() throws Exception {
        OrderMailService service = service("smtp-relay.brevo.com");
        ShopSettings settings = new ShopSettings();
        settings.setOrderConfirmationBody("<p>{{bestellungen}}</p>");
        when(shopSettingsService.get()).thenReturn(settings);
        when(brandingClient.current()).thenReturn(fallbackBranding());
        when(templateEngine.process(eq("email/order-confirmation-email"), any())).thenReturn("<html></html>");
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getDefaultInstance(new Properties())));

        service.sendOrderConfirmation(order());

        verify(templateEngine, never()).process(eq("email/order-items-table"), any());
    }

    @Test
    void aMailSendFailureIsSwallowedNotPropagated() {
        OrderMailService service = service("smtp-relay.brevo.com");
        when(shopSettingsService.get()).thenReturn(new ShopSettings());
        when(brandingClient.current()).thenReturn(fallbackBranding());
        when(templateEngine.process(anyString(), any())).thenReturn("<html></html>");
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getDefaultInstance(new Properties())));
        doThrow(new MailSendException("boom")).when(mailSender).send(any(MimeMessage.class));

        assertDoesNotThrow(() -> service.sendOrderConfirmation(order()));
    }

    @Test
    void sendTestMailThrowsIllegalStateWithoutMailHostConfigured() {
        OrderMailService service = service(/* mailHost */ "");

        assertThrows(IllegalStateException.class, service::sendTestMail);
        verifyNoInteractions(mailSender, templateEngine, brandingClient, shopSettingsService);
    }

    @Test
    void sendTestMailSendsToTheConfiguredFromAddressAndReturnsIt() throws Exception {
        OrderMailService service = service("smtp-relay.brevo.com");
        when(shopSettingsService.get()).thenReturn(new ShopSettings());
        when(brandingClient.current()).thenReturn(fallbackBranding());
        when(templateEngine.process(anyString(), any())).thenReturn("<html></html>");
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);

        String recipient = service.sendTestMail();

        assertEquals("bestellung@example.com", recipient);
        verify(mailSender).send(message);
        assertEquals("bestellung@example.com", message.getAllRecipients()[0].toString());
    }

    @Test
    void sendTestMailThrowsWithTheRootCauseMessageOnFailure() {
        OrderMailService service = service("smtp-relay.brevo.com");
        when(shopSettingsService.get()).thenReturn(new ShopSettings());
        when(brandingClient.current()).thenReturn(fallbackBranding());
        when(templateEngine.process(anyString(), any())).thenReturn("<html></html>");
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getDefaultInstance(new Properties())));
        doThrow(new MailSendException("wrapper message", new RuntimeException("535 Authentication failed")))
                .when(mailSender).send(any(MimeMessage.class));

        OrderMailService.TestMailException e = assertThrows(OrderMailService.TestMailException.class, service::sendTestMail);

        assertEquals("bestellung@example.com", e.getRecipient());
        assertEquals("535 Authentication failed", e.getMessage());
    }

    private OrderMailService service(String mailHost) {
        return new OrderMailService(mailSender, templateEngine, brandingClient, shopSettingsService,
                "bestellung@example.com", "login@example.com", mailHost);
    }

    private static MedusaCompleteCartResult.Order order() {
        return new MedusaCompleteCartResult.Order("order_1", 1001L, "test@example.com",
                new BigDecimal("19.99"), new BigDecimal("4.99"), "eur", List.of());
    }

    private static MedusaCompleteCartResult.Order orderWithItems() {
        MedusaCompleteCartResult.OrderItem item = new MedusaCompleteCartResult.OrderItem(
                "Festival-Shirt", "M", BigDecimal.ONE, new BigDecimal("15.00"), new BigDecimal("15.00"));
        return new MedusaCompleteCartResult.Order("order_1", 1001L, "test@example.com",
                new BigDecimal("19.99"), new BigDecimal("4.99"), "eur", List.of(item));
    }

    private static Branding fallbackBranding() {
        return new Branding("Festivalshop", null, null, null,
                "#2f6f68", "#e0559a", "#f2c14e", "#2f6f68", "#2f6f68", "#f2c14e", "#f2c14e",
                "#2f6f68", "#f2c14e", "20px", "999px", "font", "font", "normal", "normal");
    }
}
