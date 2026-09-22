-- Le bilan des performances (S01B) couvre desormais les cinq exercices ecoules (2021-2025), regroupes
-- dans un seul tableau, et l'exercice 2026 en cours avec ses tendances, dans les colonnes du modele
-- client : objectif, indicateur, resultat attendu en decembre, ecart, cause, cause profonde, action
-- entreprise. Seul l'intitule de la section change ici.
--
-- Le contenu deja saisi n'est pas touche : une ligne de l'ancien tableau 2026 (domaine, cible, realise,
-- commentaire) est convertie a la lecture par DerivedFieldsService, sans perte, et reecrite dans la
-- nouvelle structure a la prochaine sauvegarde de la direction.
UPDATE sections SET title = 'Bilan des performances des annees passees et de 2026'
WHERE id = 19 AND code = 'S01B';
