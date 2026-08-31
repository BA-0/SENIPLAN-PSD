-- Ajoute la notion de cycle de saisie par direction : une fois toutes les sections
-- soumises, l'admin peut demarrer un nouveau cycle. Le contenu du cycle cloture est
-- fige dans group_cycle_archives (jamais supprime) avant que les statuts/contenus
-- courants soient remis a NOT_STARTED pour la nouvelle saisie.

ALTER TABLE work_groups
    ADD COLUMN current_cycle INT NOT NULL DEFAULT 1;

CREATE TABLE group_cycle_archives (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id        BIGINT          NOT NULL,
    cycle_number    INT             NOT NULL,
    section_id      INT             NOT NULL,
    content_json    JSON            NOT NULL,
    status          VARCHAR(25)     NOT NULL,
    submitted_at    DATETIME        NULL,
    validated_at    DATETIME        NULL,
    admin_comment   TEXT            NULL,
    archived_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    archived_by     BIGINT          NULL,
    CONSTRAINT fk_gca_group FOREIGN KEY (group_id) REFERENCES work_groups(id) ON DELETE CASCADE,
    CONSTRAINT fk_gca_section FOREIGN KEY (section_id) REFERENCES sections(id) ON DELETE CASCADE,
    CONSTRAINT uq_gca_group_cycle_section UNIQUE (group_id, cycle_number, section_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_gca_group_cycle ON group_cycle_archives(group_id, cycle_number);
