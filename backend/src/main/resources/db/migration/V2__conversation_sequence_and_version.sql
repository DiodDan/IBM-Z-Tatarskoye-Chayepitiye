ALTER TABLE conversation ADD COLUMN last_message_sequence bigint NOT NULL DEFAULT 0 CHECK (last_message_sequence >= 0);
ALTER TABLE conversation ADD COLUMN version bigint NOT NULL DEFAULT 0;

-- Existing data is scanned once during migration, never when appending a message.
UPDATE conversation c SET last_message_sequence = x.last_sequence
FROM (SELECT conversation_id, max(sequence) AS last_sequence FROM message GROUP BY conversation_id) x
WHERE c.id = x.conversation_id;
