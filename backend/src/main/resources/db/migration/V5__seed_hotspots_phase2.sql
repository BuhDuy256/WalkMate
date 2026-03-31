-- ============================================================
-- V5: Phase 2 hotspot catalogue normalization (5 HCMC locations)
-- Non-destructive migration: update existing seeded rows and insert one new row.
-- ============================================================

UPDATE hotspot
SET name = 'Nguyen Hue', lat = 10.77256, lng = 106.70262
WHERE id = '11111111-1111-1111-1111-111111111111';

UPDATE hotspot
SET name = 'Turtle Lake', lat = 10.78756, lng = 106.69506
WHERE id = '22222222-2222-2222-2222-222222222222';

UPDATE hotspot
SET name = 'Han Thuan Park', lat = 10.78058, lng = 106.69731
WHERE id = '33333333-3333-3333-3333-333333333333';

UPDATE hotspot
SET name = 'Le Van Tam Park', lat = 10.78720, lng = 106.69863
WHERE id = '44444444-4444-4444-4444-444444444444';

INSERT INTO hotspot (id, name, lat, lng)
VALUES ('55555555-5555-5555-5555-555555555555', 'Crescent Mall', 10.72989, 106.71804)
ON CONFLICT (id) DO UPDATE
SET name = EXCLUDED.name,
    lat = EXCLUDED.lat,
    lng = EXCLUDED.lng;
