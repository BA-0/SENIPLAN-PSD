-- Note de synthese corrigee par la Direction Generale (demande client du 03/10/2026).
-- Une seule ligne (id = 1) : le contenu de la note tel que le DG l'a arrete, en JSON, et
-- l'empreinte du document genere sur lequel il a travaille, pour signaler qu'une direction
-- a depuis fait approuver de nouveaux contenus.

CREATE TABLE synthesis_note_edits (
    id INT NOT NULL PRIMARY KEY,
    content LONGTEXT NOT NULL,
    source_hash VARCHAR(64) NULL,
    updated_at TIMESTAMP NULL,
    updated_by VARCHAR(100) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
