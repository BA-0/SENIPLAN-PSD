package com.senico.diagnostic.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.senico.diagnostic.domain.GroupSectionStatus;
import com.senico.diagnostic.domain.NarrativeBlockKey;
import com.senico.diagnostic.domain.PsdNarrativeBlock;
import com.senico.diagnostic.domain.SectionDef;
import com.senico.diagnostic.domain.SectionResponse;
import com.senico.diagnostic.domain.SectionStatus;
import com.senico.diagnostic.domain.WorkGroup;
import com.senico.diagnostic.export.PsdDocumentStructure.Entry;
import com.senico.diagnostic.export.PsdDocumentStructure.MajorHeading;
import com.senico.diagnostic.export.PsdDocumentStructure.NarrativeEntry;
import com.senico.diagnostic.export.PsdDocumentStructure.SectionEntry;
import com.senico.diagnostic.export.PsdDocumentStructure.SynthesisEntry;
import com.senico.diagnostic.repository.GroupSectionStatusRepository;
import com.senico.diagnostic.repository.PsdNarrativeBlockRepository;
import com.senico.diagnostic.repository.SectionDefRepository;
import com.senico.diagnostic.repository.SectionResponseRepository;
import com.senico.diagnostic.repository.WorkGroupRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WordExportService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    /** Date en toutes lettres de la page de garde, comme sur un PSD publie : « Dakar, le 4 septembre 2026 ». */
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale.FRENCH);
    private static final String PRIMARY_HEX = "2D7A45";
    private static final String DARK_HEX = "1E293B";
    private static final String SLATE_HEX = "64748B";
    /** Meme logo que les couvertures PDF, pour que les deux formats sortent identiques. */
    private static final String LOGO_RESOURCE = "/branding/logo-senico.png";
    /** Le logo fait 514 x 98 px : ces deux valeurs en conservent le rapport. */
    private static final int LOGO_WIDTH_PT = 190;
    private static final int LOGO_HEIGHT_PT = 36;

    private final SectionDefRepository sectionDefRepository;
    private final SectionResponseRepository sectionResponseRepository;
    private final GroupSectionStatusRepository groupSectionStatusRepository;
    private final WorkGroupRepository workGroupRepository;
    private final PsdNarrativeBlockRepository psdNarrativeBlockRepository;
    private final SectionExportRenderer sectionExportRenderer;
    private final ExportContentReader exportContentReader;
    private final PsdSynthesisBuilder psdSynthesisBuilder;
    private final PsdBriefBuilder psdBriefBuilder;
    private final WordBlockEmitter wordBlockEmitter;

    public byte[] exportGroupRecap(WorkGroup group) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            addCoverPage(doc, group);

            List<SectionDef> sections = sectionDefRepository.findAllByOrderByOrderAsc();
            for (SectionDef section : sections) {
                doc.createParagraph().setPageBreak(true);
                addSection(doc, section, group);
            }

            doc.write(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Erreur de generation du document Word", e);
        }
    }

    public byte[] exportPsdFinalDocument() {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            List<WorkGroup> groups = workGroupRepository.findAll();
            List<Entry> entries = PsdDocumentStructure.entries();

            Map<String, SectionResponse> responsesByKey = sectionResponseRepository.findAll().stream()
                    .collect(Collectors.toMap(r -> r.getGroup().getId() + ":" + r.getSection().getId(), r -> r));
            Map<String, GroupSectionStatus> statusesByKey = groupSectionStatusRepository.findAllWithGroupAndSection().stream()
                    .collect(Collectors.toMap(s -> s.getGroup().getId() + ":" + s.getSection().getId(), s -> s));
            Map<NarrativeBlockKey, PsdNarrativeBlock> narrativeByKey = psdNarrativeBlockRepository.findAll().stream()
                    .collect(Collectors.toMap(PsdNarrativeBlock::getKey, b -> b));

            // La consolidation ne reprend que ce que le DG a approuve (cf. PsdApprovedContent).
            responsesByKey = PsdApprovedContent.approvedOnly(responsesByKey, statusesByKey);

            addFooter(doc, "Plan Stratégique de SENICO — PSD 2027-2031");
            addPsdFinalCoverPage(doc);
            doc.createParagraph().setPageBreak(true);
            addPsdFinalSommaire(doc, entries, groups);

            Map<String, SectionDef> sectionsByCode = sectionDefRepository.findAllByOrderByOrderAsc().stream()
                    .collect(Collectors.toMap(SectionDef::getCode, sd -> sd));
            for (Entry entry : entries) {
                doc.createParagraph().setPageBreak(true);
                if (entry instanceof MajorHeading heading) {
                    addPsdMajorHeading(doc, heading);
                } else if (entry instanceof NarrativeEntry narrative && narrative.key() == NarrativeBlockKey.AXES_CONSOLIDES) {
                    // Contenu structure (JSON) : il se lit en axes, rattachements et budget, pas en texte.
                    addWordSectionHeader(doc, narrative.label());
                    wordBlockEmitter.emit(doc, psdBriefBuilder.strategicAxes(groups, sectionsByCode, responsesByKey,
                            statusesByKey, narratives()));
                } else if (entry instanceof NarrativeEntry narrative) {
                    addPsdNarrative(doc, narrative, narrativeByKey.get(narrative.key()));
                } else if (entry instanceof SynthesisEntry synthesis) {
                    addPsdSynthesis(doc, synthesis, groups, responsesByKey, statusesByKey);
                } else if (entry instanceof SectionEntry sectionEntry) {
                    addPsdSectionEntry(doc, sectionEntry, groups, responsesByKey, statusesByKey);
                }
            }

            doc.write(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Erreur de generation du document final PSD", e);
        }
    }

    /** Note de synthese : pendant Word de {@link PdfExportService#exportSynthesisNote()}. */
    public byte[] exportSynthesisNote() {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            List<WorkGroup> groups = workGroupRepository.findAll();
            Map<String, SectionDef> sectionsByCode = sectionDefRepository.findAllByOrderByOrderAsc().stream()
                    .collect(Collectors.toMap(SectionDef::getCode, sd -> sd));
            Map<String, SectionResponse> responsesByKey = sectionResponseRepository.findAll().stream()
                    .collect(Collectors.toMap(r -> r.getGroup().getId() + ":" + r.getSection().getId(), r -> r));
            Map<String, GroupSectionStatus> statusesByKey = groupSectionStatusRepository.findAllWithGroupAndSection().stream()
                    .collect(Collectors.toMap(s -> s.getGroup().getId() + ":" + s.getSection().getId(), s -> s));

            // Meme regle de perimetre que le PDF : seules les sections approuvees par le DG sont
            // resumees. Le filtre etait absent ici, la ou les trois autres exports l'appliquent.
            responsesByKey = PsdApprovedContent.approvedOnly(responsesByKey, statusesByKey);

            List<ExportBlock> blocks = psdBriefBuilder.build(groups, sectionsByCode, responsesByKey, statusesByKey, narratives());

            addFooter(doc, "Plan Stratégique de Développement 2027-2031 — Note de synthèse");
            addSynthesisNoteCoverPage(doc);
            doc.createParagraph().setPageBreak(true);
            addSynthesisNoteSommaire(doc, blocks);
            // Pas de saut de page ici : chaque partie de la note ouvre deja la sienne.
            wordBlockEmitter.emit(doc, blocks);

            doc.write(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Erreur de generation de la note de synthese", e);
        }
    }

    /** Blocs narratifs arretes par la Direction Generale, indexes par cle ; un bloc absent vaut vide. */
    private Map<NarrativeBlockKey, String> narratives() {
        Map<NarrativeBlockKey, String> narratives = new java.util.EnumMap<>(NarrativeBlockKey.class);
        for (PsdNarrativeBlock block : psdNarrativeBlockRepository.findAll()) {
            narratives.put(block.getKey(), block.getContent() == null ? "" : block.getContent());
        }
        return narratives;
    }

    /**
     * Pied de page Word : intitule du document et pagination. Les champs PAGE et NUMPAGES sont
     * inseres comme champs Word plutot que comme texte, pour que la pagination reste juste
     * apres une relecture qui ajoute ou retire des pages.
     *
     * <p>A appeler avant d'ecrire le corps : la politique d'en-tetes s'attache a la section du
     * document, qui doit exister avant que le contenu ne soit pagine.</p>
     */
    private void addFooter(XWPFDocument doc, String label) {
        CTSectPr sectPr = doc.getDocument().getBody().isSetSectPr()
                ? doc.getDocument().getBody().getSectPr()
                : doc.getDocument().getBody().addNewSectPr();
        XWPFHeaderFooterPolicy policy = new XWPFHeaderFooterPolicy(doc, sectPr);

        XWPFParagraph paragraph = new XWPFParagraph(CTP.Factory.newInstance(), doc);
        paragraph.setAlignment(ParagraphAlignment.CENTER);

        XWPFRun prefix = paragraph.createRun();
        prefix.setText(label + "   —   Page ");
        prefix.setFontSize(8);
        prefix.setColor(SLATE_HEX);
        paragraph.getCTP().addNewFldSimple().setInstr("PAGE \\* MERGEFORMAT");

        XWPFRun separator = paragraph.createRun();
        separator.setText(" / ");
        separator.setFontSize(8);
        separator.setColor(SLATE_HEX);
        paragraph.getCTP().addNewFldSimple().setInstr("NUMPAGES \\* MERGEFORMAT");

        policy.createFooter(XWPFHeaderFooterPolicy.DEFAULT, new XWPFParagraph[]{paragraph});
    }

    /** Pendant Word de {@link PdfExportService#addSynthesisNoteCoverPage(com.lowagie.text.Document)}. */
    private void addSynthesisNoteCoverPage(XWPFDocument doc) {
        addCoverLogo(doc);
        addCenteredTitle(doc, "PLAN STRATÉGIQUE DE DÉVELOPPEMENT", 26, PRIMARY_HEX);
        addCenteredTitle(doc, "2027 - 2031", 26, PRIMARY_HEX);
        addCenteredTitle(doc, "SENICO SA", 14, "64748B");
        addCenteredTitle(doc, "NOTE DE SYNTHÈSE", 16, DARK_HEX);
        addCenteredTitle(doc,
                "Le résumé consolidé de l'ensemble des directions — diagnostic, enjeux, cadre stratégique, "
                        + "budget et pilotage — pour le Conseil d'Administration et le comité de pilotage.",
                11, "64748B");
        addCenteredTitle(doc, "Dakar, le " + java.time.LocalDate.now().format(LONG_DATE), 12, PRIMARY_HEX);
        addCenteredTitle(doc, "Export généré le " + java.time.LocalDateTime.now().format(DATE_FORMAT)
                + " — document interne, confidentiel", 9, "64748B");
    }

    /**
     * Sommaire de la note : les parties en gras et leurs sous-parties en retrait, dans l'ordre ou
     * {@link PsdBriefBuilder} les compose. Sans numeros de page : Word repagine le document a
     * l'ouverture selon sa propre mise en page, un numero calcule ici serait faux.
     */
    private void addSynthesisNoteSommaire(XWPFDocument doc, List<ExportBlock> blocks) {
        addWordSectionHeader(doc, "SOMMAIRE");
        for (ExportBlock block : blocks) {
            if (!(block instanceof ExportBlock.Heading heading) || heading.level() > 2) {
                continue;
            }
            boolean part = heading.level() == 1;
            XWPFParagraph item = doc.createParagraph();
            item.setSpacingBefore(part ? 120 : 0);
            item.setIndentationLeft(part ? 0 : 400);
            XWPFRun run = item.createRun();
            run.setText(heading.text());
            run.setBold(part);
            run.setFontSize(part ? 11 : 10);
            run.setColor(DARK_HEX);
        }
    }

    /**
     * Logo SENICO en tete de page de garde, comme sur les couvertures PDF. Un document Word
     * remis a la tutelle ou au Conseil d'Administration sort sinon sans identite visuelle, la
     * ou son pendant PDF est bien en-tete.
     *
     * <p>Un logo introuvable ou illisible ne doit pas empecher la generation du document : on
     * poursuit sans lui plutot que de rendre l'export indisponible pour une image.</p>
     */
    private void addCoverLogo(XWPFDocument doc) {
        XWPFParagraph paragraph = doc.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        paragraph.setSpacingAfter(240);
        try (InputStream in = getClass().getResourceAsStream(LOGO_RESOURCE)) {
            if (in == null) {
                return;
            }
            paragraph.createRun().addPicture(in, XWPFDocument.PICTURE_TYPE_PNG, "logo-senico.png",
                    Units.toEMU(LOGO_WIDTH_PT), Units.toEMU(LOGO_HEIGHT_PT));
        } catch (IOException | InvalidFormatException e) {
            // Page de garde sans logo : degrade acceptable, contrairement a un export en erreur.
        }
    }

    private void addCenteredTitle(XWPFDocument doc, String text, int size, String colorHex) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        p.setSpacingAfter(160);
        XWPFRun run = p.createRun();
        run.setText(text);
        run.setBold(size >= 16);
        run.setFontSize(size);
        run.setColor(colorHex);
    }

    private void addPsdFinalCoverPage(XWPFDocument doc) {
        addCoverLogo(doc);

        XWPFParagraph title = doc.createParagraph();
        title.setAlignment(ParagraphAlignment.CENTER);
        title.setSpacingBefore(1200);
        XWPFRun titleRun = title.createRun();
        titleRun.setText("SENICO SA — Plan Stratégique");
        titleRun.setBold(true);
        titleRun.setFontSize(22);
        titleRun.setColor(PRIMARY_HEX);

        XWPFParagraph subtitle = doc.createParagraph();
        subtitle.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun subtitleRun = subtitle.createRun();
        subtitleRun.setText("Plan Stratégique de Développement (PSD) 2027-2031");
        subtitleRun.setFontSize(14);
        subtitleRun.setColor("64748B");

        XWPFParagraph docTitle = doc.createParagraph();
        docTitle.setAlignment(ParagraphAlignment.CENTER);
        docTitle.setSpacingBefore(600);
        XWPFRun docTitleRun = docTitle.createRun();
        docTitleRun.setText("PLAN STRATÉGIQUE DE SENICO");
        docTitleRun.setBold(true);
        docTitleRun.setFontSize(16);

        XWPFParagraph meta = doc.createParagraph();
        meta.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun metaRun = meta.createRun();
        metaRun.setText("Export généré le " + java.time.LocalDateTime.now().format(DATE_FORMAT));
        metaRun.setFontSize(11);
        metaRun.setColor("64748B");
    }

    private void addPsdFinalSommaire(XWPFDocument doc, List<Entry> entries, List<WorkGroup> groups) {
        XWPFParagraph header = doc.createParagraph();
        XWPFRun headerRun = header.createRun();
        headerRun.setText("Sommaire");
        headerRun.setBold(true);
        headerRun.setFontSize(16);
        headerRun.setColor(PRIMARY_HEX);
        header.setBorderBottom(Borders.SINGLE);

        for (Entry entry : entries) {
            XWPFParagraph p = doc.createParagraph();
            XWPFRun run = p.createRun();
            if (entry instanceof MajorHeading heading) {
                p.setSpacingBefore(160);
                run.setText(heading.title());
                run.setBold(true);
                run.setFontSize(12);
                run.setColor(DARK_HEX);
            } else {
                String label;
                if (entry instanceof NarrativeEntry n) {
                    label = n.label();
                } else if (entry instanceof SynthesisEntry sy) {
                    label = sy.label();
                } else {
                    label = ((SectionEntry) entry).label();
                }
                p.setIndentationLeft(300);
                run.setText("• " + label);
                run.setFontSize(10);
                run.setColor(DARK_HEX);
            }
        }

        XWPFParagraph legendTitle = doc.createParagraph();
        legendTitle.setSpacingBefore(300);
        XWPFRun legendTitleRun = legendTitle.createRun();
        legendTitleRun.setText("Légende des directions");
        legendTitleRun.setBold(true);
        legendTitleRun.setFontSize(13);
        legendTitleRun.setColor(PRIMARY_HEX);

        for (WorkGroup group : groups) {
            XWPFParagraph p = doc.createParagraph();
            XWPFRun swatch = p.createRun();
            swatch.setText("■ ");
            swatch.setColor(hexToWordColor(group.getColor()));
            XWPFRun label = p.createRun();
            label.setText(group.getName());
            label.setColor(DARK_HEX);
            label.setFontSize(10);
        }
    }

    private void addPsdMajorHeading(XWPFDocument doc, MajorHeading heading) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        p.setSpacingBefore(3000);
        XWPFRun run = p.createRun();
        run.setText(heading.title());
        run.setBold(true);
        run.setFontSize(24);
        run.setColor(PRIMARY_HEX);
    }

    private String exclusionReason(GroupSectionStatus status) {
        SectionStatus value = status != null ? status.getStatus() : SectionStatus.NOT_STARTED;
        if (value == SectionStatus.NOT_STARTED) {
            return "non renseignée";
        }
        if (value == SectionStatus.VALIDATED) {
            return "en attente d'approbation de la Direction Générale";
        }
        return "non validée — " + SectionExportRenderer.statusLabel(value.name()).toLowerCase();
    }

    private void addPsdSynthesis(XWPFDocument doc, SynthesisEntry synthesis, List<WorkGroup> groups,
                                  Map<String, SectionResponse> responsesByKey,
                                  Map<String, GroupSectionStatus> statusesByKey) {
        addWordSectionHeader(doc, synthesis.label());

        Map<String, SectionDef> sectionsByCode = sectionDefRepository.findAllByOrderByOrderAsc().stream()
                .collect(Collectors.toMap(SectionDef::getCode, sd -> sd));
        long approved = statusesByKey.values().stream().filter(PsdApprovedContent::isApproved).count();
        wordBlockEmitter.emit(doc, psdSynthesisBuilder.build(groups, sectionsByCode, responsesByKey,
                (int) approved, statusesByKey.size()));
    }

    private void addPsdNarrative(XWPFDocument doc, NarrativeEntry narrative, PsdNarrativeBlock block) {
        addWordSectionHeader(doc, narrative.label());

        String content = block != null && block.getContent() != null ? block.getContent() : "";
        if (content.isBlank()) {
            XWPFParagraph p = doc.createParagraph();
            XWPFRun run = p.createRun();
            run.setText("(contenu à renseigner)");
            run.setItalic(true);
            run.setColor("64748B");
            return;
        }
        // Puces et intertitres rediges dans la zone de texte (cf. PsdNarrativeText).
        wordBlockEmitter.emit(doc, PsdNarrativeText.blocks(content));
    }

    private static final String[] PESTEL_AXES = {
            "POLITIQUE", "ECONOMIQUE", "SOCIAL_CULTUREL", "TECHNOLOGIQUE", "ENVIRONNEMENTAL", "LEGAL"
    };

    private void addPsdSectionEntry(XWPFDocument doc, SectionEntry sectionEntry, List<WorkGroup> groups,
                                     Map<String, SectionResponse> responsesByKey,
                                     Map<String, GroupSectionStatus> statusesByKey) {
        addWordSectionHeader(doc, sectionEntry.label());

        for (String code : sectionEntry.sectionCodes()) {
            SectionDef section = sectionDefRepository.findByCode(code)
                    .orElseThrow(() -> new IllegalStateException("Section introuvable : " + code));

            if (sectionEntry.sectionCodes().size() > 1) {
                XWPFParagraph subHeader = doc.createParagraph();
                subHeader.setSpacingBefore(160);
                XWPFRun run = subHeader.createRun();
                run.setText(section.getCode() + " — " + section.getTitle());
                run.setBold(true);
                run.setFontSize(13);
                run.setColor("64748B");
            }

            switch (code) {
                case "S01" -> addPsdMergedStakeholders(doc, section, groups, responsesByKey);
                case "S04" -> addPsdMergedSwot(doc, section, groups, responsesByKey);
                case "S03" -> addPsdMergedPestel(doc, section, groups, responsesByKey);
                default -> addPsdPerGroupSection(doc, section, groups, responsesByKey, statusesByKey);
            }
        }
    }

    /** Rendu historique : un bloc complet par direction (sections qui restent propres a chaque direction : axes, budget, plan d'actions...). */
    private void addPsdPerGroupSection(XWPFDocument doc, SectionDef section, List<WorkGroup> groups,
                                        Map<String, SectionResponse> responsesByKey,
                                        Map<String, GroupSectionStatus> statusesByKey) {
        // Rendu fusionne : un seul tableau par rubrique, toutes directions confondues
        // (cf. PsdSectionMerger), au lieu d'un bloc complet repete par direction.
        List<PsdSectionMerger.GroupBlocks> perGroup = new ArrayList<>();
        for (WorkGroup group : groups) {
            String key = group.getId() + ":" + section.getId();
            SectionResponse response = responsesByKey.get(key);
            GroupSectionStatus status = statusesByKey.get(key);
            JsonNode content = exportContentReader.read(group.getId(), section, response);
            Integer version = response != null ? response.getVersion() : 0;

            // Reponses deja filtrees sur les sections approuvees par le DG : une reponse absente
            // alors que le statut n'est pas NOT_STARTED signale un contenu retenu, pas une
            // section vide.
            boolean included = PsdApprovedContent.isApproved(status);
            ExportSectionData data = new ExportSectionData(section, content, version, status, !included);
            perGroup.add(new PsdSectionMerger.GroupBlocks(
                    group,
                    included ? sectionExportRenderer.render(data) : List.of(),
                    included,
                    included ? null : exclusionReason(status)));
        }
        wordBlockEmitter.emit(doc, PsdSectionMerger.merge(perGroup));
    }

    /**
     * Rendu fusionne : les diagnostics des directions (parties prenantes, SWOT, PESTEL) se
     * recoupent souvent (ex. plusieurs directions citent "electricite" comme menace) ; chaque
     * element n'apparait qu'une seule fois, avec un carre de couleur par direction contributrice.
     */
    private void addPsdMergedStakeholders(XWPFDocument doc, SectionDef section, List<WorkGroup> groups,
                                           Map<String, SectionResponse> responsesByKey) {
        String[] headers = {"Catégorie", "Portée", "Rôles", "Attentes", "Stratégie d'adaptation", "Importance", "Influence", "Actions", "Directions"};

        Map<String, String[]> rowValuesByKey = new LinkedHashMap<>();
        Map<String, List<WorkGroup>> contributorsByKey = new LinkedHashMap<>();

        for (WorkGroup group : groups) {
            JsonNode content = contentFor(group, section, responsesByKey);
            for (JsonNode row : JsonUtil.arr(content, "rows")) {
                String[] values = {
                        SectionLabels.stakeholderCategory(JsonUtil.text(row, "category")),
                        SectionLabels.stakeholderScope(JsonUtil.text(row, "scope")),
                        JsonUtil.text(row, "roles"),
                        JsonUtil.text(row, "expectations"),
                        JsonUtil.text(row, "adaptationStrategy"),
                        JsonUtil.text(row, "importance"),
                        JsonUtil.text(row, "influence"),
                        JsonUtil.text(row, "actions")
                };
                String key = PsdCrossGroupMerge.normalize(String.join("|", values));
                if (key.isBlank()) {
                    continue;
                }
                rowValuesByKey.putIfAbsent(key, values);
                List<WorkGroup> contributors = contributorsByKey.computeIfAbsent(key, k -> new ArrayList<>());
                if (!contributors.contains(group)) {
                    contributors.add(group);
                }
            }
        }

        if (rowValuesByKey.isEmpty()) {
            XWPFParagraph p = doc.createParagraph();
            XWPFRun run = p.createRun();
            run.setText("Aucune donnée saisie pour cette section.");
            run.setItalic(true);
            run.setColor("64748B");
            return;
        }

        XWPFTable table = doc.createTable(rowValuesByKey.size() + 1, headers.length);
        table.setWidth("100%");
        for (int c = 0; c < headers.length; c++) {
            setCell(table.getRow(0).getCell(c), headers[c], true, ParagraphAlignment.LEFT, "FFFFFF", PRIMARY_HEX, 9);
        }
        int r = 1;
        for (Map.Entry<String, String[]> entry : rowValuesByKey.entrySet()) {
            XWPFTableRow row = table.getRow(r++);
            String[] values = entry.getValue();
            for (int c = 0; c < values.length; c++) {
                setCell(row.getCell(c), values[c], false, ParagraphAlignment.LEFT, DARK_HEX, null, 9);
            }
            setDotsCell(row.getCell(headers.length - 1), contributorsByKey.get(entry.getKey()));
        }
        doc.createParagraph().setSpacingAfter(80);
        addMergeCaption(doc);
    }

    private void addPsdMergedSwot(XWPFDocument doc, SectionDef section, List<WorkGroup> groups,
                                   Map<String, SectionResponse> responsesByKey) {
        String[] fields = {"strengths", "weaknesses", "opportunities", "threats"};
        String[] labels = {"Forces", "Faiblesses", "Opportunités", "Menaces"};
        String[] tints = {"DCFCE7", "FFEDD5", "DBEAFE", "FEE2E2"};

        Map<WorkGroup, JsonNode> contentByGroup = new LinkedHashMap<>();
        for (WorkGroup group : groups) {
            contentByGroup.put(group, contentFor(group, section, responsesByKey));
        }

        XWPFTable table = doc.createTable(2, 2);
        table.setWidth("100%");

        for (int i = 0; i < fields.length; i++) {
            List<Map.Entry<WorkGroup, String>> entries = new ArrayList<>();
            for (WorkGroup group : groups) {
                for (String item : JsonUtil.strList(contentByGroup.get(group), fields[i])) {
                    entries.add(Map.entry(group, item));
                }
            }
            List<PsdCrossGroupMerge.MergedItem> merged = PsdCrossGroupMerge.merge(entries, false);

            XWPFTableCell cell = table.getRow(i / 2).getCell(i % 2);
            cell.setColor(tints[i]);
            XWPFParagraph titleParagraph = cell.getParagraphs().get(0);
            XWPFRun titleRun = titleParagraph.createRun();
            titleRun.setText(labels[i]);
            titleRun.setBold(true);
            titleRun.setFontSize(10);
            titleRun.setColor(DARK_HEX);

            if (merged.isEmpty()) {
                XWPFRun run = cell.addParagraph().createRun();
                run.setText("Aucun élément.");
                run.setFontSize(9);
                run.setColor(DARK_HEX);
            } else {
                for (PsdCrossGroupMerge.MergedItem item : merged) {
                    XWPFParagraph p = cell.addParagraph();
                    XWPFRun run = p.createRun();
                    run.setText("•  " + item.text());
                    run.setFontSize(9);
                    run.setColor(DARK_HEX);
                    appendContributorDots(p, item.contributors());
                }
            }
        }
        doc.createParagraph().setSpacingAfter(80);
        addMergeCaption(doc);
    }

    private void addPsdMergedPestel(XWPFDocument doc, SectionDef section, List<WorkGroup> groups,
                                     Map<String, SectionResponse> responsesByKey) {
        Map<WorkGroup, JsonNode> contentByGroup = new LinkedHashMap<>();
        for (WorkGroup group : groups) {
            contentByGroup.put(group, contentFor(group, section, responsesByKey));
        }

        XWPFTable table = doc.createTable(PESTEL_AXES.length + 1, 4);
        table.setWidth("100%");
        String[] headers = {"Axe", "Menaces", "Opportunités", "Actions"};
        for (int c = 0; c < headers.length; c++) {
            setCell(table.getRow(0).getCell(c), headers[c], true, ParagraphAlignment.LEFT, "FFFFFF", PRIMARY_HEX, 9);
        }

        String[] columns = {"threats", "opportunities", "actions"};
        for (int a = 0; a < PESTEL_AXES.length; a++) {
            String axisCode = PESTEL_AXES[a];
            XWPFTableRow row = table.getRow(a + 1);
            setCell(row.getCell(0), SectionLabels.pestel(axisCode), false, ParagraphAlignment.LEFT, DARK_HEX, null, 9);

            for (int c = 0; c < columns.length; c++) {
                List<Map.Entry<WorkGroup, String>> entries = new ArrayList<>();
                for (WorkGroup group : groups) {
                    for (JsonNode contentRow : JsonUtil.arr(contentByGroup.get(group), "rows")) {
                        if (axisCode.equals(JsonUtil.text(contentRow, "axis"))) {
                            String text = JsonUtil.text(contentRow, columns[c]);
                            if (!text.isBlank()) {
                                entries.add(Map.entry(group, text));
                            }
                        }
                    }
                }
                List<PsdCrossGroupMerge.MergedItem> merged = PsdCrossGroupMerge.merge(entries, true);

                XWPFTableCell cell = row.getCell(c + 1);
                XWPFParagraph first = cell.getParagraphs().get(0);
                if (merged.isEmpty()) {
                    XWPFRun run = first.createRun();
                    run.setText("—");
                    run.setFontSize(9);
                    run.setColor("64748B");
                } else {
                    boolean firstItem = true;
                    for (PsdCrossGroupMerge.MergedItem item : merged) {
                        XWPFParagraph p = firstItem ? first : cell.addParagraph();
                        firstItem = false;
                        XWPFRun run = p.createRun();
                        run.setText("•  " + item.text());
                        run.setFontSize(9);
                        run.setColor(DARK_HEX);
                        appendContributorDots(p, item.contributors());
                    }
                }
            }
        }
        doc.createParagraph().setSpacingAfter(80);
        addMergeCaption(doc);
    }

    private void setCell(XWPFTableCell cell, String text, boolean bold, ParagraphAlignment align, String colorHex, String bgHex, int fontSize) {
        if (bgHex != null) {
            cell.setColor(bgHex);
        }
        XWPFParagraph p = cell.getParagraphs().isEmpty() ? cell.addParagraph() : cell.getParagraphs().get(0);
        p.setAlignment(align);
        XWPFRun run = p.createRun();
        run.setText(text == null ? "" : text);
        run.setBold(bold);
        run.setFontSize(fontSize);
        run.setColor(colorHex);
    }

    private void appendContributorDots(XWPFParagraph p, List<WorkGroup> contributors) {
        for (WorkGroup group : contributors) {
            XWPFRun dot = p.createRun();
            dot.setText(" ■");
            dot.setColor(hexToWordColor(group.getColor()));
        }
    }

    private void setDotsCell(XWPFTableCell cell, List<WorkGroup> contributors) {
        XWPFParagraph p = cell.getParagraphs().isEmpty() ? cell.addParagraph() : cell.getParagraphs().get(0);
        appendContributorDots(p, contributors);
    }

    private void addMergeCaption(XWPFDocument doc) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingAfter(160);
        XWPFRun run = p.createRun();
        run.setText("■ = direction(s) ayant mentionné cet élément (voir légende des directions au sommaire).");
        run.setItalic(true);
        run.setFontSize(8);
        run.setColor("64748B");
    }

    private JsonNode contentFor(WorkGroup group, SectionDef section, Map<String, SectionResponse> responsesByKey) {
        SectionResponse response = responsesByKey.get(group.getId() + ":" + section.getId());
        return exportContentReader.read(group.getId(), section, response);
    }

    private void addWordSectionHeader(XWPFDocument doc, String label) {
        XWPFParagraph header = doc.createParagraph();
        XWPFRun headerRun = header.createRun();
        headerRun.setText(label);
        headerRun.setBold(true);
        headerRun.setFontSize(16);
        headerRun.setColor(PRIMARY_HEX);
        header.setBorderBottom(Borders.SINGLE);
    }

    private String hexToWordColor(String hex) {
        if (hex == null || !hex.matches("#[0-9A-Fa-f]{6}")) {
            return "64748B";
        }
        return hex.substring(1);
    }

    private void addCoverPage(XWPFDocument doc, WorkGroup group) {
        addCoverLogo(doc);

        XWPFParagraph title = doc.createParagraph();
        title.setAlignment(ParagraphAlignment.CENTER);
        title.setSpacingBefore(1200);
        XWPFRun titleRun = title.createRun();
        titleRun.setText("SENICO SA — Plan Stratégique");
        titleRun.setBold(true);
        titleRun.setFontSize(22);
        titleRun.setColor(PRIMARY_HEX);

        XWPFParagraph subtitle = doc.createParagraph();
        subtitle.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun subtitleRun = subtitle.createRun();
        subtitleRun.setText("Plan Stratégique de Développement (PSD) 2027-2031");
        subtitleRun.setFontSize(14);
        subtitleRun.setColor("64748B");

        XWPFParagraph groupName = doc.createParagraph();
        groupName.setAlignment(ParagraphAlignment.CENTER);
        groupName.setSpacingBefore(600);
        XWPFRun groupRun = groupName.createRun();
        groupRun.setText(group.getName());
        groupRun.setBold(true);
        groupRun.setFontSize(16);

        XWPFParagraph meta = doc.createParagraph();
        meta.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun metaRun = meta.createRun();
        metaRun.setText("Export généré le " + java.time.LocalDateTime.now().format(DATE_FORMAT));
        metaRun.setFontSize(11);
        metaRun.setColor("64748B");
    }

    private void addSection(XWPFDocument doc, SectionDef section, WorkGroup group) {
        XWPFParagraph header = doc.createParagraph();
        XWPFRun headerRun = header.createRun();
        headerRun.setText(section.getCode() + " — " + section.getTitle());
        headerRun.setBold(true);
        headerRun.setFontSize(16);
        headerRun.setColor(PRIMARY_HEX);
        header.setBorderBottom(Borders.SINGLE);

        List<ExportBlock> blocks = sectionExportRenderer.render(loadExportData(group.getId(), section));
        wordBlockEmitter.emit(doc, blocks);
    }

    private ExportSectionData loadExportData(Long groupId, SectionDef section) {
        SectionResponse response = sectionResponseRepository.findByGroupIdAndSectionId(groupId, section.getId()).orElse(null);
        GroupSectionStatus status = groupSectionStatusRepository.findByGroupIdAndSectionId(groupId, section.getId()).orElse(null);
        JsonNode content = exportContentReader.read(groupId, section, response);
        Integer version = response != null ? response.getVersion() : 0;
        return new ExportSectionData(section, content, version, status);
    }
}
