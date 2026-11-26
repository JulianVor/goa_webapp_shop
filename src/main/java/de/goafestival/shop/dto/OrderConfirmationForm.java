package de.goafestival.shop.dto;

import jakarta.validation.constraints.NotBlank;

/** Backing bean for the admin "Bestellbestätigung" form - the mail auto-sent after checkout. */
public class OrderConfirmationForm {

    @NotBlank
    private String subject;

    @NotBlank
    private String htmlBody;

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getHtmlBody() {
        return htmlBody;
    }

    public void setHtmlBody(String htmlBody) {
        this.htmlBody = htmlBody;
    }
}
