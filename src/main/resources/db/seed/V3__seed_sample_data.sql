-- Optional sample data (prices in IDR). Excluded from tests via spring.flyway.locations.
INSERT INTO items (name, description) VALUES ('T-Shirt', 'Basic cotton T-Shirt');
INSERT INTO items (name, description) VALUES ('Sneakers', 'Everyday canvas sneakers');
INSERT INTO items (name, description) VALUES ('Ceramic Mug', 'Single-option product, modelled with one default variant');

INSERT INTO variants (item_id, sku, name, price, stock_quantity) VALUES
    ((SELECT id FROM items WHERE name = 'T-Shirt'), 'TS-BLK-S', 'Black / S', 149000.00, 20),
    ((SELECT id FROM items WHERE name = 'T-Shirt'), 'TS-BLK-M', 'Black / M', 149000.00, 35),
    ((SELECT id FROM items WHERE name = 'T-Shirt'), 'TS-BLK-L', 'Black / L', 159000.00, 15),
    ((SELECT id FROM items WHERE name = 'T-Shirt'), 'TS-WHT-M', 'White / M', 149000.00, 0);

INSERT INTO variants (item_id, sku, name, price, stock_quantity) VALUES
    ((SELECT id FROM items WHERE name = 'Sneakers'), 'SNK-BLK-40', 'Black / 40', 599000.00, 8),
    ((SELECT id FROM items WHERE name = 'Sneakers'), 'SNK-BLK-41', 'Black / 41', 599000.00, 12),
    ((SELECT id FROM items WHERE name = 'Sneakers'), 'SNK-BLK-42', 'Black / 42', 619000.00, 5);

INSERT INTO variants (item_id, sku, name, price, stock_quantity) VALUES
    ((SELECT id FROM items WHERE name = 'Ceramic Mug'), 'MUG-STD', 'Standard', 75000.00, 40);
