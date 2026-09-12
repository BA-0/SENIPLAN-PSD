-- Seconde revue client de la note de synthese.
--
-- 1. La note est datee de septembre 2026 : l'exercice 2026 n'est pas clos, ses realisations sont
--    estimees a date. Les textes de recette ne presentent plus les cibles 2026 comme manquees.
-- 2. Le preambule sert a la fois au Plan Strategique complet et a sa note de synthese : il ne dit
--    plus « le present document constitue le Plan Strategique », faux dans la note.
-- 3. Les effectifs 2027-2031 sont une prevision : ils « progresseront ».
--
-- Seules les phrases de recette sont remplacees, a l'identique : un texte deja reecrit par la
-- Direction Generale reste tel quel.
UPDATE psd_narrative_blocks SET content = REPLACE(content,
    'L''analyse des performances de l''année 2026 en donne la mesure : les cibles de délai, de disponibilité du parc et de couverture numérique n''ont pas été atteintes.',
    'L''analyse des performances de l''année 2026, établie à date par chaque direction, en donne la mesure : les cibles de délai, de disponibilité du parc et de couverture numérique restent à ce stade hors d''atteinte.')
WHERE block_key = 'INTRODUCTION';

UPDATE psd_narrative_blocks SET content = REPLACE(content,
    'L''analyse des performances de l''exercice 2026, conduite par chaque direction, montre que les cibles de délai d''acheminement, de disponibilité du parc et de couverture numérique n''ont pas été atteintes.',
    'L''analyse des performances de l''exercice 2026, établie à date par chaque direction, montre que les cibles de délai d''acheminement, de disponibilité du parc et de couverture numérique restent à ce stade hors d''atteinte.')
WHERE block_key = 'BILAN_PSD_PRECEDENT';

UPDATE psd_narrative_blocks SET content = REPLACE(content,
    'Le présent document constitue le Plan Stratégique de SENICO SA pour la période 2027-2031.',
    'Le Plan Stratégique de SENICO SA couvre la période 2027-2031.')
WHERE block_key = 'PREAMBULE';

UPDATE psd_narrative_blocks SET content = REPLACE(content,
    'les effectifs projetés dans le plan d''évolution des effectifs progressent sur la période 2027-2031',
    'les effectifs projetés dans le plan d''évolution des effectifs progresseront sur la période 2027-2031')
WHERE block_key = 'RESSOURCES';

-- Le nom de la direction s'affiche dans la legende des couleurs : il prend ses accents.
UPDATE work_groups SET name = 'Direction Financière et Comptable'
WHERE name = 'Direction Financiere et Comptable';
