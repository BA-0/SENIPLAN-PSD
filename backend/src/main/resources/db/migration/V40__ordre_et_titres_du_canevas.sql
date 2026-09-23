-- Demande client du 23/09/2026 : l'ordre et les titres des tableaux sont ceux du canevas
-- « Diagnostic strategique VF ».
-- 1. Ordre : 10. Plan d'actions, 11. Budget, 12. Cadre de mesure de rendement, 13. Fiche d'indicateurs
--    (la revue client de V10 avait place le budget avant le plan d'actions et la fiche avant le cadre).
UPDATE sections SET display_order = 15 WHERE code = 'S10';
UPDATE sections SET display_order = 16 WHERE code = 'S11';
UPDATE sections SET display_order = 17 WHERE code = 'S12';
UPDATE sections SET display_order = 18 WHERE code = 'S13';

-- 2. Titres du canevas. S17 garde son titre sans « PSD » (regle client, cf. V37).
UPDATE sections SET title = 'Matrice d''analyse de ressources et de compétences' WHERE code = 'S02';
UPDATE sections SET title = 'Mise en relation du diagnostic stratégique externe' WHERE code = 'S05';
UPDATE sections SET title = 'Synthèse des contraintes, enjeux, défis et priorités identifiés' WHERE code = 'S06B';
UPDATE sections SET title = 'Matrice d''analyse des risques liés aux domaines d''intervention (ou activités)' WHERE code = 'S14';
UPDATE sections SET title = 'Plan d''évolution des effectifs (statut, hiérarchie, genre)' WHERE code = 'S14B';
