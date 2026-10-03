-- Copies entieres de la note envoyees par des onglets ouverts avant la mise en ligne de la fusion
-- (03/10/2026). Le serveur ne les applique pas, elles ecraseraient les corrections des autres postes,
-- mais il les garde : les corrections qu'elles contiennent peuvent ensuite etre reintegrees.

CREATE TABLE synthesis_note_rejected_saves (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    received_at TIMESTAMP NOT NULL,
    updated_by VARCHAR(100) NULL,
    content LONGTEXT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
