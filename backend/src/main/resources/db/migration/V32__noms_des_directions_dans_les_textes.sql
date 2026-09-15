-- Revue client du 15/09/2026 : les textes des directions et de la Direction Generale reprennent les noms des
-- directions renommees par V29, et ne parlent plus de « cinq » directions (l'entreprise en compte dix, et deux
-- services). « Chef de groupe - Direction Logistique » etait le nom du compte, repris par defaut comme responsable :
-- la structure responsable est la direction. REPLACE respecte la casse : seules les formulations exactes changent.

UPDATE section_responses SET content_json = REPLACE(content_json, 'Chef de groupe - Direction Logistique', 'Direction Supply Chain')
WHERE content_json LIKE '%Chef de groupe - Direction Logistique%';
UPDATE section_responses SET content_json = REPLACE(content_json, 'Direction Logistique', 'Direction Supply Chain')
WHERE content_json LIKE '%Direction Logistique%';
UPDATE section_responses SET content_json = REPLACE(content_json, 'La direction logistique', 'La Direction Supply Chain')
WHERE content_json LIKE '%La direction logistique%';
UPDATE section_responses SET content_json = REPLACE(content_json, 'Direction Technique et Exploitation', 'Direction Technique')
WHERE content_json LIKE '%Direction Technique et Exploitation%';
UPDATE section_responses SET content_json = REPLACE(content_json, 'Direction Financière et Comptable', 'Direction Financière et Contrôle de Gestion')
WHERE content_json LIKE '%Direction Financière et Comptable%';
UPDATE section_responses SET content_json = REPLACE(content_json, 'Direction Commerciale', 'Direction Ventes Locales et Marketing')
WHERE content_json LIKE '%Direction Commerciale%';

UPDATE psd_narrative_blocks SET content = REPLACE(content, 'Les cinq groupes de travail ont conduit', 'Les groupes de travail des directions ont conduit')
WHERE block_key = 'MOT_DU_DG';
UPDATE psd_narrative_blocks SET content = REPLACE(content, 'commun aux cinq directions', 'commun à l''ensemble des directions')
WHERE block_key = 'APPROCHE_METHODOLOGIQUE';
UPDATE psd_narrative_blocks SET content = REPLACE(content, 'La consolidation des diagnostics des cinq directions', 'La consolidation des diagnostics des directions')
WHERE block_key = 'DEFIS_A_RELEVER';
UPDATE psd_narrative_blocks SET content = REPLACE(content, 'conduit par les cinq directions', 'conduit par les directions')
WHERE block_key = 'CONCLUSION';
