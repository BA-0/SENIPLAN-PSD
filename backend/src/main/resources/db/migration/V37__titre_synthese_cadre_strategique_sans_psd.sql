-- Regle client (revue de la note de synthese, 11/09/2026) : SENICO SA n'a pas de plan strategique de
-- developpement, on ne parle que du « Plan Strategique 2027-2031 », jamais de « PSD ». Le titre de la
-- section S17, herite du referentiel initial (V2), portait encore ce sigle et s'affichait tel quel dans
-- l'application (tableau de bord, navigation, page de la section, comparaisons) et dans l'export Excel.
UPDATE sections SET title = 'Tableau de synthese du cadre strategique'
WHERE id = 17 AND code = 'S17';
