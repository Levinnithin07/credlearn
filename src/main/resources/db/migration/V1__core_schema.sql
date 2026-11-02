CREATE TABLE users (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 full_name VARCHAR(100) NOT NULL, email VARCHAR(254) NOT NULL UNIQUE,
 password_hash VARCHAR(100) NOT NULL, department VARCHAR(100) NOT NULL,
 year_of_study INT NOT NULL, bio VARCHAR(1000) NOT NULL DEFAULT '',
 compact_view BOOLEAN NOT NULL DEFAULT FALSE,
 created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT chk_year CHECK (year_of_study BETWEEN 1 AND 6)
);
CREATE TABLE skills (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL,
 title VARCHAR(100) NOT NULL, description VARCHAR(2000) NOT NULL,
 category VARCHAR(40) NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
 FOREIGN KEY(user_id) REFERENCES users(id), INDEX idx_skill_discovery(active, category), INDEX idx_skill_owner(user_id)
);
CREATE TABLE learning_interests (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL,
 skill_name VARCHAR(100) NOT NULL, category VARCHAR(40) NOT NULL,
 FOREIGN KEY(user_id) REFERENCES users(id), UNIQUE(user_id, skill_name)
);
CREATE TABLE credit_wallets (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL UNIQUE,
 balance DECIMAL(12,2) NOT NULL, reserved DECIMAL(12,2) NOT NULL DEFAULT 0,
 version BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
 FOREIGN KEY(user_id) REFERENCES users(id),
 CONSTRAINT chk_wallet CHECK (balance >= 0 AND reserved >= 0 AND reserved <= balance)
);
CREATE TABLE session_requests (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, requester_id BIGINT NOT NULL, provider_id BIGINT NOT NULL,
 skill_id BIGINT NOT NULL, starts_at TIMESTAMP(6) NOT NULL, duration_minutes INT NOT NULL,
 status VARCHAR(30) NOT NULL, requester_message VARCHAR(1000) NOT NULL DEFAULT '',
 issue VARCHAR(1000) NOT NULL DEFAULT '', created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
 FOREIGN KEY(requester_id) REFERENCES users(id), FOREIGN KEY(provider_id) REFERENCES users(id),
 FOREIGN KEY(skill_id) REFERENCES skills(id), INDEX idx_session_requester(requester_id, status),
 INDEX idx_session_provider(provider_id, status), INDEX idx_session_time(starts_at),
 CONSTRAINT chk_participants CHECK(requester_id <> provider_id),
 CONSTRAINT chk_duration CHECK(duration_minutes BETWEEN 30 AND 240 AND MOD(duration_minutes,15)=0)
);
CREATE TABLE session_confirmations (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, session_id BIGINT NOT NULL, user_id BIGINT NOT NULL, confirmed_at TIMESTAMP(6) NOT NULL,
 FOREIGN KEY(session_id) REFERENCES session_requests(id), FOREIGN KEY(user_id) REFERENCES users(id), UNIQUE(session_id,user_id)
);
CREATE TABLE credit_transactions (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, session_id BIGINT NULL UNIQUE,
 sender_id BIGINT NULL, receiver_id BIGINT NOT NULL, amount DECIMAL(12,2) NOT NULL,
 transaction_type VARCHAR(30) NOT NULL, created_at TIMESTAMP(6) NOT NULL,
 starter_user_id BIGINT NULL UNIQUE,
 FOREIGN KEY(session_id) REFERENCES session_requests(id), FOREIGN KEY(sender_id) REFERENCES users(id),
 FOREIGN KEY(receiver_id) REFERENCES users(id), FOREIGN KEY(starter_user_id) REFERENCES users(id),
 INDEX idx_tx_sender(sender_id,created_at), INDEX idx_tx_receiver(receiver_id,created_at),
 CONSTRAINT chk_amount CHECK(amount > 0),
 CONSTRAINT chk_transaction CHECK (
  (transaction_type='STARTER' AND session_id IS NULL AND sender_id IS NULL AND starter_user_id=receiver_id AND starter_user_id IS NOT NULL)
  OR (transaction_type='SESSION' AND session_id IS NOT NULL AND sender_id IS NOT NULL AND sender_id<>receiver_id AND starter_user_id IS NULL))
);
