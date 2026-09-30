-- Compte Superviseur : suit en temps reel tout l'espace de pilotage (tableau de bord,
-- soumissions, documents consolides, vue projecteur), en lecture seule.
--
-- Nouveau role SUPERVISEUR : il consulte tout ce que voient l'admin et la direction generale,
-- mais ne valide, n'approuve, ne modifie ni n'efface rien. Les regles correspondantes sont
-- dans SecurityConfig (toute methode autre que GET sur /admin lui est refusee).
--
-- Comme pour le compte du DG (V15), le hash ci-dessous est celui d'un secret aleatoire genere
-- puis jete : l'admin donne l'acces via « Reinitialiser le mot de passe » dans l'ecran des
-- comptes, qui affiche une fois le mot de passe genere.
--
-- group_id reste NULL : le superviseur n'est pas un groupe de travail.

INSERT INTO users (username, password_hash, full_name, role, group_id, enabled) VALUES
('superviseur', '$2a$10$iSiYL0vxnRqknDWil4vHneGkNQ2TditBXwHwgr2kWTSoj92hV9Sle', 'Superviseur', 'SUPERVISEUR', NULL, 1);
