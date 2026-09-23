-- Demande client du 23/09/2026.
-- 1. Les valeurs de SENICO s'ecrivent F.I.E.R. : Foi, Integrite, Engagement, Responsabilite. Elles
--    remplacent les six valeurs du texte arrete jusque-la (qualite, sens du client, fiabilite, rigueur,
--    securite, innovation). Meme format que ce bloc : une valeur par ligne, « Valeur : explication ».
-- 2. Le nom de la Direction des Systemes d'Information, herite du referentiel initial sans accent,
--    s'affichait tel quel dans la note de synthese et le Plan Strategique.
UPDATE psd_narrative_blocks
SET content = CONCAT(
        'Foi : SENICO SA croit en sa mission et en l''avenir de ses marques. Cette conviction, héritée de ses fondateurs, porte l''entreprise depuis la boutique de Keur Massar et guide ses choix sur la durée du plan.\n',
        'Intégrité : l''entreprise agit avec honnêteté et transparence envers ses consommateurs, ses distributeurs, ses partenaires et ses équipes. Elle respecte ses engagements et les règles qui encadrent son activité.\n',
        'Engagement : chaque direction et chaque collaborateur s''investit pour la qualité des produits, la satisfaction des clients et la réussite du plan.\n',
        'Responsabilité : SENICO SA assume les conséquences de ses décisions envers les consommateurs, le personnel, l''environnement et la société, et rend compte de ses résultats.'),
    updated_at = CURRENT_TIMESTAMP
WHERE block_key = 'VALEURS';

UPDATE work_groups SET name = 'Direction des Systèmes d''Information'
WHERE name = 'Direction des Systemes d''Information';
