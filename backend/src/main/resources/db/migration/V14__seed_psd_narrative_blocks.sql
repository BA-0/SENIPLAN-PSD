-- Redaction des 8 blocs narratifs du "Plan Strategique de SENICO" (V6), restes
-- vides depuis leur creation : le document consolide affichait "(contenu a
-- renseigner)" sur les huit pages qui ne proviennent d'aucune section du canevas.
--
-- Contenu de recette, coherent avec les diagnostics saisis par les cinq directions
-- (S01B a S17) et avec le sommaire client (CANEVA PSD.pdf). Il reste editable par
-- l'admin depuis l'ecran de redaction du document final.

UPDATE psd_narrative_blocks SET content =
'Le Plan Stratégique de Développement 2027-2031 que vous tenez entre les mains est le fruit d''un travail collectif conduit par l''ensemble des directions de SENICO SA.
Il traduit une conviction simple : notre entreprise dispose d''un capital rare — un réseau national, une marque de confiance et des équipes expérimentées — mais ce capital ne produira ses effets que s''il est modernisé, outillé et piloté.
Les cinq groupes de travail ont conduit leur diagnostic sans complaisance. Les constats sont exigeants : un parc et des infrastructures vieillissants, des systèmes d''information hétérogènes, des délais d''acheminement en retrait sur ceux de la concurrence et des marges de manoeuvre financières étroites. Ce sont ces constats, et non des ambitions déclaratives, qui fondent les axes du présent plan.
Je remercie chaque direction pour la qualité et la franchise de sa contribution, et j''invite l''ensemble du personnel à s''approprier ce plan : sa réussite se jouera dans son exécution, exercice après exercice, jusqu''en 2031.'
WHERE block_key = 'MOT_DU_DG';

UPDATE psd_narrative_blocks SET content =
'Le présent document constitue le Plan Stratégique de Développement (PSD) de SENICO SA pour la période 2027-2031.
Il a été élaboré selon le canevas de diagnostic stratégique adopté par le comité de pilotage : chaque direction, constituée en groupe de travail, a renseigné les rubriques du canevas pour son périmètre, de l''analyse des parties prenantes jusqu''au business plan à cinq ans.
Les contributions ainsi recueillies ont ensuite été consolidées, rubrique par rubrique, dans le présent document. Chaque contribution reste identifiable par le code couleur de la direction dont elle émane, de manière à préserver la traçabilité du diagnostic tout en donnant une lecture d''ensemble.
Seules les sections validées par le comité de pilotage sont reprises ici ; le détail intégral par direction figure dans les plans stratégiques sectoriels.'
WHERE block_key = 'PREAMBULE';

UPDATE psd_narrative_blocks SET content =
'SENICO SA (Sénégalaise Industrie & Commerce) exerce dans un environnement qui s''est profondément transformé au cours des dernières années : recul structurel du courrier classique, croissance rapide du commerce en ligne et des flux de colis, montée en puissance d''opérateurs privés sur les segments les plus rentables, et exigences croissantes des clients en matière de délais et de traçabilité.
Face à cette transformation, l''entreprise conserve des atouts déterminants : une couverture territoriale que peu d''acteurs peuvent égaler, une notoriété ancienne et une capacité d''intervention de proximité dans toutes les régions.
Le diagnostic conduit en 2026 montre cependant que ces atouts sont aujourd''hui contraints par l''état de l''outil de production et par le retard d''outillage numérique. L''analyse des performances de l''année 2026 en donne la mesure : les cibles de délai, de disponibilité du parc et de couverture numérique n''ont pas été atteintes.
Le plan 2027-2031 vise donc à convertir une présence historique en avantage concurrentiel durable, autour de la modernisation des moyens, de la digitalisation des services et du renforcement des compétences.'
WHERE block_key = 'INTRODUCTION';

UPDATE psd_narrative_blocks SET content =
'SENICO SA assure, sur l''ensemble du territoire national, la collecte, le transport, le tri et la distribution du courrier et des colis, ainsi que la fourniture de services financiers de proximité par l''intermédiaire de son réseau d''agences.
À ce titre, l''entreprise remplit trois missions principales :
- garantir un service d''acheminement fiable et accessible sur l''ensemble du territoire, y compris dans les zones les moins densément desservies ;
- accompagner les entreprises et les administrations dans leurs besoins de courrier de gestion, de logistique et de distribution ;
- contribuer à l''inclusion financière en offrant, au plus près des populations, des services de paiement et de transfert.
Ces missions structurent l''ensemble du présent plan : chaque axe stratégique retenu par les directions y est rattaché.'
WHERE block_key = 'MISSIONS';

UPDATE psd_narrative_blocks SET content =
'L''entreprise est organisée en cinq directions, qui ont chacune constitué un groupe de travail pour l''élaboration du présent plan :
- la Direction Commerciale, chargée de la relation client, du réseau d''agences et du développement des offres ;
- la Direction Technique et Exploitation, responsable des infrastructures, des équipements et de la conduite de l''exploitation ;
- la Direction Financière et Comptable, en charge du budget, de la comptabilité, de la trésorerie et du financement ;
- la Direction des Systèmes d''Information, responsable du socle applicatif, de l''infrastructure informatique et de la cybersécurité ;
- la Direction Logistique, qui opère le parc de véhicules, les centres de tri et l''acheminement.
Le comité de pilotage du PSD, présidé par la Direction Générale, assure l''arbitrage entre les contributions, la validation des sections et le suivi de l''exécution du plan.'
WHERE block_key = 'ORGANISATION';

UPDATE psd_narrative_blocks SET content =
'Les ressources mobilisées par SENICO SA relèvent de quatre natures, analysées en détail dans la matrice des ressources et des compétences de chaque direction.
Ressources humaines : les effectifs projetés dans le plan d''évolution des effectifs progressent sur la période 2027-2031, avec un rééquilibrage recherché en faveur de l''encadrement et une réduction progressive du recours au travail journalier. La féminisation des effectifs constitue un objectif transversal.
Ressources matérielles : parc de véhicules, centres de tri, agences et installations techniques constituent l''outil de production ; leur vieillissement est le principal facteur de fragilité identifié par le diagnostic.
Ressources financières : le plan repose sur une combinaison de ressources propres, de subventions publiques, de concours de partenaires techniques et financiers et d''emprunts, détaillée dans le plan de financement.
Ressources immatérielles : la notoriété de la marque, la relation de confiance avec la clientèle et le savoir-faire opérationnel des équipes constituent un actif que le plan entend préserver et valoriser.'
WHERE block_key = 'RESSOURCES';

UPDATE psd_narrative_blocks SET content =
'La consolidation des diagnostics des cinq directions fait converger les défis prioritaires vers cinq chantiers communs.
Moderniser l''outil de production : renouveler le parc de véhicules et remettre à niveau les infrastructures techniques, dont le vieillissement pèse directement sur la disponibilité et sur les délais.
Passer du curatif au préventif : généraliser la maintenance préventive planifiée, aujourd''hui supplantée par les interventions correctives, et sécuriser les stocks de pièces critiques.
Urbaniser et sécuriser le système d''information : interconnecter des systèmes encore hétérogènes, déployer un CRM et un outil de planification logistique, et renforcer le dispositif de cybersécurité.
Rétablir des marges de manoeuvre financières : réduire les délais de clôture et de paiement, maîtriser les écarts entre budget et réalisations et diversifier les sources de financement.
Accompagner la montée en compétences : former les équipes aux outils numériques, de planification et de gestion, et fidéliser les profils critiques.'
WHERE block_key = 'DEFIS_A_RELEVER';

UPDATE psd_narrative_blocks SET content =
'Au-delà des défis opérationnels, trois enjeux déterminent la trajectoire de SENICO SA sur la période du plan.
Un enjeu de positionnement : la croissance du commerce en ligne ouvre un marché du colis en expansion rapide, sur lequel l''entreprise dispose d''un avantage de couverture territoriale mais reste exposée à des opérateurs plus agiles sur les délais. Capter cette croissance dans des conditions de coût maîtrisées conditionne le renouvellement du modèle économique, alors que le courrier classique poursuit son recul.
Un enjeu de crédibilité de service : la qualité perçue se joue sur le délai et sur l''information de suivi. Le diagnostic 2026 identifie le délai de livraison comme premier motif de réclamation ; il constitue de ce fait l''indicateur transversal du plan.
Un enjeu de soutenabilité : la modernisation exige un effort d''investissement soutenu sur cinq ans, dans un contexte inflationniste et de tension sur la trésorerie. La capacité à sécuriser un financement pluriannuel et à en démontrer le rendement conditionne l''exécution de l''ensemble des axes.'
WHERE block_key = 'ENJEUX';
