CREATE TABLE conversations (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, session_id BIGINT NOT NULL UNIQUE,
 created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
 FOREIGN KEY(session_id) REFERENCES session_requests(id)
);
INSERT INTO conversations(session_id, created_at, updated_at)
 SELECT id, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6) FROM session_requests
 WHERE status IN ('ACCEPTED','DISPUTED','COMPLETED');

CREATE TABLE chat_messages (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, conversation_id BIGINT NOT NULL, sender_id BIGINT NOT NULL,
 client_message_id VARCHAR(36) NOT NULL, message_content VARCHAR(2000) NOT NULL,
 sent_at TIMESTAMP(6) NOT NULL, read_at TIMESTAMP(6) NULL,
 FOREIGN KEY(conversation_id) REFERENCES conversations(id), FOREIGN KEY(sender_id) REFERENCES users(id),
 UNIQUE(conversation_id, sender_id, client_message_id),
 INDEX idx_chat_history(conversation_id,id), INDEX idx_chat_unread(conversation_id,read_at,sender_id)
);
ALTER TABLE session_requests
 ADD COLUMN proposed_starts_at TIMESTAMP(6) NULL,
 ADD COLUMN proposed_duration_minutes INT NULL,
 ADD COLUMN schedule_proposed_by BIGINT NULL,
 ADD COLUMN schedule_revision BIGINT NOT NULL DEFAULT 0,
 ADD CONSTRAINT fk_schedule_proposer FOREIGN KEY(schedule_proposed_by) REFERENCES users(id),
 ADD CONSTRAINT chk_schedule_proposal CHECK (
  (proposed_starts_at IS NULL AND proposed_duration_minutes IS NULL AND schedule_proposed_by IS NULL)
  OR (proposed_starts_at IS NOT NULL AND proposed_duration_minutes BETWEEN 30 AND 240
      AND MOD(proposed_duration_minutes,15)=0 AND schedule_proposed_by IS NOT NULL));

CREATE TABLE meeting_details (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, session_id BIGINT NOT NULL UNIQUE,
 external_event_id VARCHAR(64) NOT NULL UNIQUE, conference_request_id VARCHAR(64) NOT NULL,
 google_subject VARCHAR(255) NOT NULL, created_by BIGINT NOT NULL,
 meeting_url VARCHAR(500) NULL, status VARCHAR(20) NOT NULL,
 scheduled_start TIMESTAMP(6) NOT NULL, scheduled_end TIMESTAMP(6) NOT NULL,
 last_error VARCHAR(400) NOT NULL DEFAULT '', lease_until TIMESTAMP(6) NULL,
 created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
 FOREIGN KEY(session_id) REFERENCES session_requests(id), FOREIGN KEY(created_by) REFERENCES users(id),
 CONSTRAINT chk_meeting_status CHECK(status IN ('CREATING','PENDING','READY','ERROR'))
);
