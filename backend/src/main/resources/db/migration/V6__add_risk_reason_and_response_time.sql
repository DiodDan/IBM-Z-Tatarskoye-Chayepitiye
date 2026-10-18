-- prompt_risk_reason: the classifier's explanation for prompt_risk (USER rows). NULL until classified.
-- response_time_ms: how long the model took to answer (ASSISTANT rows). NULL for rows saved before this column.
-- Both are admin-only data, never returned by the chat API.

ALTER TABLE message
    ADD COLUMN prompt_risk_reason text,
    ADD COLUMN response_time_ms bigint CHECK (response_time_ms IS NULL OR response_time_ms >= 0);
