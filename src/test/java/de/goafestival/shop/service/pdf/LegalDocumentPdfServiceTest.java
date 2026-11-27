package de.goafestival.shop.service.pdf;

import de.goafestival.shop.domain.ShopSettings;
import de.goafestival.shop.service.ShopSettingsService;
import org.junit.jupiter.api.Test;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Uses a real Thymeleaf engine (not mocked) against the actual pdf/*.html templates, and real
 * PDF rendering (openhtmltopdf+jsoup) - this is new, non-trivial wiring where a template typo
 * or an unsupported HTML construct in admin-authored rich text would otherwise only surface
 * the first time an admin actually triggers a send.
 */
class LegalDocumentPdfServiceTest {

    private final ShopSettingsService shopSettingsService = mock(ShopSettingsService.class);
    private final LegalDocumentPdfService service =
            new LegalDocumentPdfService(realTemplateEngine(), shopSettingsService, "https://shop.example.com");

    @Test
    void rendersAgbAsAValidPdfIncludingUmlautsAndRichTextMarkup() {
        ShopSettings settings = sampleSettings();
        settings.setAgbText("<p>§1 Geltungsbereich für Bestellungen über 50 € - Umlaute: äöüÄÖÜß.</p>"
                + "<ul><li>Punkt eins</li><li>Punkt zwei</li></ul><p><strong>Wichtig</strong></p>");
        when(shopSettingsService.get()).thenReturn(settings);

        assertIsPdf(service.agbPdf());
    }

    @Test
    void rendersWiderrufAsAValidPdf() {
        ShopSettings settings = sampleSettings();
        settings.setWiderrufText("<p>Du hast das Recht, binnen 14 Tagen ohne Angabe von Gründen zu widerrufen.</p>");
        when(shopSettingsService.get()).thenReturn(settings);

        assertIsPdf(service.widerrufPdf());
    }

    @Test
    void rendersMusterWiderrufsformularAsAValidPdfEvenWithNoLegalTextSetYet() {
        when(shopSettingsService.get()).thenReturn(sampleSettings());

        assertIsPdf(service.musterWiderrufsformularPdf());
    }

    private static ShopSettings sampleSettings() {
        ShopSettings settings = new ShopSettings();
        settings.setCompanyName("Festival e.V.");
        settings.setCompanyStreet("Grumbrechtstraße 1");
        settings.setCompanyPostalCode("12345");
        settings.setCompanyCity("Musterstadt");
        settings.setCompanyCountry("Deutschland");
        settings.setContactEmail("kontakt@example.com");
        return settings;
    }

    private static void assertIsPdf(byte[] pdf) {
        assertTrue(pdf.length > 200, "PDF sollte nicht trivial leer sein");
        String header = new String(pdf, 0, 5, StandardCharsets.US_ASCII);
        assertTrue(header.startsWith("%PDF-"), "sollte mit der PDF-Magic-Byte-Sequenz beginnen, war: " + header);
    }

    /**
     * SpringTemplateEngine, not the plain Thymeleaf TemplateEngine - the app's real
     * ITemplateEngine bean (from spring-boot-starter-thymeleaf) evaluates expressions via
     * SpringEL, not OGNL, and this project deliberately has no ognl dependency at all.
     */
    private static ITemplateEngine realTemplateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
