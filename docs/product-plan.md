# SmartCampusNavigation Second Version

## Positioning

The system focuses on AI map interaction, self-managed NUIST POI data, a real-map fallback strategy, and a feedback correction loop. Discovery content and profile pages stay secondary.

## Baseline Scope

- User login and role-based access.
- AI map workbench as the user homepage.
- Self-managed POI listing, filtering, detail, and map highlight.
- Mock AI structured JSON with map actions.
- Feedback submission from POI context.
- Admin POI management, feedback review, discovery card management, and AI record review.

## Second Version Focus

- Keep Mock AI stable while adding optional DeepSeek structured intent recognition.
- Use AMap JS API 2.0 when configured, with a built-in demo-map fallback when the key is missing or loading fails.
- Keep AI responses constrained to `intent`, `reply`, `pois`, `toolCalls`, and `mapActions`.
- Require user feedback to bind to an existing POI and make approved changes visible in user POI details.
- Keep NUIST seed data self-managed and source-aware; avoid unverified opening-hour claims.
- Prepare thesis-ready diagrams and test cases in `docs/thesis-materials.md`.

## UI Direction

UI uses UI UX Pro Max guidance:

- Map-directory layout.
- Search-first interaction.
- Data-dense but readable dashboard.
- Professional blue/teal palette with green action states.
- Discovery cards must bind to POI or route experience, not generic forum posts.
