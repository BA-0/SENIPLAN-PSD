-- Cadre strategique de l'entreprise, arrete par la Direction Generale : vision, mission, valeurs,
-- axes strategiques communs et dispositif de pilotage.
--
-- Jusqu'ici, la note de synthese et le Plan Strategique de SENICO alignaient la vision, la mission,
-- les valeurs et les quatre axes de chacune des cinq directions : cinq visions et vingt "axes
-- strategiques" pour une seule entreprise, la ou un PSD publie n'en affiche qu'un jeu. Ces blocs
-- portent la version de l'entreprise ; les propositions des directions restent dans leur canevas.
--
-- Meme statut que V14 et V19 : contenu de recette, coherent avec les diagnostics saisis par les
-- directions, destine a etre relu puis ajuste par l'admin depuis l'ecran de redaction du Plan
-- Strategique de SENICO. Les UPDATE ne touchent qu'un bloc encore vide, pour ne jamais ecraser
-- une redaction deja faite.

INSERT INTO psd_narrative_blocks (block_key, content) VALUES
('VISION', ''),
('MISSION', ''),
('VALEURS', ''),
('AXES_CONSOLIDES', ''),
('DISPOSITIF_PILOTAGE', '')
ON DUPLICATE KEY UPDATE block_key = block_key;

UPDATE psd_narrative_blocks SET content =
'Faire de SENICO SA l''opérateur de référence du courrier, du colis et des services financiers de proximité sur le marché national et sous-régional, reconnu à l''horizon 2031 pour la fiabilité de ses délais, la modernité de ses services et la solidité de sa gestion.'
WHERE block_key = 'VISION' AND (content IS NULL OR content = '');

UPDATE psd_narrative_blocks SET content =
'Assurer sur l''ensemble du territoire national la collecte, le tri, l''acheminement et la distribution du courrier et des colis, ainsi que des services financiers de proximité, en offrant à chaque client — particulier, entreprise ou administration — un service fiable, sûr et accessible, adossé à des infrastructures modernisées, à un système d''information sécurisé et à des équipes compétentes.'
WHERE block_key = 'MISSION' AND (content IS NULL OR content = '');

UPDATE psd_narrative_blocks SET content =
'Fiabilité : SENICO SA tient ses engagements de délai, de qualité et de continuité de service. Chaque direction contribue à un service sur lequel les clients et les partenaires peuvent compter, jour après jour.
Sens du client et proximité : la satisfaction des usagers guide l''organisation du réseau, des services et des outils. La présence de l''entreprise sur tout le territoire est un engagement envers chaque client, où qu''il se trouve.
Rigueur et transparence : l''entreprise gère ses ressources avec méthode, rend compte de ses résultats et respecte les règles qui encadrent son activité et ses finances.
Sécurité : la protection des personnes, des installations, des envois et des données est une exigence permanente, placée au cœur de l''exploitation et du système d''information.
Innovation et engagement : SENICO SA modernise ses services et ses méthodes et compte sur l''implication de ses équipes pour conduire sa transformation.'
WHERE block_key = 'VALEURS' AND (content IS NULL OR content = '');

UPDATE psd_narrative_blocks SET content =
'La réussite du plan dépend autant de la qualité de son exécution que de la pertinence de ses orientations. Son pilotage repose sur trois niveaux de gouvernance, un reporting périodique et un tableau de bord stratégique.

Niveau stratégique :
- le Conseil d''Administration approuve le plan, examine chaque année son état d''avancement et arbitre les réorientations majeures ;
- le comité de pilotage du PSD, présidé par le Directeur Général et réunissant les directeurs, se réunit chaque trimestre : il examine les indicateurs, arbitre les difficultés et valide les mesures correctives.

Niveau tactique :
- le comité de direction (CODIR) assure chaque mois la coordination entre les directions et le suivi des délais et des budgets ;
- une cellule de coordination du PSD (PMO), rattachée à la Direction Générale, centralise les informations, consolide les indicateurs et prépare les tableaux de bord.

Niveau opérationnel :
- chaque direction exécute les actions dont elle est responsable, renseigne ses indicateurs selon la périodicité retenue et signale les risques opérationnels.

Reporting :
- mensuel : activités réalisées, activités en cours, difficultés rencontrées et mesures correctives proposées ;
- trimestriel : avancement par axe stratégique, indicateurs de performance, risques majeurs et recommandations ;
- annuel : évaluation des performances globales, mesure des résultats et réorientation éventuelle des actions.

Une revue à mi-parcours, en 2029, permettra d''ajuster la trajectoire au vu des résultats obtenus ; une évaluation finale, en 2031, en tirera les enseignements pour le cycle stratégique suivant.'
WHERE block_key = 'DISPOSITIF_PILOTAGE' AND (content IS NULL OR content = '');

-- Axes de l'entreprise et rattachement des axes des directions (format : cf. PsdConsolidatedAxes).
-- Les directions sont retrouvees par leur nom plutot que par leur identifiant, qui peut differer
-- d'une base a l'autre ; une direction introuvable laisse simplement ses axes non rattaches, ce que
-- les documents signalent.
SET @dc  = (SELECT id FROM work_groups WHERE name LIKE 'Direction Commerciale%' ORDER BY id LIMIT 1);
SET @dte = (SELECT id FROM work_groups WHERE name LIKE 'Direction Technique%' ORDER BY id LIMIT 1);
SET @dfc = (SELECT id FROM work_groups WHERE name LIKE 'Direction Financi%' ORDER BY id LIMIT 1);
SET @dsi = (SELECT id FROM work_groups WHERE name LIKE 'Direction des Syst%' ORDER BY id LIMIT 1);
SET @dl  = (SELECT id FROM work_groups WHERE name LIKE 'Direction Logistique%' ORDER BY id LIMIT 1);

UPDATE psd_narrative_blocks SET content = CAST(JSON_OBJECT('axes', JSON_ARRAY(
    JSON_OBJECT(
        'title', 'Croissance commerciale et expérience client',
        'objective', 'Accroître le chiffre d''affaires et la part de marché sur le courrier, le colis et les services financiers, en offrant une expérience client fiable et différenciante et en captant la croissance du e-commerce.',
        'links', JSON_ARRAY(
            JSON_OBJECT('groupId', @dc, 'axisCode', 'AXE1'),
            JSON_OBJECT('groupId', @dc, 'axisCode', 'AXE2'),
            JSON_OBJECT('groupId', @dl, 'axisCode', 'AXE4'))),
    JSON_OBJECT(
        'title', 'Modernisation de l''outil de production et performance opérationnelle',
        'objective', 'Moderniser les infrastructures, le parc de véhicules et les centres de tri, généraliser la maintenance préventive et réduire durablement les délais d''acheminement, dans le respect des normes de sécurité.',
        'links', JSON_ARRAY(
            JSON_OBJECT('groupId', @dte, 'axisCode', 'AXE1'),
            JSON_OBJECT('groupId', @dte, 'axisCode', 'AXE2'),
            JSON_OBJECT('groupId', @dte, 'axisCode', 'AXE3'),
            JSON_OBJECT('groupId', @dte, 'axisCode', 'AXE4'),
            JSON_OBJECT('groupId', @dl, 'axisCode', 'AXE1'),
            JSON_OBJECT('groupId', @dl, 'axisCode', 'AXE2'),
            JSON_OBJECT('groupId', @dl, 'axisCode', 'AXE3'))),
    JSON_OBJECT(
        'title', 'Transformation digitale et sécurité du système d''information',
        'objective', 'Urbaniser, moderniser et sécuriser le système d''information, et digitaliser les services rendus aux clients comme aux directions.',
        'links', JSON_ARRAY(
            JSON_OBJECT('groupId', @dc, 'axisCode', 'AXE3'),
            JSON_OBJECT('groupId', @dsi, 'axisCode', 'AXE1'),
            JSON_OBJECT('groupId', @dsi, 'axisCode', 'AXE2'),
            JSON_OBJECT('groupId', @dsi, 'axisCode', 'AXE3'))),
    JSON_OBJECT(
        'title', 'Soutenabilité financière et mobilisation des ressources',
        'objective', 'Garantir l''équilibre financier de l''entreprise, fiabiliser l''information comptable, piloter le budget et mobiliser les financements nécessaires au plan.',
        'links', JSON_ARRAY(
            JSON_OBJECT('groupId', @dfc, 'axisCode', 'AXE1'),
            JSON_OBJECT('groupId', @dfc, 'axisCode', 'AXE2'),
            JSON_OBJECT('groupId', @dfc, 'axisCode', 'AXE3'),
            JSON_OBJECT('groupId', @dfc, 'axisCode', 'AXE4'))),
    JSON_OBJECT(
        'title', 'Gouvernance, capital humain et compétences',
        'objective', 'Structurer la gouvernance du plan et du système d''information, et développer les compétences et l''engagement des équipes.',
        'links', JSON_ARRAY(
            JSON_OBJECT('groupId', @dc, 'axisCode', 'AXE4'),
            JSON_OBJECT('groupId', @dsi, 'axisCode', 'AXE4')))
)) AS CHAR)
WHERE block_key = 'AXES_CONSOLIDES' AND (content IS NULL OR content = '');

-- Le preambule de recette (V14, retouche par V17) annonce un perimetre limite a la validation du
-- comite de pilotage, alors que l'approbation du DG conditionne desormais l'entree d'une section
-- (V16), et ne dit rien de l'arbitrage du cadre strategique. On ne le reecrit que s'il porte encore
-- cette phrase de recette : une redaction de l'admin n'est jamais ecrasee.
UPDATE psd_narrative_blocks SET content =
'Le présent document constitue le Plan Stratégique de Développement (PSD) de SENICO SA pour la période 2027-2031.
Il a été élaboré selon le canevas de diagnostic stratégique adopté par le comité de pilotage : chaque direction, constituée en groupe de travail, a renseigné les rubriques du canevas pour son périmètre, de l''analyse des parties prenantes jusqu''au plan de financement.
Les contributions ainsi recueillies ont été consolidées puis arbitrées par la Direction Générale, qui a arrêté la vision, la mission, les valeurs et les axes stratégiques communs à l''entreprise. Chaque constat repris des directions reste identifiable par le code couleur de la direction dont il émane.
Seules les sections validées par le comité de pilotage puis approuvées par la Direction Générale sont reprises ici ; le détail intégral par direction figure dans les plans stratégiques sectoriels.'
WHERE block_key = 'PREAMBULE' AND content LIKE '%Seules les sections valid_es par le comit_ de pilotage sont reprises ici%';
