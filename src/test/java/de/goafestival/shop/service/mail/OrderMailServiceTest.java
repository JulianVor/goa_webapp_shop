package de.goafestival.shop.service.mail;

import de.goafestival.shop.service.branding.Branding;
import de.goafestival.shop.service.branding.GoaWebappBrandingClient;
import de.goafestival.shop.service.medusa.dto.MedusaCompleteCartResult;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.ITemplateEngine;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OrderMailServiceTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final ITemplateEngine templateEngine = mock(ITemplateEngine.class);
    private final GoaWebappBrandingClient brandingClient = mock(GoaWebappBrandingClient.class);

    @Test
    void doesNothingWithoutMailHostConfigured() {
        OrderMailService service = service(/* mailHost */ "");

        service.sendOrderConfirmation(order());

        verifyNoInteractions(mailSender, templateEngine, brandingClient);
    }

    @Test
    void doesNothingForANullOrOrderlessEmailOrder() {
        OrderMailService service = service("smtp-relay.brevo.com");

        service.sendOrderConfirmation(null);
        service.sendOrderConfirmation(new MedusaCompleteCartResult.Order("order_1", 1001L, null));

        verifyNoInteractions(mailSender, templateEngine, brandingClient);
    }

    @Test
    void sendsTheConfirmationWhenConfigured() throws Exception {
        OrderMailService service = service("smtp-relay.brevo.com");
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
    void aMailSendFailureIsSwallowedNotPropagated() {
        OrderMailService service = service("smtp-relay.brevo.com");
        when(brandingClient.current()).thenReturn(fallbackBranding());
        when(templateEngine.process(anyString(), any())).thenReturn("<html></html>");
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getDefaultInstance(new Properties())));
        doThrow(new MailSendException("boom")).when(mailSender).send(any(MimeMessage.class));

        assertDoesNotThrow(() -> service.sendOrderConfirmation(order()));
    }

    private OrderMailService service(String mailHost) {
        return new OrderMailService(mailSender, templateEngine, brandingClient,
                "bestellung@example.com", "login@example.com", mailHost);
    }

    private static MedusaCompleteCartResult.Order order() {
        return new MedusaCompleteCartResult.Order("order_1", 1001L, "test@example.com");
    }

    private static Branding fallbackBranding() {
        return new Branding("Festivalshop", null, null, null,
                "#2f6f68", "#e0559a", "#f2c14e", "#2f6f68", "#2f6f68", "#f2c14e", "#f2c14e",
                "#2f6f68", "#f2c14e", "20px", "999px", "font", "font", "normal", "normal");
    }
}
