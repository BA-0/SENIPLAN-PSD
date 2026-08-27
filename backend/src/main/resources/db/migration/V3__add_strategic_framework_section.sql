-- Ajout d'une 18e section "Cadre strategique" (Mission, Valeurs, Vision),
-- inseree avant les Axes strategiques (id 8). On decale uniquement l'ordre
-- d'affichage des sections existantes (id 8 a 17) : leurs id/code/type ne
-- changent pas, donc les references fixes (ex. DerivedFieldsService) restent valides.

UPDATE sections SET display_order = display_order + 1 WHERE id >= 8;

INSERT INTO sections (id, code, title, display_order, type) VALUES
(18, 'S07B', 'Cadre strategique (Mission, Valeurs, Vision)', 8, 'STRATEGIC_FRAMEWORK');
