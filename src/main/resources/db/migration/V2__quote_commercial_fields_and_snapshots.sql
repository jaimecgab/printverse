ALTER TABLE quotes
    ADD COLUMN title VARCHAR(200),
    ADD COLUMN internal_notes VARCHAR(3000),
    ADD COLUMN currency_code VARCHAR(3) NOT NULL DEFAULT 'MXN',
    ADD COLUMN updated_at TIMESTAMP(6) WITH TIME ZONE,
    ADD COLUMN sent_at TIMESTAMP(6) WITH TIME ZONE,
    ADD COLUMN accepted_at TIMESTAMP(6) WITH TIME ZONE,
    ADD COLUMN rejected_at TIMESTAMP(6) WITH TIME ZONE,
    ADD COLUMN customer_name_snapshot VARCHAR(150),
    ADD COLUMN customer_phone_snapshot VARCHAR(40),
    ADD COLUMN customer_email_snapshot VARCHAR(254),
    ADD CONSTRAINT chk_quotes_currency_code CHECK (currency_code = 'MXN');

ALTER TABLE quote_items
    ADD COLUMN material_name_snapshot VARCHAR(100),
    ADD COLUMN printer_name_snapshot VARCHAR(100),
    ADD COLUMN printer_model_snapshot VARCHAR(100);

UPDATE quotes quote
SET updated_at = quote.created_at;

UPDATE quotes quote
SET customer_name_snapshot = customer.name,
    customer_phone_snapshot = customer.phone,
    customer_email_snapshot = customer.email
FROM customers customer
WHERE quote.customer_id = customer.id
  AND quote.status <> 'DRAFT';

UPDATE quote_items item
SET material_name_snapshot = material.name,
    printer_name_snapshot = printer.name,
    printer_model_snapshot = printer.model
FROM materials material, printers printer
WHERE item.material_id = material.id
  AND item.printer_id = printer.id;

ALTER TABLE quotes
    ALTER COLUMN updated_at SET NOT NULL,
    ADD CONSTRAINT chk_quotes_final_customer_snapshot CHECK (
        status = 'DRAFT'
        OR (customer_name_snapshot IS NOT NULL AND customer_phone_snapshot IS NOT NULL)
    );

ALTER TABLE quote_items
    ALTER COLUMN material_name_snapshot SET NOT NULL,
    ALTER COLUMN printer_name_snapshot SET NOT NULL;

CREATE INDEX idx_quotes_status_valid_until ON quotes (status, valid_until);
CREATE INDEX idx_quotes_updated_at ON quotes (updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_quotes_customer_id ON quotes (customer_id);
CREATE INDEX IF NOT EXISTS idx_quotes_created_at ON quotes (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_quote_items_quote_id ON quote_items (quote_id);
CREATE INDEX IF NOT EXISTS idx_quote_items_material_id ON quote_items (material_id);
CREATE INDEX IF NOT EXISTS idx_quote_items_printer_id ON quote_items (printer_id);
CREATE INDEX IF NOT EXISTS idx_additional_charges_item_id ON additional_charges (quote_item_id);
