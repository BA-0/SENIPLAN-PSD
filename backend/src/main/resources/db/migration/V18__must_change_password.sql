-- Changement du mot de passe a la premiere connexion.
--
-- Tous les acces sont aujourd'hui remis en main propre : l'admin cree le compte, lit le mot de
-- passe genere une seule fois a l'ecran, puis le transmet. Ce mot de passe a donc ete vu par
-- quelqu'un d'autre que son titulaire, et le reste tant qu'il ne l'a pas remplace. Le drapeau
-- ci-dessous force ce remplacement : tant qu'il vaut 1, le serveur refuse tout appel autre que
-- la connexion et le changement de mot de passe (cf. PasswordChangeGuardFilter).
--
-- Il est repose a 1 a chaque fois qu'un mot de passe est attribue par un tiers : creation de
-- compte, creation d'une direction, reinitialisation. Il retombe a 0 quand le titulaire choisit
-- lui-meme son mot de passe.

ALTER TABLE users
    ADD COLUMN must_change_password TINYINT(1) NOT NULL DEFAULT 0 AFTER password_hash;

-- Comptes jamais utilises : leur mot de passe est encore celui remis par l'admin, ils sont
-- exactement dans le cas que la regle vise. Les comptes deja actifs ne sont pas deranges.
UPDATE users SET must_change_password = 1 WHERE last_login_at IS NULL;
