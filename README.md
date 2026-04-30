# SmartCampusNavigation

SmartCampusNavigation is a first-version demo for an AI-assisted smart campus navigation system. The core loop is:

1. User asks a campus map question in natural language.
2. Mock AI returns structured JSON with intent, tool calls, and map actions.
3. The map highlights POIs, opens details, or starts route fallback.
4. User submits POI feedback.
5. Admin reviews feedback and updates campus POI data.

## Modules

- `backend/`: Spring Boot 3.5.x API service.
- `frontend/`: Vue 3 + Vite + Element Plus UI.
- `sql/`: MySQL schema and seed data.
- `docs/`: design and project notes.

## Local Run

1. Create MySQL database and import `sql/schema.sql`.
2. Configure environment variables:

```powershell
$env:DB_URL="jdbc:mysql://localhost:3306/smart_campus_navigation?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="your-password"
$env:JWT_SECRET="replace-with-a-long-random-secret"
```

3. Start backend:

```powershell
cd backend
mvn spring-boot:run
```

The backend runs on:

```text
http://localhost:8000
```

4. Start frontend:

```powershell
cd frontend
npm.cmd install
npm.cmd run dev
```

Default seed users:

- User: `student` / `123456`
- Admin: `admin` / `123456`

## Git Flow

Development should happen on `develop`; keep `main` stable for milestone demos.
