# WalkMate Backend

Huong dan build, chay server backend, va chay test cho module `backend`.

## 1. Yeu cau moi truong

- Java 17+
- PostgreSQL (Supabase hoac local PostgreSQL)
- Gradle Wrapper (da co san trong project)

## 2. Cau hinh bien moi truong

Backend dung cac bien sau trong `application.properties`:

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`

Ban co 2 cach:

1. Dat bien moi truong trong shell/CI.
2. Tao file `.env` (dang da duoc dung trong backend) voi noi dung:

```env
DB_URL=jdbc:postgresql://<host>:<port>/<database>
DB_USERNAME=<username>
DB_PASSWORD=<password>
```

Luu y:

- Flyway da duoc go khoi backend app.
- Supabase/PostgreSQL schema la single source of truth.
- JPA dang `validate`, nen schema tren Supabase phai khop voi entity backend.

## 3. Build backend

Tu thu muc root `WalkMate/`:

```powershell
.\gradlew.bat :backend:clean :backend:build
```

Hoac tren bash:

```bash
./gradlew :backend:clean :backend:build
```

## 4. Chay backend server

Tu thu muc root `WalkMate/`:

```powershell
.\gradlew.bat :backend:bootRun
```

Hoac bash:

```bash
./gradlew :backend:bootRun
```

Mac dinh server chay o:

- `http://localhost:8080`

## 5. Chay test

Chay toan bo test backend:

```powershell
.\gradlew.bat :backend:test
```

Chay 1 test class cu the:

```powershell
.\gradlew.bat :backend:test --tests "com.walkmate.domain.session.WalkSessionTest"
```

## 6. Dong goi artifact

Tao JAR cua backend:

```powershell
.\gradlew.bat :backend:bootJar
```

Jar output thuong o:

- `backend/build/libs/`

## 7. Migration DB (Supabase la nguon su that)

Backend khong tu dong migrate schema.

Ban can cap nhat schema truc tiep tren Supabase/PostgreSQL truoc khi chay app.

Vi du voi loi `missing column [abort_reason] in table [walk_session]`, chay SQL:

```sql
ALTER TABLE walk_session ADD COLUMN IF NOT EXISTS abort_reason VARCHAR(32);
```

Khuyen nghi:

- Quan ly SQL script trong `docs/single-source-of-truth/db/migrations/`.
- Apply script bang SQL Editor cua Supabase hoac psql.

## 8. Troubleshooting nhanh

1. Loi ket noi DB:

- Kiem tra `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`.
- Kiem tra database co cho phep ket noi tu IP hien tai.

2. Loi JPA validate (schema mismatch):

- Kiem tra schema Supabase co day du cot/constraint theo entity backend.
- Bo sung schema thieu bang SQL truc tiep tren Supabase.

3. Loi auth khi goi API:

- Project dang dung Spring Security OAuth2 Resource Server.
- Cac endpoint can JWT hop le neu security duoc bat cho route do.

## 9. Lenh dung nhanh (copy/paste)

```powershell
# Build backend
.\gradlew.bat :backend:clean :backend:build

# Run backend
.\gradlew.bat :backend:bootRun

# Run backend tests
.\gradlew.bat :backend:test
```
