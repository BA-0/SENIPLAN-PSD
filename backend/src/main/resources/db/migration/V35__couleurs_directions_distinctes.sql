-- Retour client (21/09/2026) : plusieurs directions avaient des couleurs trop proches pour etre distinguees
-- dans la legende et les tableaux (bleu / bleu marine / cyan / sarcelle, vert / vert olive, orange / brun).
-- Chaque direction recoit une teinte d'une famille differente, assez soutenue pour rester lisible en texte
-- sur fond blanc comme en aplat.

UPDATE work_groups SET color = '#1F4FD8' WHERE name = 'Direction Ventes Locales et Marketing';
UPDATE work_groups SET color = '#2E8B2E' WHERE name = 'Direction Technique';
UPDATE work_groups SET color = '#E36A00' WHERE name = 'Direction Financière et Contrôle de Gestion';
UPDATE work_groups SET color = '#7B2CBF' WHERE name = 'Direction des Systèmes d''Information';
UPDATE work_groups SET color = '#D62828' WHERE name = 'Direction Supply Chain';
UPDATE work_groups SET color = '#E0409A' WHERE name = 'Direction des Ressources Humaines';
UPDATE work_groups SET color = '#0E9AA7' WHERE name = 'Direction des Risques et Conformités';
UPDATE work_groups SET color = '#7F4A1E' WHERE name = 'Direction Industrielle';
UPDATE work_groups SET color = '#C9A000' WHERE name = 'Direction QHSE';
UPDATE work_groups SET color = '#1B2A41' WHERE name = 'Direction Générale';
UPDATE work_groups SET color = '#6B7280' WHERE name = 'Service Juridique';
UPDATE work_groups SET color = '#800020' WHERE name = 'Service R&D';
