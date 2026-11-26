package de.goafestival.shop.web;

/** Shared between StorefrontController and CheckoutController - both read this same cookie. */
final class CartCookie {

    static final String NAME = "cart_id";

    private CartCookie() {
    }
}
