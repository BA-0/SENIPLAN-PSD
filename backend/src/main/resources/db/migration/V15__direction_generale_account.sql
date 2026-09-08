-- Compte de la Direction Generale, pour le DG Mouhamed DIA.
--
-- Nouveau role DIRECTEUR_GENERAL : voit et valide tout, mais n'a pas l'administration
-- technique (creation de groupes, reinitialisation de mots de passe, nouveau cycle,
-- edition directe des saisies), qui reste a l'admin. Les regles correspondantes sont
-- dans SecurityConfig.
--
-- Le compte n'a pas de mot de passe utilisable a la creation : le hash ci-dessous est
-- celui d'un secret aleatoire genere puis jete, que personne ne connait. L'admin donne
-- son acces au DG via "Reinitialiser le mot de passe" dans l'ecran Groupes de travail,
-- qui affiche une fois le mot de passe genere. Aucun mot de passe n'a donc transite par
-- le code source ni par un canal de discussion.
--
-- group_id reste NULL : le DG n'est pas un groupe de travail et ne remplit pas de canevas,
-- il arbitre celui des cinq directions.

INSERT INTO users (username, password_hash, full_name, role, group_id, enabled) VALUES
('m.dia', '$2a$10$JIPkXVpUQBJ2kQmJhvJnVunKNtjKkpZAxEnmy1cWsRHFS1kOx0WfS', 'Mouhamed DIA', 'DIRECTEUR_GENERAL', NULL, 1);
