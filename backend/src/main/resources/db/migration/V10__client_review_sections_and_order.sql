-- Revue client (presentation PSD 2027-2031) : 5 nouvelles sections du canevas et
-- reordonnancement des onglets existants.
--
-- Nouvelles sections (ids 19-23, hors sequence des ids existants pour ne pas
-- invalider les references fixes de DerivedFieldsService/PsdDocumentStructure) :
--   S01B Analyse des performances de l'annee 2026   -> avant l'analyse des ressources (S02)
--   S03B Synthese de l'analyse des ressources       -> avant le SWOT (S04)
--   S06B Synthese des enjeux et des contraintes     -> avant l'inventaire (S07)
--   S09B Synthese du cadre logique                  -> apres le cadre logique (S09)
--   S14B Plan d'evolution des effectifs             -> avant le plan de financement (S15)
--
-- Reordonnancement demande :
--   - Budget (S11) avant Plan d'actions (S10) avant Cadre de mesure de rendement (S12)
--   - Fiche d'indicateurs (S13) avant Cadre de mesure de rendement (S12)
--   - Matrice des ressources (S02) avant PESTEL (S03)      [deja le cas]
--   - Synthese du cadre strategique (S17) apres Business plan (S16)  [deja le cas]

INSERT INTO sections (id, code, title, display_order, type) VALUES
(19, 'S01B', 'Analyse des performances de l''annee 2026',  2, 'PERFORMANCE_REVIEW_2026'),
(20, 'S03B', 'Synthese de l''analyse des ressources',      5, 'RESOURCES_SYNTHESIS'),
(21, 'S06B', 'Synthese des enjeux et des contraintes',     9, 'CONSTRAINTS_SYNTHESIS'),
(22, 'S09B', 'Synthese du cadre logique',                 14, 'LOGFRAME_SYNTHESIS'),
(23, 'S14B', 'Plan d''evolution des effectifs',           20, 'STAFF_EVOLUTION');

-- Ordre complet reecrit explicitement (plus lisible et plus sur qu'une serie de
-- decalages relatifs : l'ordre cible du client est visible d'un seul coup d'oeil).
UPDATE sections SET display_order = 1  WHERE id = 1;   -- S01  Parties prenantes
UPDATE sections SET display_order = 2  WHERE id = 19;  -- S01B Performances 2026
UPDATE sections SET display_order = 3  WHERE id = 2;   -- S02  Matrice des ressources
UPDATE sections SET display_order = 4  WHERE id = 3;   -- S03  PESTEL
UPDATE sections SET display_order = 5  WHERE id = 20;  -- S03B Synthese des ressources
UPDATE sections SET display_order = 6  WHERE id = 4;   -- S04  SWOT
UPDATE sections SET display_order = 7  WHERE id = 5;   -- S05  TOWS
UPDATE sections SET display_order = 8  WHERE id = 6;   -- S06  Analyse causale
UPDATE sections SET display_order = 9  WHERE id = 21;  -- S06B Enjeux et contraintes
UPDATE sections SET display_order = 10 WHERE id = 7;   -- S07  Inventaire
UPDATE sections SET display_order = 11 WHERE id = 18;  -- S07B Cadre strategique
UPDATE sections SET display_order = 12 WHERE id = 8;   -- S08  Axes strategiques
UPDATE sections SET display_order = 13 WHERE id = 9;   -- S09  Cadre logique
UPDATE sections SET display_order = 14 WHERE id = 22;  -- S09B Synthese du cadre logique
UPDATE sections SET display_order = 15 WHERE id = 11;  -- S11  Budget
UPDATE sections SET display_order = 16 WHERE id = 10;  -- S10  Plan d'actions
UPDATE sections SET display_order = 17 WHERE id = 13;  -- S13  Fiche d'indicateurs
UPDATE sections SET display_order = 18 WHERE id = 12;  -- S12  Cadre de mesure de rendement
UPDATE sections SET display_order = 19 WHERE id = 14;  -- S14  Matrice des risques
UPDATE sections SET display_order = 20 WHERE id = 23;  -- S14B Evolution des effectifs
UPDATE sections SET display_order = 21 WHERE id = 15;  -- S15  Plan de financement
UPDATE sections SET display_order = 22 WHERE id = 16;  -- S16  Business plan
UPDATE sections SET display_order = 23 WHERE id = 17;  -- S17  Synthese du cadre strategique

-- Meme regle qu'a l'initialisation (V2) et qu'au precedent ajout de section (V5) :
-- une ligne NOT_STARTED par groupe pour chaque section du referentiel, sans quoi
-- les nouvelles sections resteraient invisibles dans les tableaux de bord.
INSERT INTO group_section_status (group_id, section_id, status)
SELECT wg.id, s.id, 'NOT_STARTED'
FROM work_groups wg
CROSS JOIN sections s
WHERE NOT EXISTS (
    SELECT 1 FROM group_section_status gss
    WHERE gss.group_id = wg.id AND gss.section_id = s.id
);
