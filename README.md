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
   - If the database already exists and you only want to add the second-stage demo POIs, run `sql/seed_stage2.sql` instead of resetting the schema.
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

Frontend runs on:

```text
http://localhost:5173/login
```

## Demo Script

Use this sequence for the first milestone demo:

1. Log in as `student / 123456`.
2. Open the AI map workbench and ask: `找一个安静有插座的自习点`.
3. Confirm the result list, map highlight, and POI detail panel update together.
4. Ask: `找打印店`; the result should focus on print/copy service POIs.
5. Ask: `从宿舍 A 区去图书馆三楼自习区`; the mock AI should identify route intent and highlight the origin/destination.
6. Open a POI detail and submit feedback, for example: `打印店周末下午也营业，需要补充备注。`
7. Log in as `admin / 123456`.
8. Open feedback review, approve the pending feedback, and sync the generated POI remark.
9. Return to the user map page and confirm the POI detail remark changed.

The demo should emphasize the closed loop: self-managed POI data, AI map actions, user feedback, admin review, and visible POI correction.

## Git Flow

Development should happen on `develop`; keep `main` stable for milestone demos.
