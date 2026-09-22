-- Les titres des sections, herites du referentiel initial (V2) sans accents, s'affichent tels quels dans
-- l'application (tableau de bord, navigation, en-tete de chaque section, comparaisons) et nomment les
-- onglets de l'export Excel, alors que la note de synthese et le Plan Strategique sont accentues.
-- Les codes (S01...S17) et l'ordre ne changent pas : rien dans le code ne compare ces titres.
UPDATE sections SET title = 'Bilan des performances des années passées et de 2026' WHERE code = 'S01B';
UPDATE sections SET title = 'Matrice d''analyse des ressources et compétences' WHERE code = 'S02';
UPDATE sections SET title = 'Synthèse de l''analyse des ressources' WHERE code = 'S03B';
UPDATE sections SET title = 'Mise en relation du diagnostic stratégique (matrice de confrontation SWOT/TOWS)' WHERE code = 'S05';
UPDATE sections SET title = 'Synthèse des enjeux et des contraintes' WHERE code = 'S06B';
UPDATE sections SET title = 'Cadre stratégique (Mission, Valeurs, Vision)' WHERE code = 'S07B';
UPDATE sections SET title = 'Axes stratégiques / Orientations' WHERE code = 'S08';
UPDATE sections SET title = 'Synthèse du cadre logique' WHERE code = 'S09B';
UPDATE sections SET title = 'Plan d''évolution des effectifs' WHERE code = 'S14B';
UPDATE sections SET title = 'Tableau de synthèse du cadre stratégique' WHERE code = 'S17';
