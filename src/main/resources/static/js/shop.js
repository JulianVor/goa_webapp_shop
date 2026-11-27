const countBadge = document.querySelector('[data-count-url]');
if (countBadge) {
    window.addEventListener('pageshow', async event => {
        if (!countBadge.hidden && !event.persisted) return;
        try {
            const response = await fetch(countBadge.dataset.countUrl);
            if (response.ok) {
                countBadge.textContent = await response.json();
                countBadge.hidden = false;
            }
        } catch {
            // The cart link still works if its optional item count is unavailable.
        }
    });
}

for (const quantityForm of document.querySelectorAll('.quantity-form')) {
    quantityForm.querySelector('button').hidden = true;
    quantityForm.addEventListener('change', () => quantityForm.requestSubmit());
}

const gallery = document.querySelector('.thumbs');
if (gallery) {
    gallery.addEventListener('click', event => {
        const thumb = event.target.closest('a');
        if (!thumb || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
        event.preventDefault();
        document.getElementById('product-photo').src = thumb.href;
        gallery.querySelector('[aria-current]')?.removeAttribute('aria-current');
        thumb.setAttribute('aria-current', 'true');
    });
}

const form = document.querySelector('.purchase-form');
if (form) {
    function showVariantPrice() {
        const selected = form.querySelector('input[name="variantId"]:checked');
        document.getElementById('initial-price').hidden = Boolean(selected);
        for (const price of document.querySelectorAll('[data-price-for]')) {
            price.hidden = price.dataset.priceFor !== selected?.value;
        }
        const initialSku = document.getElementById('initial-sku');
        if (initialSku) {
            initialSku.hidden = Boolean(selected);
            for (const sku of document.querySelectorAll('[data-sku-for]')) {
                sku.hidden = sku.dataset.skuFor !== selected?.value;
            }
        }
    }
    form.addEventListener('change', showVariantPrice);
    window.addEventListener('pageshow', showVariantPrice);
    showVariantPrice();
}
