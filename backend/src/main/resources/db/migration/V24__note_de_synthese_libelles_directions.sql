-- Troisieme revue client de la note de synthese : la note reprend desormais les syntheses propres
-- a chaque direction (analyse des ressources, cadre logique). Deux phrases de recette y portaient
-- un vocabulaire que le client ne veut pas voir :
--
-- 1. « PSD » : SENICO SA n'a pas de plan strategique de developpement, on parle du Plan Strategique.
-- 2. « la fiche S13 » : le code interne de la section, inconnu du lecteur de la note.
--
-- Seules ces phrases exactes sont remplacees : un texte deja reecrit par une direction reste tel quel.
UPDATE section_responses SET content_json = REPLACE(content_json,
    'Les defis prioritaires retenus pour le PSD 2027-2031',
    'Les defis prioritaires retenus pour le Plan Stratégique 2027-2031')
WHERE content_json LIKE '%Les defis prioritaires retenus pour le PSD 2027-2031%';

UPDATE section_responses SET content_json = REPLACE(content_json,
    'Les extrants sont mesures par les indicateurs de la fiche S13',
    'Les extrants sont mesures par les indicateurs objectivement verifiables')
WHERE content_json LIKE '%Les extrants sont mesures par les indicateurs de la fiche S13%';
