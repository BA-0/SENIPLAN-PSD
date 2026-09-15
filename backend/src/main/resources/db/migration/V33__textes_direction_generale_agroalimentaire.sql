-- Revue client du 15/09/2026 : les textes arretes par la Direction Generale decrivent SENICO telle que la
-- presentent l'historique et les missions (V30, V31) — un groupe agroalimentaire qui produit, commercialise et
-- distribue ses produits — et non plus un operateur de courrier, de colis et de services financiers. Ils restent
-- adosses aux constats des directions (delais de livraison, parc et equipements, maintenance, systeme
-- d'information, financement) et n'annoncent aucun chiffre que le diagnostic ne porte pas.
-- Chaque texte n'est reecrit que s'il porte encore sa formulation d'origine : une version deja reprise sur
-- l'ecran « Plan Stratégique de SENICO » reste telle quelle.

UPDATE psd_narrative_blocks SET content =
'Faire de SENICO SA le groupe agroalimentaire de référence au Sénégal et un acteur reconnu dans la sous-région à l''horizon 2031, par la qualité de ses produits, la force de ses marques, la fiabilité de sa chaîne logistique et la solidité de sa gestion.'
WHERE block_key = 'VISION' AND content LIKE '%du courrier, du colis%';

UPDATE psd_narrative_blocks SET content =
'Produire, commercialiser et distribuer des produits alimentaires sûrs, de qualité et accessibles au plus grand nombre, au Sénégal comme à l''export, en s''appuyant sur des unités de production modernisées, une chaîne logistique fiable, un système d''information sécurisé et des équipes compétentes.'
WHERE block_key = 'MISSION' AND content LIKE '%distribution du courrier et des colis%';

UPDATE psd_narrative_blocks SET content =
'Qualité et sécurité des produits : SENICO SA garantit à chaque consommateur des produits conformes aux exigences de qualité, d''hygiène et de sécurité, de la réception des matières premières à la livraison.
Sens du client et proximité : la satisfaction des consommateurs et des distributeurs guide le développement des marques, la distribution et le service. Rendre les produits de l''entreprise disponibles partout où ils sont attendus est un engagement envers chaque client.
Fiabilité : SENICO SA tient ses engagements de qualité, de délai et de continuité d''approvisionnement. Chaque direction contribue à un service sur lequel les clients et les partenaires peuvent compter, jour après jour.
Rigueur et transparence : l''entreprise gère ses ressources avec méthode, rend compte de ses résultats et respecte les règles qui encadrent son activité et ses finances.
Sécurité : la protection des personnes, des installations, des produits et des données est une exigence permanente, placée au cœur de la production, de la logistique et du système d''information.
Innovation et engagement : SENICO SA fait évoluer ses produits, ses procédés et ses méthodes, et compte sur l''implication de ses équipes pour conduire sa transformation.'
WHERE block_key = 'VALEURS' AND content LIKE '%des envois et des données%';

UPDATE psd_narrative_blocks SET content =
'Le Plan Stratégique 2027-2031 que vous tenez entre les mains est le fruit d''un travail collectif conduit par l''ensemble des directions de SENICO SA.
Née en 1989 d''une simple boutique de Keur Massar, notre entreprise est devenue un acteur majeur de l''agroalimentaire au Sénégal. Elle dispose d''un capital rare — des marques connues et appréciées des consommateurs, une présence commerciale étendue et des équipes expérimentées — mais ce capital ne produira ses effets que s''il est modernisé, outillé et piloté.
Les groupes de travail des directions ont conduit leur diagnostic sans complaisance. Les constats sont exigeants : des équipements et un parc vieillissants, des systèmes d''information hétérogènes, des délais de livraison encore en retrait sur les attentes de nos clients et des marges de manœuvre financières étroites. Ce sont ces constats, et non des ambitions déclaratives, qui fondent les axes du présent plan.
Je remercie chaque direction pour la qualité et la franchise de sa contribution, et j''invite l''ensemble du personnel à s''approprier ce plan : sa réussite se jouera dans son exécution, exercice après exercice, jusqu''en 2031.'
WHERE block_key = 'MOT_DU_DG' AND content LIKE '%délais d''acheminement en retrait%';

UPDATE psd_narrative_blocks SET content =
'SENICO SA (Sénégalaise Industrie & Commerce) produit, commercialise et distribue des produits alimentaires dans un environnement qui s''est profondément transformé au cours des dernières années : concurrence accrue sur les segments les plus rentables, pression sur le pouvoir d''achat des ménages, hausse du coût des facteurs de production, de l''énergie et du transport, développement de nouveaux circuits de vente et exigences croissantes des consommateurs en matière de qualité, de sécurité et de disponibilité des produits.
Face à ces évolutions, l''entreprise conserve des atouts déterminants : des marques connues des consommateurs, une présence commerciale étendue sur le territoire national et à l''export, un outil industriel implanté dans la zone industrielle de Diamniadio et un savoir-faire construit depuis 1989.
Le diagnostic conduit en 2026 montre cependant que ces atouts sont aujourd''hui contraints par l''état d''une partie de l''outil de production et du parc logistique, et par le retard d''outillage numérique. L''analyse des performances de l''exercice 2026, établie à date par chaque direction, en donne la mesure : plusieurs cibles de délai, de disponibilité des équipements et de couverture numérique restent à ce stade hors d''atteinte.
Le plan 2027-2031 vise donc à convertir cette position acquise en avantage concurrentiel durable, autour de la modernisation des moyens de production et de distribution, de la digitalisation et du renforcement des compétences.'
WHERE block_key = 'INTRODUCTION' AND content LIKE '%recul structurel du courrier classique%';

UPDATE psd_narrative_blocks SET content =
'Au-delà des défis opérationnels, trois enjeux déterminent la trajectoire de SENICO SA sur la période du plan.
Un enjeu de positionnement : sur un marché agroalimentaire où la concurrence s''intensifie et où le pouvoir d''achat des ménages reste sous pression, l''entreprise devra consolider la place de ses marques sur le marché national et développer de nouveaux relais de croissance, à l''export comme dans les nouveaux circuits de vente, en maîtrisant ses coûts.
Un enjeu de qualité et de service : la confiance des consommateurs et des distributeurs se joue sur la qualité et la sécurité des produits, sur leur disponibilité et sur la fiabilité des livraisons. Le diagnostic 2026 identifie le délai de livraison comme premier motif de réclamation ; il constitue de ce fait un indicateur transversal du plan.
Un enjeu de soutenabilité : la modernisation exige un effort d''investissement soutenu sur cinq ans, dans un contexte inflationniste et de tension sur la trésorerie. La capacité à sécuriser un financement pluriannuel et à en démontrer le rendement conditionne l''exécution de l''ensemble des axes.'
WHERE block_key = 'ENJEUX' AND content LIKE '%marché du colis%';

UPDATE psd_narrative_blocks SET content = REPLACE(content,
'Moderniser l''outil de production : renouveler le parc de véhicules et remettre à niveau les infrastructures techniques,',
'Moderniser l''outil de production et de distribution : renouveler les équipements et le parc de véhicules les plus anciens et remettre à niveau les infrastructures techniques,')
WHERE block_key = 'DEFIS_A_RELEVER';

UPDATE psd_narrative_blocks SET content = REPLACE(content,
'Ressources matérielles : parc de véhicules, centres de tri, agences et installations techniques constituent l''outil de production ; leur vieillissement est le principal facteur de fragilité identifié par le diagnostic.',
'Ressources matérielles : unités de production et de conditionnement, entrepôts, parc de véhicules et installations techniques constituent l''outil de production et de distribution ; le vieillissement d''une partie de ces équipements est le principal facteur de fragilité identifié par le diagnostic.')
WHERE block_key = 'RESSOURCES';
UPDATE psd_narrative_blocks SET content = REPLACE(content,
'Ressources immatérielles : la notoriété de la marque, la relation de confiance avec la clientèle et le savoir-faire opérationnel des équipes',
'Ressources immatérielles : la notoriété des marques de l''entreprise, dont Kadi, Halib et Jadida, la relation de confiance avec les consommateurs et les distributeurs, et le savoir-faire industriel et commercial des équipes')
WHERE block_key = 'RESSOURCES';

UPDATE psd_narrative_blocks SET content = REPLACE(content,
'premières initiatives de modernisation du réseau et des outils',
'premières initiatives de modernisation des équipements et des outils')
WHERE block_key = 'BILAN_PSD_PRECEDENT';
UPDATE psd_narrative_blocks SET content = REPLACE(content,
'montre que les cibles de délai d''acheminement, de disponibilité du parc et de couverture numérique restent',
'montre que plusieurs cibles de délai de livraison, de disponibilité des équipements et du parc, et de couverture numérique restent')
WHERE block_key = 'BILAN_PSD_PRECEDENT';

UPDATE psd_narrative_blocks SET content = REPLACE(content,
'sans dégrader le service rendu',
'sans dégrader la qualité de ses produits ni le service rendu à ses clients')
WHERE block_key = 'CONCLUSION';

-- Objectifs des axes de l'entreprise (contenu structure JSON) : seuls les deux qui decrivaient l'operateur postal changent.
UPDATE psd_narrative_blocks SET content = REPLACE(content,
'Accroître le chiffre d''affaires et la part de marché sur le courrier, le colis et les services financiers, en offrant une expérience client fiable et différenciante et en captant la croissance du e-commerce.',
'Accroître le chiffre d''affaires et les parts de marché des marques de SENICO, au Sénégal et à l''export, en offrant aux consommateurs et aux distributeurs une expérience fiable et différenciante et en développant les nouveaux circuits de vente, dont le e-commerce.')
WHERE block_key = 'AXES_CONSOLIDES';
UPDATE psd_narrative_blocks SET content = REPLACE(content,
'Moderniser les infrastructures, le parc de véhicules et les centres de tri, généraliser la maintenance préventive et réduire durablement les délais d''acheminement, dans le respect des normes de sécurité.',
'Moderniser les unités de production, les infrastructures techniques, les entrepôts et le parc de véhicules, généraliser la maintenance préventive et réduire durablement les délais de livraison, dans le respect des normes de qualité, d''hygiène et de sécurité.')
WHERE block_key = 'AXES_CONSOLIDES';
