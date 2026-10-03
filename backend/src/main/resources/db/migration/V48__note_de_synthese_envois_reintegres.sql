-- Les copies envoyees par les onglets d'avant la fusion sont desormais reintegrees dans la note
-- (03/10/2026) : on note quand, pour ne pas les reprendre deux fois. Celles deja recues le seront
-- au prochain demarrage.

ALTER TABLE synthesis_note_rejected_saves ADD COLUMN reintegrated_at TIMESTAMP NULL AFTER content;
