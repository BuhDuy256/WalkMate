-- ============================================================
-- V4: Seed the 4 initial hotspots
-- Matches the Android mock data so the map works immediately
-- when the frontend switches from mock to real API calls.
-- ============================================================

INSERT INTO hotspot (id, name, lat, lng) VALUES
    ('11111111-1111-1111-1111-111111111111', 'Công viên Tao Đàn',    10.77702, 106.69328),
    ('22222222-2222-2222-2222-222222222222', 'Hồ Con Rùa',            10.78756, 106.69506),
    ('33333333-3333-3333-3333-333333333333', 'Công viên Gia Định',   10.81289, 106.67790),
    ('44444444-4444-4444-4444-444444444444', 'Công viên Lê Văn Tám', 10.78720, 106.69863);
