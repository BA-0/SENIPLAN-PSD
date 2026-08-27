-- Couleur par direction/departement (work_groups), pour les distinguer dans les vues
-- admin et l'export Excel consolide.

ALTER TABLE work_groups ADD COLUMN color VARCHAR(9) NULL;

UPDATE work_groups SET color = '#2563EB' WHERE id = 1;
UPDATE work_groups SET color = '#16A34A' WHERE id = 2;
UPDATE work_groups SET color = '#D97706' WHERE id = 3;
