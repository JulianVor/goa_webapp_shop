-- Defensive, idempotent safety net - see application.yml's spring.sql.init.mode comment.
-- Every statement here must be safe to run on every single startup, against a database
-- that may already have some or all of these objects (IF NOT EXISTS everywhere), and must
-- never DROP or otherwise touch existing data. This is not a general migration mechanism -
-- it exists purely to guarantee the columns/table added for the Rechtskonformität work
-- actually exist, after Hibernate's ddl-auto=update failed to add them on at least one
-- production deploy.

ALTER TABLE IF EXISTS shop_settings
    ADD COLUMN IF NOT EXISTS company_name varchar(255),
    ADD COLUMN IF NOT EXISTS company_street varchar(255),
    ADD COLUMN IF NOT EXISTS company_postal_code varchar(255),
    ADD COLUMN IF NOT EXISTS company_city varchar(255),
    ADD COLUMN IF NOT EXISTS company_country varchar(255),
    ADD COLUMN IF NOT EXISTS return_email varchar(255),
    ADD COLUMN IF NOT EXISTS agb_version integer NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS agb_version_date date,
    ADD COLUMN IF NOT EXISTS widerruf_version integer NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS widerruf_version_date date,
    ADD COLUMN IF NOT EXISTS shipping_info_text text,
    ADD COLUMN IF NOT EXISTS delivery_payment_notice text;

CREATE TABLE IF NOT EXISTS withdrawal_request (
    id bigserial PRIMARY KEY,
    customer_name varchar(255),
    order_reference varchar(255),
    email varchar(255),
    note text,
    received_at timestamp,
    status varchar(255)
);
