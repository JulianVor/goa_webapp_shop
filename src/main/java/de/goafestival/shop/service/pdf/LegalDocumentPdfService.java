package de.goafestival.shop.service.pdf;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import de.goafestival.shop.domain.ShopSettings;
import de.goafestival.shop.service.ShopSettingsService;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;
import org.w3c.dom.Document;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Renders the AGB, Widerrufsbelehrung and Muster-Widerrufsformular as standalone PDFs, from
 * the exact same admin-edited text/structured seller data shown on the public legal pages -
 * attached to the order confirmation mail (see OrderMailService) instead of embedded as HTML
 * in the mail body itself.
 */
@Service
public class LegalDocumentPdfService {

    private final ITemplateEngine templateEngine;
    private final ShopSettingsService shopSettingsService;
    private final String shopBaseUrl;

    public LegalDocumentPdfService(ITemplateEngine templateEngine, ShopSettingsService shopSettingsService,
                                    @Value("${app.shop.base-url:}") String shopBaseUrl) {
        this.templateEngine = templateEngine;
        this.shopSettingsService = shopSettingsService;
        this.shopBaseUrl = shopBaseUrl;
    }

    public byte[] agbPdf() {
        ShopSettings settings = shopSettingsService.get();
        Context context = sellerContext(settings);
        context.setVariable("agbText", settings.getAgbText());
        context.setVariable("agbVersion", settings.getAgbVersion());
        context.setVariable("agbVersionDate", settings.getAgbVersionDate());
        return render("pdf/agb", context);
    }

    public byte[] widerrufPdf() {
        ShopSettings settings = shopSettingsService.get();
        Context context = sellerContext(settings);
        context.setVariable("widerrufText", settings.getWiderrufText());
        context.setVariable("widerrufVersion", settings.getWiderrufVersion());
        context.setVariable("widerrufVersionDate", settings.getWiderrufVersionDate());
        context.setVariable("widerrufErklaerenUrl", shopBaseUrl.isBlank() ? null : shopBaseUrl + "/widerruf-erklaeren");
        return render("pdf/widerruf", context);
    }

    public byte[] musterWiderrufsformularPdf() {
        return render("pdf/muster-widerrufsformular", sellerContext(shopSettingsService.get()));
    }

    private Context sellerContext(ShopSettings settings) {
        Context context = new Context();
        context.setVariable("companyName", settings.getCompanyName());
        context.setVariable("companyStreet", settings.getCompanyStreet());
        context.setVariable("companyPostalCode", settings.getCompanyPostalCode());
        context.setVariable("companyCity", settings.getCompanyCity());
        context.setVariable("companyCountry", settings.getCompanyCountry());
        context.setVariable("contactEmail", settings.getContactEmail());
        return context;
    }

    /**
     * The admin's rich-text-editor HTML (AGB/Widerruf) isn't guaranteed to be well-formed XML,
     * which openhtmltopdf requires - jsoup parses it leniently (like a browser would) and
     * converts the result to a proper XHTML DOM, the documented way to feed "real world" HTML
     * to openhtmltopdf.
     */
    private byte[] render(String template, Context context) {
        String html = templateEngine.process(template, context);
        try {
            org.jsoup.nodes.Document jsoupDocument = Jsoup.parse(html);
            jsoupDocument.outputSettings().syntax(org.jsoup.nodes.Document.OutputSettings.Syntax.xml);
            Document w3cDocument = new W3CDom().fromJsoup(jsoupDocument);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withW3cDocument(w3cDocument, "");
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("PDF (" + template + ") konnte nicht erzeugt werden", e);
        }
    }
}
