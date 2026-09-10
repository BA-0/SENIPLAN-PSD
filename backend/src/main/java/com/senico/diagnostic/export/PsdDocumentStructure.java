package com.senico.diagnostic.export;

import com.senico.diagnostic.domain.NarrativeBlockKey;

import java.util.List;

/**
 * Ordre et composition exacts du "Plan Strategique de SENICO" (ex-"Document final PSD
 * 2027-2031"), tels que definis par le sommaire transmis par le client (CANEVA PSD.pdf)
 * puis amendes en revue de presentation. Source unique de verite partagee par
 * {@link PdfExportService#exportPsdFinalDocument()} et {@link WordExportService#exportPsdFinalDocument()} :
 * ne pas dupliquer ce mapping ailleurs.
 *
 * <p>Ce document est le niveau consolide : il agrege les contributions de toutes les
 * directions. Le niveau par direction est le "Plan Strategique Sectoriel"
 * (PdfExportService#exportGroupRecap), qui reprend, lui, l'integralite des sections du
 * canevas pour la seule direction concernee.</p>
 *
 * <p>Sections du canevas volontairement absentes de ce document (S06, S07) : l'analyse causale
 * et l'inventaire sont des etapes de travail dont seule la synthese est publiee (S06B, S07B).
 * Elles restent dans le plan sectoriel et le "Document de consolidation"
 * (PdfExportService#exportConsolidated / ExcelExportService).</p>
 *
 * <p>S02 (ressources et competences), S05 (confrontation SWOT/TOWS) et S14 (matrice des risques)
 * ont ete reintegrees : elles sont saisies par les directions et un PSD de reference les publie
 * (tableaux 3 et 4 des annexes, chapitre "orientations strategiques croisees"). Les en ecarter
 * privait le document consolide d'un diagnostic deja disponible.</p>
 */
public final class PsdDocumentStructure {

    private PsdDocumentStructure() {
    }

    public sealed interface Entry permits MajorHeading, NarrativeEntry, SectionEntry, SynthesisEntry {
    }

    /**
     * Recap chiffre calcule a partir des sections reprises dans le document
     * (cf. {@link PsdSynthesisBuilder}), par opposition aux blocs narratifs qui, eux, sont saisis.
     */
    public record SynthesisEntry(String label) implements Entry {
    }

    public record MajorHeading(String title) implements Entry {
    }

    public record NarrativeEntry(String label, NarrativeBlockKey key) implements Entry {
    }

    /** Un ou plusieurs codes de section (SectionDef.code) rendus sous un meme intitule, ex. "Contexte : SWOT / PESTEL" -> S04, S03. */
    public record SectionEntry(String label, List<String> sectionCodes) implements Entry {
        public SectionEntry(String label, String sectionCode) {
            this(label, List.of(sectionCode));
        }
    }

    public static List<Entry> entries() {
        return List.of(
                new NarrativeEntry("Mot du DG", NarrativeBlockKey.MOT_DU_DG),
                new NarrativeEntry("Préambule", NarrativeBlockKey.PREAMBULE),
                new NarrativeEntry("Introduction", NarrativeBlockKey.INTRODUCTION),
                new NarrativeEntry("Approche méthodologique", NarrativeBlockKey.APPROCHE_METHODOLOGIQUE),
                new SynthesisEntry("Synthèse du PSD"),

                new MajorHeading("Présentation de la structure"),
                new NarrativeEntry("Rappel des missions", NarrativeBlockKey.MISSIONS),
                new NarrativeEntry("Organisation", NarrativeBlockKey.ORGANISATION),
                new NarrativeEntry("Ressources", NarrativeBlockKey.RESSOURCES),
                new NarrativeEntry("Bilan du plan stratégique précédent", NarrativeBlockKey.BILAN_PSD_PRECEDENT),

                new MajorHeading("Diagnostic stratégique"),
                new SectionEntry("Analyse des performances de l'année 2026", "S01B"),
                new SectionEntry("Analyse des parties prenantes", "S01"),
                new SectionEntry("Matrice d'analyse des ressources et des compétences", "S02"),
                new SectionEntry("Synthèse de l'analyse des ressources", "S03B"),
                new SectionEntry("Contexte : SWOT / PESTEL", List.of("S04", "S03")),
                new SectionEntry("Orientations stratégiques croisées (matrice SWOT / TOWS)", "S05"),
                new SectionEntry("Matrice d'analyse des risques", "S14"),
                new SectionEntry("Synthèse des enjeux et des contraintes", "S06B"),
                new NarrativeEntry("Défis à relever", NarrativeBlockKey.DEFIS_A_RELEVER),
                new NarrativeEntry("Enjeux", NarrativeBlockKey.ENJEUX),
                new NarrativeEntry("Facteurs clés de réussite et d'échec", NarrativeBlockKey.FACTEURS_CLES),

                new MajorHeading("Cadre stratégique"),
                new NarrativeEntry("Vision de SENICO", NarrativeBlockKey.VISION),
                new NarrativeEntry("Mission de SENICO", NarrativeBlockKey.MISSION),
                new NarrativeEntry("Valeurs de SENICO", NarrativeBlockKey.VALEURS),
                new SectionEntry("Vision, mission (mandat) et valeurs proposées par les directions", "S07B"),
                new NarrativeEntry("Axes stratégiques de SENICO", NarrativeBlockKey.AXES_CONSOLIDES),
                new SectionEntry("Orientations et objectifs stratégiques / Axes d'intervention et objectifs spécifiques", "S08"),
                new SectionEntry("Lignes d'actions", "S17"),

                new MajorHeading("Cadre de mise en œuvre, de suivi et d'évaluation"),
                new SectionEntry("Cadre logique", "S09"),
                new SectionEntry("Synthèse du cadre logique", "S09B"),
                new SectionEntry("Budget", "S11"),
                new SectionEntry("Opérationnalisation", "S10"),
                new NarrativeEntry("Dispositif de pilotage et de suivi-évaluation", NarrativeBlockKey.DISPOSITIF_PILOTAGE),
                new SectionEntry("Dispositif de suivi évaluation", "S13"),
                new SectionEntry("Cadre de mesure de rendement", "S12"),
                new SectionEntry("Plan d'évolution des effectifs", "S14B"),
                new SectionEntry("Plan de financement", "S15"),

                new NarrativeEntry("Conclusion", NarrativeBlockKey.CONCLUSION)
        );
    }
}
