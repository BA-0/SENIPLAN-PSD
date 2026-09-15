-- Revue client de la note de synthese (15/09/2026), textes arretes par la Direction Generale :
--  - « Historique » et « Gouvernance » rejoignent la presentation de SENICO. L'historique reste a rediger par
--    SENICO sur l'ecran « Plan Stratégique de SENICO » : la note signale sa place tant qu'il est vide.
--  - Les missions decrivent les trois activites de SENICO : la production, la commercialisation et la logistique.
--  - L'organisation liste les dix directions et deux services du client, avec leurs responsables.
-- Les missions et l'organisation ne sont reecrites que si elles portent encore le texte d'origine.

INSERT IGNORE INTO psd_narrative_blocks (block_key, content) VALUES ('HISTORIQUE', NULL);

INSERT IGNORE INTO psd_narrative_blocks (block_key, content) VALUES ('GOUVERNANCE',
'La gouvernance de SENICO SA repose sur des instances qui fixent les orientations de l''entreprise, en contrôlent l''exécution et coordonnent l''action des directions.
- le Conseil d''Administration fixe les orientations de l''entreprise, approuve le plan stratégique et en examine chaque année l''état d''avancement ;
- la Direction Générale, assurée par Mouhamed DIA, Directeur Général, conduit la mise en œuvre de la stratégie et arbitre entre les directions ;
- le comité de direction (CODIR) coordonne chaque mois l''action des directions et suit les délais et les budgets ;
- le comité de pilotage du Plan Stratégique, présidé par la Direction Générale, arbitre les contributions des directions, valide les sections et suit l''exécution du plan.');

UPDATE psd_narrative_blocks SET content =
'SENICO SA (Sénégalaise Industrie & Commerce) exerce trois activités : la production, la commercialisation et la logistique.
À ce titre, l''entreprise remplit trois missions principales :
- produire, dans le respect des exigences de qualité, d''hygiène, de sécurité et d''environnement ;
- commercialiser ses produits auprès de sa clientèle ;
- assurer la logistique qui achemine ses produits jusqu''à ses clients.
Ces missions structurent l''ensemble du présent plan : chaque axe stratégique retenu par les directions y est rattaché.'
WHERE block_key = 'MISSIONS' AND content LIKE 'SENICO SA assure, sur l''ensemble du territoire national, la collecte%';

UPDATE psd_narrative_blocks SET content =
'L''entreprise est organisée en dix directions et deux services, chacun représenté par un groupe de travail pour l''élaboration du présent plan.
- la Direction Générale, dirigée par Mouhamed DIA ;
- la Direction des Systèmes d''Information, dirigée par Ousseynou SECK ;
- la Direction des Ressources Humaines, dirigée par El Hadj Souleymane NDIAYE ;
- la Direction Financière et Contrôle de Gestion, dirigée par Mame Marème DIA ;
- la Direction des Risques et Conformités, dirigée par Adama NDOYE ;
- la Direction Ventes Locales et Marketing, dirigée par Fatou NGOM ;
- la Direction Technique, dirigée par Rodrigue NSOH ;
- la Direction Industrielle, dirigée par Basile KOUAMEN ;
- la Direction QHSE, dirigée par El Houcine AIT HSINE ;
- la Direction Supply Chain, dirigée par Maimouna SYLLA ;
- le Service Juridique, dirigé par Amadou SYLLA DIOP ;
- le Service R&D, dirigé par Ndeye Fatou GUEYE.
Le comité de pilotage du Plan Stratégique, présidé par la Direction Générale, assure l''arbitrage entre les contributions, la validation des sections et le suivi de l''exécution du plan.'
WHERE block_key = 'ORGANISATION' AND content LIKE 'L''entreprise est organisée en cinq directions%';
