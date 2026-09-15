-- Revue client du 15/09/2026 : les directions de SENICO et leurs responsables.
--
-- Les cinq groupes de travail existants portent le contenu deja approuve : ils sont renommes selon
-- l'organisation du client plutot que recrees vides (Commerciale -> Ventes Locales et Marketing,
-- Technique et Exploitation -> Technique, Financiere et Comptable -> Financiere et Controle de Gestion,
-- Logistique -> Supply Chain ; la DSI garde son nom). Leurs identifiants de connexion ne changent pas.
-- Les sept autres directions et services sont crees, chacun avec sa couleur, ses sections a renseigner
-- et un compte de chef de groupe au nom de son responsable. Ce compte n'a pas de mot de passe connu :
-- l'administrateur lui en attribue un depuis l'ecran des groupes (« Réinitialiser le mot de passe »),
-- que le responsable change a sa premiere connexion.

UPDATE work_groups SET name = 'Direction Ventes Locales et Marketing' WHERE name = 'Direction Commerciale';
UPDATE work_groups SET name = 'Direction Technique' WHERE name = 'Direction Technique et Exploitation';
UPDATE work_groups SET name = 'Direction Financière et Contrôle de Gestion' WHERE name = 'Direction Financière et Comptable';
UPDATE work_groups SET name = 'Direction Supply Chain' WHERE name = 'Direction Logistique';
-- La DSI n'avait pas de couleur choisie : elle garde le violet de repli que les documents lui donnaient deja.
UPDATE work_groups SET color = '#7C3AED' WHERE name = 'Direction des Systèmes d''Information' AND (color IS NULL OR color = '');

-- Les chefs de groupe encore nommes d'apres leur direction prennent le nom de leur responsable.
UPDATE users u JOIN work_groups g ON g.leader_user_id = u.id SET u.full_name = 'Fatou NGOM'
WHERE g.name = 'Direction Ventes Locales et Marketing' AND u.full_name LIKE 'Chef de groupe%';
UPDATE users u JOIN work_groups g ON g.leader_user_id = u.id SET u.full_name = 'Rodrigue NSOH'
WHERE g.name = 'Direction Technique' AND u.full_name LIKE 'Chef de groupe%';
UPDATE users u JOIN work_groups g ON g.leader_user_id = u.id SET u.full_name = 'Mame Marème DIA'
WHERE g.name = 'Direction Financière et Contrôle de Gestion' AND u.full_name LIKE 'Chef de groupe%';

CREATE TEMPORARY TABLE senico_new_directions (
    name VARCHAR(150) NOT NULL,
    color VARCHAR(9) NOT NULL,
    username VARCHAR(60) NOT NULL,
    full_name VARCHAR(150) NOT NULL
) DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;  -- celle de work_groups et users, pour les jointures sur les noms

INSERT INTO senico_new_directions (name, color, username, full_name) VALUES
    ('Direction des Ressources Humaines', '#DB2777', 'dir.rh', 'El Hadj Souleymane NDIAYE'),
    ('Direction des Risques et Conformités', '#0D9488', 'dir.risques', 'Adama NDOYE'),
    ('Direction Industrielle', '#92400E', 'dir.industrielle', 'Basile KOUAMEN'),
    ('Direction QHSE', '#0891B2', 'dir.qhse', 'El Houcine AIT HSINE'),
    ('Direction Générale', '#1E3A8A', 'dir.generale', 'Mouhamed DIA'),
    ('Service Juridique', '#475569', 'svc.juridique', 'Amadou SYLLA DIOP'),
    ('Service R&D', '#4D7C0F', 'svc.rd', 'Ndeye Fatou GUEYE');

INSERT INTO work_groups (name, description, color, enabled)
SELECT n.name, '', n.color, 1
FROM senico_new_directions n
LEFT JOIN work_groups g ON g.name = n.name
WHERE g.id IS NULL;

INSERT INTO users (username, password_hash, must_change_password, full_name, role, group_id, enabled)
SELECT n.username, '$2a$10$RvHiDPreGmB2BmcKO6eyw..MoJQgiXuBgYtII5Hy9bByDFUJxs.wy', 1, n.full_name, 'GROUP_LEADER', g.id, 1
FROM senico_new_directions n
JOIN work_groups g ON g.name = n.name
LEFT JOIN users u ON u.username = n.username
WHERE u.id IS NULL AND g.leader_user_id IS NULL;

UPDATE work_groups g
JOIN senico_new_directions n ON n.name = g.name
JOIN users u ON u.username = n.username
SET g.leader_user_id = u.id
WHERE g.leader_user_id IS NULL;

INSERT INTO group_section_status (group_id, section_id, status)
SELECT g.id, s.id, 'NOT_STARTED'
FROM senico_new_directions n
JOIN work_groups g ON g.name = n.name
CROSS JOIN sections s
LEFT JOIN group_section_status st ON st.group_id = g.id AND st.section_id = s.id
WHERE st.id IS NULL;

DROP TEMPORARY TABLE senico_new_directions;
