-- SENICO n'est pas encore dotee d'un « Plan Strategique de Developpement » : le document
-- s'intitule « Plan Strategique 2027-2031 ». Les textes arretes par la Direction Generale
-- reprenaient l'ancienne appellation ; on la remplace la ou elle figure, sans toucher au reste.
UPDATE psd_narrative_blocks
SET content = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(content,
        'Plan Stratégique de Développement (PSD)', 'Plan Stratégique'),
        'Plan Stratégique de Développement', 'Plan Stratégique'),
        'plan stratégique de développement', 'plan stratégique'),
        'du PSD', 'du Plan Stratégique'),
        'le PSD', 'le Plan Stratégique'),
        'PSD ', 'Plan Stratégique ')
WHERE content LIKE '%PSD%' OR content LIKE '%de Développement%' OR content LIKE '%de développement%';
