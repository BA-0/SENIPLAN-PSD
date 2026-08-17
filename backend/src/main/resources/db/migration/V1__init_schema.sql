-- SENICO Diagnostic Strategique - Schema initial
-- Charset utf8mb4 pour supporter tous les caracteres (accents francais, emojis eventuels)

CREATE TABLE work_groups (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(150)    NOT NULL,
    description     TEXT,
    leader_user_id  BIGINT          NULL,
    enabled         TINYINT(1)      NOT NULL DEFAULT 1,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE users (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    username        VARCHAR(60)     NOT NULL UNIQUE,
    password_hash   VARCHAR(100)    NOT NULL,
    full_name       VARCHAR(150)    NOT NULL,
    role            VARCHAR(20)     NOT NULL,
    group_id        BIGINT          NULL,
    enabled         TINYINT(1)      NOT NULL DEFAULT 1,
    last_login_at   DATETIME        NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_users_group FOREIGN KEY (group_id) REFERENCES work_groups(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE work_groups
    ADD CONSTRAINT fk_workgroups_leader FOREIGN KEY (leader_user_id) REFERENCES users(id) ON DELETE SET NULL;

CREATE TABLE sections (
    id              INT             PRIMARY KEY,
    code            VARCHAR(20)     NOT NULL UNIQUE,
    title           VARCHAR(200)    NOT NULL,
    display_order   INT             NOT NULL,
    type            VARCHAR(30)     NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE group_section_status (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id        BIGINT          NOT NULL,
    section_id      INT             NOT NULL,
    status          VARCHAR(25)     NOT NULL DEFAULT 'NOT_STARTED',
    submitted_at    DATETIME        NULL,
    validated_at    DATETIME        NULL,
    admin_comment   TEXT            NULL,
    last_activity_at DATETIME       NULL,
    CONSTRAINT fk_gss_group FOREIGN KEY (group_id) REFERENCES work_groups(id) ON DELETE CASCADE,
    CONSTRAINT fk_gss_section FOREIGN KEY (section_id) REFERENCES sections(id) ON DELETE CASCADE,
    CONSTRAINT uq_gss_group_section UNIQUE (group_id, section_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE section_responses (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id        BIGINT          NOT NULL,
    section_id      INT             NOT NULL,
    content_json    JSON            NOT NULL,
    version         INT             NOT NULL DEFAULT 1,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT          NULL,
    CONSTRAINT fk_sr_group FOREIGN KEY (group_id) REFERENCES work_groups(id) ON DELETE CASCADE,
    CONSTRAINT fk_sr_section FOREIGN KEY (section_id) REFERENCES sections(id) ON DELETE CASCADE,
    CONSTRAINT uq_sr_group_section UNIQUE (group_id, section_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE section_response_revisions (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    section_response_id     BIGINT      NOT NULL,
    content_json            JSON        NOT NULL,
    version                 INT         NOT NULL,
    created_at              DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by              BIGINT      NULL,
    CONSTRAINT fk_srr_response FOREIGN KEY (section_response_id) REFERENCES section_responses(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE activity_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id        BIGINT          NULL,
    user_id         BIGINT          NULL,
    action          VARCHAR(50)     NOT NULL,
    section_id      INT             NULL,
    timestamp       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_al_group FOREIGN KEY (group_id) REFERENCES work_groups(id) ON DELETE SET NULL,
    CONSTRAINT fk_al_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_al_section FOREIGN KEY (section_id) REFERENCES sections(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_gss_group ON group_section_status(group_id);
CREATE INDEX idx_gss_section ON group_section_status(section_id);
CREATE INDEX idx_sr_group ON section_responses(group_id);
CREATE INDEX idx_al_timestamp ON activity_log(timestamp);
CREATE INDEX idx_al_group ON activity_log(group_id);
