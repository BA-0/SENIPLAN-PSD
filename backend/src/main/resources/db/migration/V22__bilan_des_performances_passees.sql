-- SENICO SA n'a pas encore eu de plan strategique : la partie « Bilan » porte sur les
-- performances des annees precedentes, et non plus sur un « cycle strategique precedent ».
-- Seul le texte de recette depose par V19 est remplace : un bilan deja reecrit par la Direction
-- Generale reste tel quel.
UPDATE psd_narrative_blocks SET content =
'SENICO SA ne s''est pas encore dotée d''un plan stratégique formalisé : le présent bilan porte donc sur les performances des exercices précédents, telles que les directions les ont mesurées au titre du diagnostic.

Ces exercices ont permis de consolider plusieurs acquis : formalisation d''un cadre d''objectifs, mise en place d''un suivi budgétaire par direction et premières initiatives de modernisation du réseau et des outils.

Ils ont toutefois mis en évidence des limites que le présent plan entend corriger. L''analyse des performances de l''exercice 2026, conduite par chaque direction, montre que les cibles de délai d''acheminement, de disponibilité du parc et de couverture numérique n''ont pas été atteintes. Les investissements de modernisation ont été engagés plus tardivement que prévu, faute de ressources mobilisées en temps utile, et le suivi des indicateurs est resté irrégulier, sans dispositif de reporting consolidé.

Quatre enseignements en sont tirés pour la période 2027-2031 :
- la qualité d''un plan tiendra autant à son exécution qu''à sa conception : le dispositif de pilotage et de suivi-évaluation doit être installé dès la première année ;
- la modernisation de l''outil de production et des systèmes d''information conditionne les gains attendus sur tous les autres axes ;
- le financement des investissements structurants doit être sécurisé en amont, et non arbitré exercice par exercice ;
- le développement des compétences doit accompagner chaque chantier, sous peine de doter l''entreprise d''outils qu''elle n''exploiterait pas.

Ces enseignements ont directement structuré les axes retenus dans le cadre stratégique du présent plan.'
WHERE block_key = 'BILAN_PSD_PRECEDENT'
  AND content LIKE 'Le cycle stratégique précédent a permis%';
