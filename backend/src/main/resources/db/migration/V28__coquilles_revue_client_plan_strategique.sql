-- Relecture du Plan Strategique exporte le 14/09/2026 a 09:13 : coquilles restantes.
--
-- « securise » avait recu l'accent du participe la ou la phrase emploie le verbe, et « finance » ou « marque »
-- etaient restes sans le leur ; « Soutien nationale » accordait l'adjectif au mauvais genre. Les textes du plan
-- ecrivaient « manoeuvre » la ou le reste du document ecrit « œuvre ».
--
-- Seules ces formulations exactes sont remplacees : un texte deja reecrit par une direction reste tel quel.
UPDATE section_responses SET content_json = REPLACE(content_json,
    'conformité technique sécurisé la conformité',
    'conformité technique sécurise la conformité')
WHERE content_json LIKE '%conformité technique sécurisé la conformité%';

UPDATE section_responses SET content_json = REPLACE(content_json,
    'du parc validé et finance',
    'du parc validé et financé')
WHERE content_json LIKE '%du parc validé et finance%';

UPDATE section_responses SET content_json = REPLACE(content_json,
    'reste marque par',
    'reste marqué par')
WHERE content_json LIKE '%reste marque par%';

UPDATE section_responses SET content_json = REPLACE(content_json,
    'Soutien nationale à',
    'Soutien national à')
WHERE content_json LIKE '%Soutien nationale à%';

UPDATE psd_narrative_blocks SET content = REPLACE(content, 'manoeuvre', 'manœuvre')
WHERE content LIKE '%manoeuvre%';
