-- Validation a deux niveaux. Le comite de pilotage (ADMIN) valide une section soumise comme
-- avant ; le DG l'approuve ensuite. Tant que l'approbation du DG manque, la section n'entre
-- ni dans le Document de consolidation, ni dans la Note de synthese, ni dans le Plan
-- Strategique de SENICO.
--
-- L'approbation est portee par une date plutot que par un statut de plus : le statut dit ou
-- en est la direction (brouillon, soumis, valide, a reviser) et reste lisible pour elle ;
-- l'approbation du DG est un second axe, qui ne vaut que sur une section VALIDATED. Toute
-- sortie de VALIDATED — revision demandee, main rendue au groupe, nouvelle soumission,
-- reinitialisation, nouveau cycle, correction du contenu par l'admin — remet dg_approved_at
-- a NULL (cf. SectionEngineService) : une approbation ancienne ne doit jamais ressusciter
-- sur un contenu revu depuis.
--
-- Rien n'est retro-approuve : les sections deja validees par l'admin avant cette migration
-- attendent le passage du DG, qui peut les approuver direction par direction en un clic
-- ("Approuver toutes les sections validees").

ALTER TABLE group_section_status
    ADD COLUMN dg_approved_at DATETIME NULL AFTER validated_at,
    ADD COLUMN dg_approved_by BIGINT NULL AFTER dg_approved_at,
    ADD COLUMN dg_comment TEXT NULL AFTER admin_comment;

ALTER TABLE group_section_status
    ADD CONSTRAINT fk_gss_dg_approved_by FOREIGN KEY (dg_approved_by) REFERENCES users(id) ON DELETE SET NULL;

-- Les archives de cycle figent l'etat d'une section a la cloture : sans cette colonne on
-- perdrait la trace de ce que le DG avait approuve dans le cycle precedent.
ALTER TABLE group_cycle_archives
    ADD COLUMN dg_approved_at DATETIME NULL AFTER validated_at;
