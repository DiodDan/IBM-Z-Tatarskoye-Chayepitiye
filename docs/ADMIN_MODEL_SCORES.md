# Admin model scores

Start PostgreSQL with `make db-up`, the backend with `make backend` (or
`make backend-echo` for replies without an external LLM), and the frontend with
`make frontend`. Open http://localhost:5173/admin. Refresh the Responses panel
after sending a new message in the chat.

The admin chat and response feed display the immutable full-response score from
`ResponseEmbedding.prediction`. Categories are independently scored heads, not
parts of a distribution. Very small scores use scientific notation.

The latest trajectory score comes from `Trajectory.prediction`. It includes all
current members, including later responses; it is not a historical score at the
selected response. Single-member trajectories have no combined score yet.
Association score, raw logit and link threshold describe that member's join
decision. First members have no association decision.

Use **Full model report** for categories, calibration, model fingerprint, token
coverage and separate window-peak evidence. Window peaks are not full-response
probabilities. Uncalibrated sigmoid scores are explicitly labelled.

Use **Trajectory members** to fetch the complete trajectory, ordered by member
ordinal, and jump to any member's response in its conversation. This list is not
restricted by the response feed's 200-row default limit.

Risk sorting uses the current model's atomic assistant score in the database,
before limiting results. Missing current-model scores sort last. The legacy user
prompt score/reason is kept separate and never substituted for a response score.

Viewing the admin page does not run inference or backfill old responses. Missing
analysis for the current model is pending; disabling analysis is shown explicitly.
The existing chronological analysis pipeline processes replies when new messages
are generated. Failed analysis is not persisted by that pipeline, so a later admin
read without a stored embedding reports pending rather than inventing a failure.

Endpoints:
- `GET /api/admin/conversations/{id}`: assistant messages include `modelAssessment`.
- `GET /api/admin/responses?sort=newest|risk-asc|risk-desc|reason`: batched current
  model assessments; `reason` refers to the legacy prompt classifier.
- `GET /api/admin/trajectories/{id}`: latest trajectory scores and all ordered
  members, their atomic scores and association decisions. Unknown IDs return 404.

These projections expose scores and metadata, not embeddings or stored logit
vectors. The existing admin access policy is unchanged.

Verification:
```bash
cd backend
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests '*Admin*'
cd ../frontend
npm run build
```
