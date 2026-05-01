# SmartCampusNavigation

SmartCampusNavigation is a second-version graduation-project demo for AI-assisted campus navigation. The main loop is:

1. A user asks a campus map question in Chinese or English.
2. Mock AI or DeepSeek returns structured JSON with intent, tool calls, POIs, and map actions.
3. The map highlights POIs, opens details, or draws a standard route fallback.
4. The user submits POI-bound feedback from a place detail.
5. An admin reviews the feedback and syncs visible POI status or remark changes.

The second version keeps the first-version MVP scope small, but strengthens real API integration, NUIST-specific data, fallback behavior, and thesis-ready documentation.

## Modules

- `backend/`: Spring Boot 3.5 API service.
- `frontend/`: Vue 3 + Vite + Element Plus UI.
- `sql/`: MySQL schema and NUIST seed data.
- `docs/`: product notes and thesis diagrams/tables.

## Local Run

1. Create the MySQL database and seed data:

```powershell
mysqlsh --sql --host=localhost --port=3306 --user=root --password=your-password -f sql/schema.sql
```

If the database already exists and you only need to refresh the NUIST second-version demo data, run:

```powershell
mysqlsh --sql --host=localhost --port=3306 --user=root --password=your-password --database=smart_campus_navigation -f sql/seed_nuist.sql
```

2. Configure backend environment variables:

```powershell
$env:DB_URL="jdbc:mysql://localhost:3306/smart_campus_navigation?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="your-password"
$env:JWT_SECRET="replace-with-a-long-random-secret"
```

Optional DeepSeek real AI mode:

```powershell
$env:AI_PROVIDER="deepseek"
$env:DEEPSEEK_API_KEY="your-deepseek-api-key"
$env:DEEPSEEK_BASE_URL="https://api.deepseek.com"
$env:DEEPSEEK_MODEL="deepseek-chat"
$env:DEEPSEEK_TIMEOUT_SECONDS="20"
```

If `AI_PROVIDER` is not `deepseek`, the key is missing, DeepSeek returns invalid JSON, required map actions are missing, or the model references illegal POI IDs, the backend falls back to Mock AI.

3. Start backend:

```powershell
cd backend
mvn spring-boot:run
```

Backend URL:

```text
http://localhost:8000
```

4. Configure optional AMap JS API:

```powershell
cd frontend
Copy-Item .env.example .env.local
```

Fill these values in `frontend\.env.local`:

```text
VITE_AMAP_KEY=your-web-js-api-key
VITE_AMAP_SECURITY_CODE=your-security-js-code
```

If the key is missing or AMap fails to load, the frontend automatically uses the built-in demo map.

5. Start frontend:

```powershell
cd frontend
npm.cmd install
npm.cmd run dev
```

Frontend URL:

```text
http://localhost:5173/login
```

Default accounts:

- User: `student` / `123456`
- Admin: `admin` / `123456`

## Demo Script

Use this sequence for the second-version demo:

1. Log in as `student / 123456`.
2. Open the AI map workbench.
3. Ask `找一个安静有插座的自习点`; confirm study POIs are highlighted.
4. Switch to English and ask `Find a quiet study place with outlets`; confirm the same structured action flow works.
5. Ask `Find Campus Print Shop`; confirm the result focuses on print/copy service POIs.
6. Ask `Go from Xiyuan Dormitory Area to NUIST Library Study Area`; confirm start/end points and the standard route fallback are shown.
7. Open a POI detail and submit feedback such as `Campus Print Shop also opens on weekend afternoons.`
8. Log in as `admin / 123456`.
9. Open feedback review, approve the pending feedback, and sync the generated POI remark or status.
10. Return to the user map page and confirm the POI detail reflects the change.

The defense narrative should emphasize: self-managed NUIST POI data, structured AI map actions, real-map fallback strategy, and the feedback correction loop.

## API Contract

`POST /api/ai/chat`

Request:

```json
{
  "message": "Find Campus Print Shop",
  "locale": "en-US"
}
```

Response keeps the same public structure:

```json
{
  "intent": "find_poi",
  "reply": "I found these campus places from your question and highlighted them on the map.",
  "pois": [],
  "toolCalls": [],
  "mapActions": []
}
```

`POST /api/feedback` now requires `poiId`. Feedback must be submitted from an existing POI context.

`GET /api/pois`

Common query parameters:

- `enabledOnly=true`: return enabled POIs only.
- `mapOnly=true`: return only POIs with `mapRank`.
- `limit=20`: cap the result size, used by the AI map page for the default hot-marker layer.

The seed data contains 100 enabled NUIST POIs. The default map layer shows the 20 POIs with `mapRank` from 1 to 20; AI search and admin management still use the full POI table.

## Verification

```powershell
mvn -f backend\pom.xml test
npm.cmd --prefix frontend run build
```

Expected current result:

- Backend tests pass, including Mock AI, DeepSeek fallback, DeepSeek parser validation, POI map-only filtering, and feedback binding checks.
- Frontend build passes. Vite may warn that the single generated JS chunk is larger than 500 kB because Element Plus and AMap-facing UI code are bundled together.

## NUIST Data Sources

Seed data is self-managed demo data and should not be treated as authoritative opening-hour data. POI names and facility coverage are checked against official NUIST pages where possible:

- [NUIST campus map service](https://nic.nuist.edu.cn/2473/listm.htm)
- [Campus and Facilities](https://en.nuist.edu.cn/4204/list.psp)
- [Library](https://en.nuist.edu.cn/4061/list.psp)
- [Sports](https://en.nuist.edu.cn/4062/list.psp)
- [Medical Service](https://en.nuist.edu.cn/_s104/4217/list.psp)
- [Services](https://en.nuist.edu.cn/4071/listm.psp)
- [International campus map links](https://gjy.nuist.edu.cn/english/CampusMap/list.psp)

## Git Flow

Development should happen on `develop`; keep `main` stable for milestone demos.
