-- Schema from initial-docs/backend-requirements.md §3.
-- Flyway applies this on startup; Hibernate only validates it (ddl-auto: validate).

CREATE TABLE app_user (
    id          uuid        PRIMARY KEY,
    external_id uuid        NOT NULL,
    created_at  timestamptz NOT NULL,
    CONSTRAINT uq_app_user_external_id UNIQUE (external_id)
);

CREATE TABLE conversation (
    id         uuid        PRIMARY KEY,
    user_id    uuid        NOT NULL REFERENCES app_user (id),
    title      varchar(80) NOT NULL CHECK (btrim(title) <> ''),
    created_at timestamptz NOT NULL
);

CREATE INDEX ix_conversation_user_created
    ON conversation (user_id, created_at DESC, id ASC);

CREATE TABLE message (
    id              uuid        PRIMARY KEY,
    conversation_id uuid        NOT NULL REFERENCES conversation (id) ON DELETE CASCADE,
    sequence        bigint      NOT NULL CHECK (sequence > 0),
    role            varchar(16) NOT NULL CHECK (role IN ('USER', 'ASSISTANT')),
    content         text        NOT NULL CHECK (btrim(content) <> ''),
    created_at      timestamptz NOT NULL,
    CONSTRAINT uq_message_conversation_sequence UNIQUE (conversation_id, sequence)
);
