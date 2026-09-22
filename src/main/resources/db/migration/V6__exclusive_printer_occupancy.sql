DO $$
DECLARE
    duplicate_printers TEXT;
    invalid_printers TEXT;
    invalid_orders TEXT;
    ambiguous_busy_printers TEXT;
BEGIN
    UPDATE printers printer
    SET operational_status = 'AVAILABLE'
    WHERE printer.operational_status = 'BUSY'
      AND EXISTS (
          SELECT 1
          FROM production_order_items item
          JOIN production_orders production_order ON production_order.id = item.production_order_id
          WHERE item.assigned_printer_id = printer.id
            AND item.status = 'IN_PROGRESS'
            AND production_order.status = 'CANCELLED'
      )
      AND NOT EXISTS (
          SELECT 1
          FROM production_order_items item
          JOIN production_orders production_order ON production_order.id = item.production_order_id
          WHERE item.assigned_printer_id = printer.id
            AND item.status = 'IN_PROGRESS'
            AND production_order.status <> 'CANCELLED'
      );

    UPDATE production_order_items item
    SET status = 'BLOCKED'
    FROM production_orders production_order
    WHERE item.production_order_id = production_order.id
      AND item.status = 'IN_PROGRESS'
      AND production_order.status = 'CANCELLED';

    SELECT STRING_AGG(item.id::TEXT, ', ' ORDER BY item.id)
    INTO invalid_orders
    FROM production_order_items item
    JOIN production_orders production_order ON production_order.id = item.production_order_id
    WHERE item.status = 'IN_PROGRESS'
      AND production_order.status <> 'IN_PRODUCTION';

    IF invalid_orders IS NOT NULL THEN
        RAISE EXCEPTION 'Cannot enforce printer occupancy; IN_PROGRESS items outside IN_PRODUCTION orders: %',
            invalid_orders;
    END IF;

    SELECT STRING_AGG(assigned_printer_id::TEXT, ', ' ORDER BY assigned_printer_id)
    INTO duplicate_printers
    FROM (
        SELECT assigned_printer_id
        FROM production_order_items
        WHERE status = 'IN_PROGRESS' AND assigned_printer_id IS NOT NULL
        GROUP BY assigned_printer_id
        HAVING COUNT(*) > 1
    ) duplicates;

    IF duplicate_printers IS NOT NULL THEN
        RAISE EXCEPTION 'Cannot enforce exclusive printer occupancy; printers with multiple IN_PROGRESS items: %',
            duplicate_printers;
    END IF;

    SELECT STRING_AGG(DISTINCT printer.id::TEXT, ', ' ORDER BY printer.id::TEXT)
    INTO invalid_printers
    FROM production_order_items item
    JOIN printers printer ON printer.id = item.assigned_printer_id
    WHERE item.status = 'IN_PROGRESS'
      AND (NOT printer.active OR printer.operational_status IN ('MAINTENANCE', 'OUT_OF_SERVICE'));

    IF invalid_printers IS NOT NULL THEN
        RAISE EXCEPTION 'Cannot reconcile printer occupancy; active items use inactive or unavailable printers: %',
            invalid_printers;
    END IF;

    SELECT STRING_AGG(printer.id::TEXT, ', ' ORDER BY printer.id)
    INTO ambiguous_busy_printers
    FROM printers printer
    WHERE printer.operational_status = 'BUSY'
      AND NOT EXISTS (
          SELECT 1
          FROM production_order_items item
          WHERE item.assigned_printer_id = printer.id
            AND item.status = 'IN_PROGRESS'
      );

    IF ambiguous_busy_printers IS NOT NULL THEN
        RAISE EXCEPTION 'Cannot reinterpret legacy BUSY printers without active production items; resolve printer IDs: %',
            ambiguous_busy_printers;
    END IF;
END $$;

ALTER TABLE production_order_items
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE production_order_items
    ADD CONSTRAINT chk_production_item_in_progress_printer
    CHECK (status <> 'IN_PROGRESS' OR assigned_printer_id IS NOT NULL);

CREATE UNIQUE INDEX uq_production_item_active_printer
    ON production_order_items (assigned_printer_id)
    WHERE status = 'IN_PROGRESS';

UPDATE printers printer
SET operational_status = CASE
    WHEN EXISTS (
        SELECT 1
        FROM production_order_items item
        WHERE item.assigned_printer_id = printer.id
          AND item.status = 'IN_PROGRESS'
    ) THEN 'BUSY'
    ELSE printer.operational_status
END;
