-- Retrait du canevas de la section S16 "Business plan" (demande client) :
-- elle disparait du referentiel, donc de la saisie, des tableaux de bord et de
-- tous les exports. Le type BUSINESS_PLAN n'existant plus cote applicatif, la
-- ligne ne peut pas etre simplement masquee : elle est supprimee.
--
-- ATTENTION, suppression definitive : les cles etrangeres sur sections(id) sont
-- toutes ON DELETE CASCADE, si bien que les reponses deja saisies par les
-- directions sur S16 (section_responses et leurs revisions, group_section_status
-- et les archives de cycles group_cycle_archives) partent avec la section.
-- Le journal d'activite, lui, est en ON DELETE SET NULL : ses entrees restent,
-- rattachees a aucune section.
DELETE FROM sections WHERE code = 'S16';

-- L'ordre d'affichage doit rester une suite continue : S17, qui suivait S16,
-- prend sa place (22) plutot que de laisser un trou.
UPDATE sections SET display_order = 22 WHERE code = 'S17';

-- Le preambule du document final decrivait le canevas comme allant "de l'analyse
-- des parties prenantes jusqu'au business plan a cinq ans" : la phrase n'a plus
-- d'objet. Le bloc narratif restant editable par l'admin, on ne reecrit que si la
-- formule d'origine est encore la, pour ne pas ecraser une reformulation.
UPDATE psd_narrative_blocks
SET content = REPLACE(
        content,
        'de l''analyse des parties prenantes jusqu''au business plan à cinq ans',
        'de l''analyse des parties prenantes jusqu''au plan de financement')
WHERE block_key = 'PREAMBULE'
  AND content LIKE '%business plan à cinq ans%';
