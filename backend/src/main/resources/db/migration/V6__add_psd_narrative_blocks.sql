-- Blocs de texte narratif saisis par l'admin pour le "Document final PSD 2027-2031"
-- (parties du sommaire qui ne proviennent d'aucune des 17 sections du canevas :
-- Mot du DG, Preambule, Introduction, Presentation de la structure, Defis a relever, Enjeux).

CREATE TABLE psd_narrative_blocks (
    block_key VARCHAR(40) NOT NULL PRIMARY KEY,
    content TEXT NULL,
    updated_at TIMESTAMP NULL,
    updated_by VARCHAR(100) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO psd_narrative_blocks (block_key, content) VALUES
('MOT_DU_DG', ''),
('PREAMBULE', ''),
('INTRODUCTION', ''),
('MISSIONS', ''),
('ORGANISATION', ''),
('RESSOURCES', ''),
('DEFIS_A_RELEVER', ''),
('ENJEUX', '');
