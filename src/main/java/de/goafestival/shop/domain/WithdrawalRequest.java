package de.goafestival.shop.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One electronically declared Widerruf (contract withdrawal, §356a BGB), recorded here for
 * audit/record-keeping - entirely within goa_webapp_shop, never touching Medusa (see the
 * "Medusa bleibt vanilla" principle: no Medusa order is read, cancelled or refunded
 * automatically; a human handles that manually in the Medusa Admin + Stripe Dashboard after
 * seeing the request in this app's own admin worklist).
 */
@Entity
@Table(name = "withdrawal_request")
public class WithdrawalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String customerName;

    private String orderReference;

    private String email;

    @Column(columnDefinition = "TEXT")
    private String note;

    private Instant receivedAt;

    @Enumerated(EnumType.STRING)
    private WithdrawalStatus status = WithdrawalStatus.NEU;

    public Long getId() {
        return id;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getOrderReference() {
        return orderReference;
    }

    public void setOrderReference(String orderReference) {
        this.orderReference = orderReference;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }

    public WithdrawalStatus getStatus() {
        return status;
    }

    public void setStatus(WithdrawalStatus status) {
        this.status = status;
    }
}
