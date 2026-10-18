-- Native arrays are sufficient: candidate selection is by conversation, not vector distance.
-- No pgvector server extension or approximate-neighbour index is required for this workflow.
CREATE TABLE analysis_cursor (
    id uuid PRIMARY KEY,
    conversation_id uuid NOT NULL REFERENCES conversation(id) ON DELETE CASCADE,
    model_fingerprint varchar(64) NOT NULL,
    last_message_sequence bigint NOT NULL DEFAULT 0 CHECK (last_message_sequence >= 0 AND last_message_sequence % 2 = 0),
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_analysis_cursor_release UNIQUE (conversation_id, model_fingerprint)
);

CREATE TABLE response_embedding (
    id uuid PRIMARY KEY,
    response_id uuid NOT NULL REFERENCES message(id) ON DELETE CASCADE,
    model_fingerprint varchar(64) NOT NULL,
    embedding real[] NOT NULL CHECK (array_ndims(embedding) = 1 AND cardinality(embedding) = 256 AND array_position(embedding, NULL) IS NULL),
    risk_logits real[] NOT NULL CHECK (array_ndims(risk_logits) = 1 AND cardinality(risk_logits) = 10 AND array_position(risk_logits, NULL) IS NULL),
    overall_score real NOT NULL CHECK (overall_score BETWEEN 0 AND 1),
    category_scores jsonb NOT NULL CHECK (jsonb_typeof(category_scores) = 'object' AND category_scores ?& ARRAY['violence_weapons','bio_chemical','malicious_cyber','fraud','illicit_evasion','hate_threats_harassment','privacy_stalking','sexual_exploitation','self_harm_facilitation'] AND category_scores - ARRAY['violence_weapons','bio_chemical','malicious_cyber','fraud','illicit_evasion','hate_threats_harassment','privacy_stalking','sexual_exploitation','self_harm_facilitation'] = '{}'::jsonb),
    calibration_id varchar(120) NOT NULL,
    calibration_applied boolean NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT uq_response_embedding_release UNIQUE (response_id, model_fingerprint)
);

CREATE TABLE trajectory (
    id uuid PRIMARY KEY,
    conversation_id uuid NOT NULL REFERENCES conversation(id) ON DELETE CASCADE,
    model_fingerprint varchar(64) NOT NULL,
    embedding real[] NOT NULL CHECK (array_ndims(embedding) = 1 AND cardinality(embedding) = 256 AND array_position(embedding, NULL) IS NULL),
    risk_logits real[] NOT NULL CHECK (array_ndims(risk_logits) = 1 AND cardinality(risk_logits) = 10 AND array_position(risk_logits, NULL) IS NULL),
    overall_score real NOT NULL CHECK (overall_score BETWEEN 0 AND 1),
    category_scores jsonb NOT NULL CHECK (jsonb_typeof(category_scores) = 'object' AND category_scores ?& ARRAY['violence_weapons','bio_chemical','malicious_cyber','fraud','illicit_evasion','hate_threats_harassment','privacy_stalking','sexual_exploitation','self_harm_facilitation'] AND category_scores - ARRAY['violence_weapons','bio_chemical','malicious_cyber','fraud','illicit_evasion','hate_threats_harassment','privacy_stalking','sexual_exploitation','self_harm_facilitation'] = '{}'::jsonb),
    calibration_id varchar(120) NOT NULL,
    calibration_applied boolean NOT NULL,
    response_count integer NOT NULL CHECK (response_count > 0),
    first_position bigint NOT NULL CHECK (first_position > 0),
    last_position bigint NOT NULL CHECK (last_position >= first_position),
    first_occurred_at timestamptz,
    last_occurred_at timestamptz,
    status varchar(16) NOT NULL CHECK (status IN ('ACTIVE', 'CLOSED')),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CHECK (updated_at >= created_at AND expires_at > updated_at)
);

CREATE INDEX ix_trajectory_active ON trajectory(conversation_id, model_fingerprint, status, expires_at);

CREATE TABLE trajectory_member (
    id uuid PRIMARY KEY,
    trajectory_id uuid NOT NULL REFERENCES trajectory(id) ON DELETE CASCADE,
    response_embedding_id uuid NOT NULL REFERENCES response_embedding(id) ON DELETE CASCADE,
    ordinal integer NOT NULL CHECK (ordinal > 0),
    association_logit real,
    association_score real CHECK (association_score BETWEEN 0 AND 1),
    link_threshold real CHECK (link_threshold BETWEEN 0 AND 1),
    CHECK ((association_logit IS NULL) = (association_score IS NULL)),
    CHECK ((association_score IS NULL) = (link_threshold IS NULL)),
    CONSTRAINT uq_trajectory_member_response UNIQUE (response_embedding_id),
    CONSTRAINT uq_trajectory_member_ordinal UNIQUE (trajectory_id, ordinal)
);
