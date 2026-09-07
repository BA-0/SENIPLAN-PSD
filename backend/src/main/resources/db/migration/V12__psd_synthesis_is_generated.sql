-- La "Synthese du PSD" n'est plus un bloc de texte saisi par l'admin (V11) mais un recap
-- calcule a partir des sections reprises dans le document (cf. PsdSynthesisBuilder).
-- On retire la ligne devenue sans objet : NarrativeBlockKey ne contient plus cette cle,
-- et la laisser ferait echouer la lecture des blocs narratifs.

DELETE FROM psd_narrative_blocks WHERE block_key = 'SYNTHESE_PSD';
