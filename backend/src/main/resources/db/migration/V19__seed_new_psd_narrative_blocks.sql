-- Redaction des 4 blocs narratifs ajoutes au "Plan Strategique de SENICO" pour aligner son
-- sommaire sur celui d'un PSD publie : approche methodologique, bilan du plan precedent,
-- facteurs cles de reussite et d'echec, conclusion. Sans eux, le document affichait
-- "(contenu a renseigner)" sur quatre pages (cf. PsdFinalDocumentCompletenessIT).
--
-- Meme statut que V14 : contenu de recette, coherent avec les diagnostics saisis par les cinq
-- directions et avec le sommaire client, et destine a etre relu puis ajuste par l'admin depuis
-- l'ecran de redaction du document final. L'INSERT est idempotent : les lignes n'existent pas
-- encore, l'enum NarrativeBlockKey venant d'etre etendu.

INSERT INTO psd_narrative_blocks (block_key, content) VALUES
('APPROCHE_METHODOLOGIQUE', ''),
('BILAN_PSD_PRECEDENT', ''),
('FACTEURS_CLES', ''),
('CONCLUSION', '')
ON DUPLICATE KEY UPDATE block_key = block_key;

UPDATE psd_narrative_blocks SET content =
'L''élaboration du présent plan a suivi une démarche participative, conduite direction par direction puis consolidée au niveau de l''entreprise. Elle s''est déroulée en quatre phases.

Phase 1 — Cadrage. Le comité de pilotage a arrêté le canevas de diagnostic stratégique, commun aux cinq directions, et le calendrier de la campagne. Chaque direction a été constituée en groupe de travail et dotée d''un compte lui donnant accès à son propre canevas.

Phase 2 — Diagnostic. Chaque groupe de travail a renseigné les rubriques du canevas pour son périmètre : analyse des parties prenantes, performances de l''exercice écoulé, ressources et compétences, analyses SWOT et PESTEL, confrontation des facteurs internes et externes, matrice des risques, puis synthèse des enjeux et des contraintes.

Phase 3 — Formulation stratégique. Les enjeux dégagés par le diagnostic ont été traduits en vision, mission et valeurs, puis en axes d''intervention et objectifs spécifiques. Pour chaque objectif ont été définis le cadre logique, les lignes d''actions, les indicateurs objectivement vérifiables, les responsables, le budget pluriannuel et le plan de financement.

Phase 4 — Validation et consolidation. Chaque section renseignée est soumise par la direction, examinée par le comité de pilotage, puis approuvée par la Direction Générale. Seules les sections ayant franchi ces deux niveaux entrent dans le présent document : ce qui attend encore un arbitrage en est écarté, afin qu''aucun chiffre publié ici ne soit démenti par la suite.'
WHERE block_key = 'APPROCHE_METHODOLOGIQUE';

UPDATE psd_narrative_blocks SET content =
'Le cycle stratégique précédent a permis de poser les bases de la planification à SENICO SA : formalisation d''un cadre d''objectifs, mise en place d''un suivi budgétaire par direction et premières initiatives de modernisation du réseau et des outils.

Son exécution a toutefois mis en évidence des limites que le présent plan entend corriger. L''analyse des performances de l''exercice 2026, conduite par chaque direction au titre du diagnostic, montre que les cibles de délai d''acheminement, de disponibilité du parc et de couverture numérique n''ont pas été atteintes. Les investissements de modernisation ont été engagés plus tardivement que prévu, faute de ressources mobilisées en temps utile, et le suivi des indicateurs est resté irrégulier, sans dispositif de reporting consolidé.

Quatre enseignements en sont tirés pour la période 2027-2031 :
- la qualité d''un plan tient autant à son exécution qu''à sa conception : le dispositif de pilotage et de suivi-évaluation doit être installé dès la première année ;
- la modernisation de l''outil de production et des systèmes d''information conditionne les gains attendus sur tous les autres axes ;
- le financement des investissements structurants doit être sécurisé en amont, et non arbitré exercice par exercice ;
- le développement des compétences doit accompagner chaque chantier, sous peine de doter l''entreprise d''outils qu''elle n''exploite pas.

Ces enseignements ont directement structuré les axes retenus dans le cadre stratégique du présent plan.'
WHERE block_key = 'BILAN_PSD_PRECEDENT';

UPDATE psd_narrative_blocks SET content =
'La réalisation des objectifs du plan dépendra de la capacité de l''entreprise à réunir un ensemble de conditions, et à maîtriser les risques susceptibles de l''en écarter.

Facteurs clés de réussite :
- un pilotage stratégique effectif : instances réunies selon le calendrier prévu, tableaux de bord tenus, arbitrages rendus en temps utile ;
- la mobilisation effective des financements prévus au plan de financement, en particulier pour les investissements de modernisation ;
- l''appropriation du plan par l''encadrement et par les équipes, relayée par un dispositif de conduite du changement ;
- la montée en compétence des personnels sur les nouveaux outils et les nouveaux services ;
- la qualité et la régularité de la donnée de gestion, sans laquelle le suivi des indicateurs reste déclaratif.

Principaux risques d''échec :
- un pilotage qui s''essouffle après la première année, et un suivi des indicateurs qui redevient irrégulier ;
- un financement insuffisant ou tardif, qui reporterait les investissements structurants et, avec eux, les gains attendus ;
- une résistance au changement ou un déficit de compétences sur les chantiers de digitalisation ;
- une dégradation de la qualité de service pendant la phase de transformation, qui coûterait des parts de marché difficiles à reconquérir ;
- une dépendance persistante aux activités les moins rentables, faute d''avoir développé les relais de croissance identifiés au diagnostic.

Le dispositif de suivi-évaluation décrit plus loin a précisément pour objet de détecter ces dérives assez tôt pour que des mesures correctives puissent être prises.'
WHERE block_key = 'FACTEURS_CLES';

UPDATE psd_narrative_blocks SET content =
'Le Plan Stratégique de Développement 2027-2031 engage SENICO SA dans une transformation dont le diagnostic conduit par les cinq directions a établi la nécessité. Il ne s''agit pas d''un exercice de planification formel : les constats qui le fondent — vieillissement de l''outil de production, hétérogénéité des systèmes d''information, pression concurrentielle sur les segments les plus rentables, étroitesse des marges de manoeuvre financières — appellent des décisions dont le report aurait un coût.

Le plan y répond par un cadre stratégique articulé en axes d''intervention, décliné en objectifs spécifiques, en lignes d''actions et en indicateurs objectivement vérifiables, et adossé à un budget pluriannuel et à un plan de financement. Chaque action porte un responsable et une échéance.

Sa réussite reposera sur quatre conditions : la régularité du pilotage et du suivi des indicateurs par les instances de gouvernance, la mobilisation effective des financements, l''appropriation du plan par l''ensemble des directions et de leurs équipes, et la capacité de l''entreprise à conduire sa transformation sans dégrader le service rendu.

Une revue à mi-parcours permettra d''ajuster la trajectoire au vu des résultats obtenus, et une évaluation finale en 2031 d''en tirer les enseignements pour le cycle suivant. Le présent document constitue, jusque-là, la référence commune de l''ensemble des directions.'
WHERE block_key = 'CONCLUSION';
