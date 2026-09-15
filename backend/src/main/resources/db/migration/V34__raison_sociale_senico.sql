-- Relecture de la note de synthese (15/09/2026) : la raison sociale s'ecrit partout comme dans l'historique,
-- « Sénégalaise Industrie et Commerce ». Les missions et l'introduction l'ecrivaient avec une esperluette.

UPDATE psd_narrative_blocks
SET content = REPLACE(content, 'Sénégalaise Industrie & Commerce', 'Sénégalaise Industrie et Commerce')
WHERE content LIKE '%Sénégalaise Industrie & Commerce%';
