-- Versions de la note de synthese telles que les postes les ont chargees, par empreinte SHA-256.
-- Plusieurs personnes corrigent la note en meme temps : chacune n'envoie que les blocs qu'elle a
-- modifies et la version dont elle est partie, que le serveur retrouve ici pour fusionner ses
-- corrections avec celles enregistrees entre-temps. Purgee au bout de 24 heures.

CREATE TABLE synthesis_note_versions (
    version CHAR(64) NOT NULL PRIMARY KEY,
    content LONGTEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    INDEX idx_synthesis_note_versions_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
