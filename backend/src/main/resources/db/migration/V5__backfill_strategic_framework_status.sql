-- La section 18 (Cadre strategique, S07B) a ete ajoutee par V3 apres la creation
-- des groupes de travail existants. Ceux-ci n'ont donc pas de ligne dans
-- group_section_status pour cette section, ce qui la rend invisible dans les
-- tableaux de bord (listStatuses ne renvoie que les lignes deja presentes).
-- On retablit ici la meme regle qu'a l'initialisation (V2) : une ligne
-- NOT_STARTED par groupe pour chaque section du referentiel.

INSERT INTO group_section_status (group_id, section_id, status)
SELECT wg.id, s.id, 'NOT_STARTED'
FROM work_groups wg
CROSS JOIN sections s
WHERE NOT EXISTS (
    SELECT 1 FROM group_section_status gss
    WHERE gss.group_id = wg.id AND gss.section_id = s.id
);
