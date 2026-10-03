-- Document genere sur lequel la Direction Generale a corrige la note de synthese, en JSON : il
-- permet de fusionner sa version corrigee avec les sections approuvees depuis, au lieu de les ignorer.

ALTER TABLE synthesis_note_edits ADD COLUMN source_content LONGTEXT NULL AFTER content;
