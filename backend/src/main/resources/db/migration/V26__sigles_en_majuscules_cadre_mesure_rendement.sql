-- Revue client du Plan Strategique : trois indicateurs du cadre de mesure de rendement ecrivaient
-- leurs sigles en minuscules (« deployer un crm commercial », « schema directeur si », « gouvernance si »),
-- et le recapitulatif des axes les reprenait tels quels.
--
-- Seules ces formulations exactes sont remplacees : un texte deja reecrit par une direction reste tel quel.
UPDATE section_responses SET content_json = REPLACE(content_json,
    'deployer un crm commercial',
    'deployer un CRM commercial')
WHERE content_json LIKE '%deployer un crm commercial%';

UPDATE section_responses SET content_json = REPLACE(content_json,
    'schema directeur si"',
    'schema directeur SI"')
WHERE content_json LIKE '%schema directeur si"%';

UPDATE section_responses SET content_json = REPLACE(content_json,
    'gouvernance si"',
    'gouvernance SI"')
WHERE content_json LIKE '%gouvernance si"%';
