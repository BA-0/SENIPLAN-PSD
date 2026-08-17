-- Referentiel des 17 sections du canevas de diagnostic strategique PSD 2027-2031

INSERT INTO sections (id, code, title, display_order, type) VALUES
(1,  'S01', 'Analyse des parties prenantes',                                            1,  'STAKEHOLDERS'),
(2,  'S02', 'Matrice d''analyse des ressources et competences',                         2,  'RESOURCES_MATRIX'),
(3,  'S03', 'Analyse PESTEL',                                                           3,  'PESTEL'),
(4,  'S04', 'Analyse SWOT (FFOM)',                                                      4,  'SWOT'),
(5,  'S05', 'Mise en relation du diagnostic strategique (matrice de confrontation SWOT/TOWS)', 5,  'TOWS_MATRIX'),
(6,  'S06', 'Analyse causale',                                                          6,  'CAUSAL_ANALYSIS'),
(7,  'S07', 'Inventaire',                                                               7,  'INVENTORY'),
(8,  'S08', 'Axes strategiques / Orientations',                                        8,  'STRATEGIC_AXES'),
(9,  'S09', 'Cadre logique',                                                            9,  'LOGICAL_FRAMEWORK'),
(10, 'S10', 'Plan d''actions 2027-2031',                                                10, 'ACTION_PLAN'),
(11, 'S11', 'Budget 2027-2031',                                                         11, 'BUDGET'),
(12, 'S12', 'Cadre de mesure de rendement 2027-2031',                                   12, 'PERFORMANCE_FRAMEWORK'),
(13, 'S13', 'Fiche d''indicateurs',                                                     13, 'INDICATOR_SHEET'),
(14, 'S14', 'Matrice d''analyse des risques',                                           14, 'RISK_MATRIX'),
(15, 'S15', 'Plan de financement',                                                      15, 'FINANCING_PLAN'),
(16, 'S16', 'Business plan',                                                            16, 'BUSINESS_PLAN'),
(17, 'S17', 'Tableau de synthese du cadre strategique - PSD 2027-2031',                 17, 'STRATEGIC_SUMMARY');

-- Groupes de travail de demonstration (departements)
INSERT INTO work_groups (id, name, description, enabled) VALUES
(1, 'Direction Commerciale',              'Groupe de travail - Diagnostic strategique PSD 2027-2031', 1),
(2, 'Direction Technique et Exploitation','Groupe de travail - Diagnostic strategique PSD 2027-2031', 1),
(3, 'Direction Financiere et Comptable',  'Groupe de travail - Diagnostic strategique PSD 2027-2031', 1);

-- Comptes de demonstration
-- Admin      : admin / Admin@2027
-- Chefs de groupe : dir.commerciale / dir.technique / dir.financiere : Groupe@2027
INSERT INTO users (username, password_hash, full_name, role, group_id, enabled) VALUES
('admin',           '$2b$10$Izo6uYhRYY4.qFOY054DhOsJL97QZTHSw8qr3kaM7KM6CCqvrbT5u', 'Administrateur SENICO',                          'ADMIN',        NULL, 1),
('dir.commerciale', '$2b$10$NWN3T2kE77OGWhBu6pDkcusLcTTRCe3aMW8Zkfp64ung9G6DNWbiu', 'Chef de groupe - Direction Commerciale',         'GROUP_LEADER', 1,    1),
('dir.technique',   '$2b$10$NWN3T2kE77OGWhBu6pDkcusLcTTRCe3aMW8Zkfp64ung9G6DNWbiu', 'Chef de groupe - Direction Technique et Exploitation', 'GROUP_LEADER', 2, 1),
('dir.financiere',  '$2b$10$NWN3T2kE77OGWhBu6pDkcusLcTTRCe3aMW8Zkfp64ung9G6DNWbiu', 'Chef de groupe - Direction Financiere et Comptable', 'GROUP_LEADER', 3, 1);

UPDATE work_groups SET leader_user_id = (SELECT id FROM users WHERE username = 'dir.commerciale') WHERE id = 1;
UPDATE work_groups SET leader_user_id = (SELECT id FROM users WHERE username = 'dir.technique')   WHERE id = 2;
UPDATE work_groups SET leader_user_id = (SELECT id FROM users WHERE username = 'dir.financiere')  WHERE id = 3;

-- Initialisation du statut NOT_STARTED pour chaque couple (groupe, section)
INSERT INTO group_section_status (group_id, section_id, status)
SELECT wg.id, s.id, 'NOT_STARTED'
FROM work_groups wg
CROSS JOIN sections s;
