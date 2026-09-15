-- Revue client du Plan Strategique et de sa note de synthese : le cadre logique et le cadre de mesure
-- de rendement de la Direction Logistique renvoyaient encore a « section 13 », le code interne de la
-- fiche d'indicateurs dans le canevas. Le lecteur ne connait pas ces codes : le renvoi nomme la fiche.
--
-- Seules ces formulations exactes sont remplacees : un texte deja reecrit par une direction reste tel quel.
UPDATE section_responses SET content_json = REPLACE(content_json,
    '(cf. fiche d''indicateurs, section 13)',
    '(cf. fiche des indicateurs)')
WHERE content_json LIKE '%(cf. fiche d''indicateurs, section 13)%';

UPDATE section_responses SET content_json = REPLACE(content_json,
    '(cf. section 13)',
    '(cf. fiche des indicateurs)')
WHERE content_json LIKE '%(cf. section 13)%';
