-- prompt_risk: classifier score for a USER message, 0..1. NULL until the classifier exists / has run.
-- suggestions: short follow-up prompts the model proposed with an ASSISTANT message (JSON array of strings).
-- Both are additive, so existing rows stay valid.

ALTER TABLE message
    ADD COLUMN prompt_risk double precision
        CHECK (prompt_risk IS NULL OR (prompt_risk >= 0 AND prompt_risk <= 1)),
    ADD COLUMN suggestions jsonb NOT NULL DEFAULT '[]'::jsonb;
