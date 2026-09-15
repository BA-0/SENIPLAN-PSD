-- Revue client du 15/09/2026 : historique de SENICO, redige a partir des notes transmises par le consultant
-- (sources : senico-sa.com, leadersenegalais.com, jeuneafrique.com). Le texte n'est pose que si la rubrique
-- est encore vide : un historique deja redige par la Direction Generale reste tel quel.

UPDATE psd_narrative_blocks SET content =
'SENICO SA (Sénégalaise Industrie et Commerce) est née en 1989 à Keur Massar, dans la banlieue de Dakar, sous la forme d''un groupement d''intérêt économique, le GIE Dia et Frères, fondé par des frères, dont Abdoulaye Dia.
Les grandes étapes de son développement :
- 1989, les débuts : le GIE Dia et Frères commence ses activités dans une simple boutique de Keur Massar, en vendant une marque unique de thé, le Thé La Force ;
- années 1990, la croissance : l''entreprise se développe dans le négoce et l''import-export entre le Sénégal et la Gambie, avant de structurer ses propres activités industrielles ;
- la transformation en SENICO : la boutique de banlieue devient peu à peu une grande unité industrielle de transformation, de conditionnement et de commercialisation de produits alimentaires ;
- l''expansion : l''entreprise s''implante dans la zone industrielle de Diamniadio, sur de vastes infrastructures modernes, et emploie des milliers de personnes.
Aujourd''hui, SENICO est un acteur majeur de l''agroalimentaire au Sénégal. Ses marques, dont Kadi, Halib et Jadida, sont présentes dans de nombreux points de vente et exportées dans plusieurs pays.'
WHERE block_key = 'HISTORIQUE' AND (content IS NULL OR TRIM(content) = '');
