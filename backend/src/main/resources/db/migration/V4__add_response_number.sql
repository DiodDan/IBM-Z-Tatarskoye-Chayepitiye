-- response_number: a permanent, app-wide number for every ASSISTANT reply ("Response №12" on the admin page).
-- Admins copy it and paste it into the admin search to find the conversation it belongs to.
-- It never changes, even when other conversations are deleted. NULL for user rows.

CREATE SEQUENCE response_number_seq;

ALTER TABLE message ADD COLUMN response_number bigint;

-- Number the replies that already exist, oldest first.
UPDATE message m
SET response_number = numbered.n
FROM (
    SELECT id, row_number() OVER (ORDER BY created_at, conversation_id, sequence) AS n
    FROM message
    WHERE role = 'ASSISTANT'
) AS numbered
WHERE m.id = numbered.id;

SELECT setval('response_number_seq', COALESCE((SELECT max(response_number) FROM message), 0) + 1, false);

ALTER TABLE message
    ADD CONSTRAINT uq_message_response_number UNIQUE (response_number),
    ADD CONSTRAINT ck_message_response_number CHECK ((role = 'ASSISTANT') = (response_number IS NOT NULL));
