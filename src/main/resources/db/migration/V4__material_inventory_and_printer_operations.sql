ALTER TABLE materials ADD COLUMN material_type VARCHAR(60);
ALTER TABLE materials ADD COLUMN brand VARCHAR(100);
ALTER TABLE materials ADD COLUMN color VARCHAR(80);
ALTER TABLE materials ADD COLUMN stock_grams NUMERIC(14, 3);
ALTER TABLE materials ADD COLUMN low_stock_threshold_grams NUMERIC(14, 3);
ALTER TABLE materials ADD COLUMN notes VARCHAR(1000);

ALTER TABLE materials ADD CONSTRAINT chk_materials_stock_non_negative
    CHECK (stock_grams IS NULL OR stock_grams >= 0);
ALTER TABLE materials ADD CONSTRAINT chk_materials_low_stock_threshold_non_negative
    CHECK (low_stock_threshold_grams IS NULL OR low_stock_threshold_grams >= 0);

ALTER TABLE printers ADD COLUMN operational_status VARCHAR(30);
UPDATE printers SET operational_status = 'AVAILABLE' WHERE operational_status IS NULL;
ALTER TABLE printers ALTER COLUMN operational_status SET DEFAULT 'AVAILABLE';
ALTER TABLE printers ALTER COLUMN operational_status SET NOT NULL;
ALTER TABLE printers ADD COLUMN notes VARCHAR(1000);
ALTER TABLE printers ADD CONSTRAINT chk_printers_operational_status
    CHECK (operational_status IN ('AVAILABLE', 'BUSY', 'MAINTENANCE', 'OUT_OF_SERVICE'));

CREATE INDEX idx_materials_active_stock
    ON materials (active, stock_grams, low_stock_threshold_grams);
CREATE INDEX idx_printers_active_operational_status
    ON printers (active, operational_status);
