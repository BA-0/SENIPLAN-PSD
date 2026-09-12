package com.senico.diagnostic.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.lowagie.text.*;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import java.awt.Color;
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
public class PdfExportService {

    private static final Color PRIMARY = new Color(0x2D, 0x7A, 0x45);
    private static final Color SLATE = new Color(0x64, 0x74, 0x8B);
    private static final Color BORDER = new Color(0xE2, 0xE8, 0xF0);
    /** Points de conduite du sommaire : assez marques pour guider l'oeil, assez clairs pour s'effacer. */
    private static final Color BORDER_DARK = new Color(0xA0, 0xAE, 0xC0);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    /** Date en toutes lettres de la page de garde, comme sur un PSD publie : « Dakar, le 4 septembre 2026 ». */
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale.FRENCH);
    private static final String LOGO_RESOURCE = "/branding/logo-senico.png";

    private byte[] logoBytes;

    private final SectionDefRepository sectionDefRepository;
    private final SectionResponseRepository sectionResponseRepository;
    private final GroupSectionStatusRepository groupSectionStatusRepository;
    private final WorkGroupRepository workGroupRepository;
    private final PsdNarrativeBlockRepository psdNarrativeBlockRepository;
    private final SectionExportRenderer sectionExportRenderer;
    private final ExportContentReader exportContentReader;
    private final PsdSynthesisBuilder psdSynthesisBuilder;
    private final PsdBriefBuilder psdBriefBuilder;
    private final PdfBlockEmitter pdfBlockEmitter;

    public byte[] exportGroupRecap(WorkGroup group) {
        try {
            Document document = new Document(PageSize.A4, 40, 40, 60, 50);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            writer.setPageEvent(new DocumentFooter("Plan Stratégique Sectoriel — SENICO SA"));
            document.open();

            addCoverPage(document, group);

            List<SectionDef> sections = sectionDefRepository.findAllByOrderByOrderAsc();
            for (SectionDef section : sections) {
                document.newPage();
                addSectionPage(document, section, group);
            }

            document.close();
            return baos.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Erreur de generation du PDF", e);
        }
    }

    public byte[] exportConsolidated() {
        try {
            Document document = new Document(PageSize.A4, 40, 40, 60, 50);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            writer.setPageEvent(new DocumentFooter("Document de consolidation — Plan Stratégique 2027-2031"));
            document.open();

            List<WorkGroup> groups = workGroupRepository.findAll();
            List<SectionDef> sections = sectionDefRepository.findAllByOrderByOrderAsc();

            Map<String, SectionResponse> responsesByKey = sectionResponseRepository.findAll().stream()
                    .collect(Collectors.toMap(r -> key(r.getGroup().getId(), r.getSection().getId()), r -> r));
            Map<String, GroupSectionStatus> statusesByKey = groupSectionStatusRepository.findAllWithGroupAndSection().stream()
                    .collect(Collectors.toMap(s -> key(s.getGroup().getId(), s.getSection().getId()), s -> s));

            // Le Document de consolidation fait foi : il ne reprend que les contributions
            // approuvees par le DG (cf. PsdApprovedContent). Celles qui attendent encore son
            // arbitrage apparaissent avec leur statut, mais sans leur contenu.
            responsesByKey = PsdApprovedContent.approvedOnly(responsesByKey, statusesByKey);

            addConsolidatedCoverPage(document);
            document.newPage();
            addSommairePage(document, sections, groups);

            for (SectionDef section : sections) {
                document.newPage();
                addConsolidatedSectionPage(document, section, groups, responsesByKey, statusesByKey);
            }

            document.close();
            return baos.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Erreur de generation du PDF consolide", e);
        }
    }

    public byte[] exportPsdFinalDocument() {
        try {
            Document document = new Document(PageSize.A4, 40, 40, 60, 50);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            writer.setPageEvent(new DocumentFooter("Plan Stratégique de SENICO 2027-2031"));
            document.open();

            List<WorkGroup> groups = workGroupRepository.findAll();
            List<Entry> entries = PsdDocumentStructure.entries();

            Map<String, SectionResponse> responsesByKey = sectionResponseRepository.findAll().stream()
                    .collect(Collectors.toMap(r -> key(r.getGroup().getId(), r.getSection().getId()), r -> r));
            Map<String, GroupSectionStatus> statusesByKey = groupSectionStatusRepository.findAllWithGroupAndSection().stream()
                    .collect(Collectors.toMap(s -> key(s.getGroup().getId(), s.getSection().getId()), s -> s));
            Map<NarrativeBlockKey, PsdNarrativeBlock> narrativeByKey = psdNarrativeBlockRepository.findAll().stream()
                    .collect(Collectors.toMap(PsdNarrativeBlock::getKey, b -> b));

            // La consolidation ne reprend que ce que le DG a approuve (cf. PsdApprovedContent).
            responsesByKey = PsdApprovedContent.approvedOnly(responsesByKey, statusesByKey);

            addPsdFinalCoverPage(document);
            document.newPage();
            addPsdFinalSommairePage(document, entries, groups);

            Map<String, SectionDef> sectionsByCode = sectionDefRepository.findAllByOrderByOrderAsc().stream()
                    .collect(Collectors.toMap(SectionDef::getCode, sd -> sd));
            for (Entry entry : entries) {
                document.newPage();
                if (entry instanceof MajorHeading heading) {
                    addPsdMajorHeadingPage(document, heading);
                } else if (entry instanceof NarrativeEntry narrative && narrative.key() == NarrativeBlockKey.AXES_CONSOLIDES) {
                    // Contenu structure (JSON) : il se lit en axes, rattachements et budget, pas en texte.
                    Paragraph header = new Paragraph(PdfFonts.phrase(narrative.label(), PdfFonts.font(16, Font.BOLD, PRIMARY)));
                    header.setSpacingAfter(4);
                    document.add(header);
                    LineSeparator separator = new LineSeparator();
                    separator.setLineColor(PRIMARY);
                    document.add(new Chunk(separator));
                    document.add(new Paragraph(" "));
                    pdfBlockEmitter.emit(document, writer, psdBriefBuilder.strategicAxes(groups, sectionsByCode,
                            responsesByKey, statusesByKey, narratives()));
                } else if (entry instanceof NarrativeEntry narrative) {
                    addPsdNarrativePage(document, narrative, narrativeByKey.get(narrative.key()));
                } else if (entry instanceof SynthesisEntry synthesis) {
                    addPsdSynthesisPage(document, synthesis, groups, responsesByKey, statusesByKey);
                } else if (entry instanceof SectionEntry sectionEntry) {
                    addPsdSectionEntryPage(document, sectionEntry, groups, responsesByKey, statusesByKey);
                }
            }

            document.close();
            return baos.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Erreur de generation du document final PSD", e);
        }
    }

    /**
     * Note de synthese : le Plan Strategique de Developpement sur le plan d'un PSD publie
     * (cf. {@link PsdBriefBuilder}).
     *
     * <p>Generee en deux passes. La premiere ne sert qu'a relever la page ou tombe chaque titre ;
     * la seconde produit le meme document, sommaire numerote. Les numeros ne changent pas d'une
     * passe a l'autre : le sommaire garde le meme nombre de lignes, seul le numero s'y ajoute.</p>
     */
    public byte[] exportSynthesisNote() {
        List<WorkGroup> groups = workGroupRepository.findAll();
        Map<String, SectionDef> sectionsByCode = sectionDefRepository.findAllByOrderByOrderAsc().stream()
                .collect(Collectors.toMap(SectionDef::getCode, sd -> sd));
        Map<String, SectionResponse> responsesByKey = sectionResponseRepository.findAll().stream()
                .collect(Collectors.toMap(r -> key(r.getGroup().getId(), r.getSection().getId()), r -> r));
        Map<String, GroupSectionStatus> statusesByKey = groupSectionStatusRepository.findAllWithGroupAndSection().stream()
                .collect(Collectors.toMap(s -> key(s.getGroup().getId(), s.getSection().getId()), s -> s));

        // Meme regle que les autres documents qui font foi : seules les sections approuvees
        // par le DG sont resumees.
        responsesByKey = PsdApprovedContent.approvedOnly(responsesByKey, statusesByKey);

        List<ExportBlock> blocks = psdBriefBuilder.build(groups, sectionsByCode, responsesByKey, statusesByKey, narratives());
        List<ExportBlock.Heading> toc = blocks.stream()
                .filter(block -> block instanceof ExportBlock.Heading heading && heading.level() <= 2)
                .map(ExportBlock.Heading.class::cast)
                .toList();

        Map<String, Integer> pages = renderSynthesisNote(blocks, toc, Map.of()).pages();
        return renderSynthesisNote(blocks, toc, pages).pdf();
    }

    private record RenderedNote(byte[] pdf, Map<String, Integer> pages) {
    }

    private RenderedNote renderSynthesisNote(List<ExportBlock> blocks, List<ExportBlock.Heading> toc,
                                             Map<String, Integer> pages) {
        try {
            Document document = new Document(PageSize.A4, 48, 48, 56, 64);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            // Un graphique qui ne tient plus en bas de page passe a la suivante sans que le texte
            // qui le suit ne remonte avant lui.
            writer.setStrictImageSequence(true);
            DocumentFooter footer = new DocumentFooter("Plan Stratégique 2027-2031 — Note de synthèse");
            writer.setPageEvent(footer);
            document.open();

            addSynthesisNoteCoverPage(document);
            document.newPage();
            addSynthesisNoteSommairePage(document, toc, pages);
            pdfBlockEmitter.emit(document, writer, blocks);

            document.close();
            return new RenderedNote(baos.toByteArray(), footer.tagPages());
        } catch (DocumentException e) {
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
     * Page de garde sur le modele d'un PSD publie : logo, filet, titre du plan en grand, badge
     * de la nature du document, puis le lieu et la date en toutes lettres.
     */
    private void addSynthesisNoteCoverPage(Document document) throws DocumentException {
        Paragraph topSpacer = new Paragraph(" ");
        topSpacer.setSpacingAfter(70);
        document.add(topSpacer);

        document.add(senicoLogo());

        Paragraph logoSpacer = new Paragraph(" ");
        logoSpacer.setSpacingAfter(28);
        document.add(logoSpacer);

        LineSeparator rule = new LineSeparator();
        rule.setLineColor(PRIMARY);
        rule.setLineWidth(1.5f);
        document.add(new Chunk(rule));

        // « Plan Strategique », sans « de Developpement » : SENICO n'est pas encore a ce niveau de
        // planification, et c'est l'intitule que porte deja la page de garde Word.
        Paragraph title = new Paragraph("PLAN STRATÉGIQUE", PdfFonts.font(26, Font.BOLD, PRIMARY));
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingBefore(34);
        title.setLeading(32);
        document.add(title);

        Paragraph period = new Paragraph("2027 - 2031", PdfFonts.font(26, Font.BOLD, PRIMARY));
        period.setAlignment(Element.ALIGN_CENTER);
        period.setSpacingBefore(8);
        document.add(period);

        Paragraph company = new Paragraph("SENICO SA", PdfFonts.font(14, Font.NORMAL, SLATE));
        company.setAlignment(Element.ALIGN_CENTER);
        company.setSpacingBefore(14);
        document.add(company);

        PdfPTable badge = new PdfPTable(1);
        badge.setWidthPercentage(62);
        badge.setSpacingBefore(46);
        badge.setHorizontalAlignment(Element.ALIGN_CENTER);
        PdfPCell badgeCell = new PdfPCell(new Paragraph("NOTE DE SYNTHÈSE",
                PdfFonts.font(14, Font.BOLD, Color.WHITE)));
        badgeCell.setBackgroundColor(PRIMARY);
        badgeCell.setPadding(14);
        badgeCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        badgeCell.setBorder(Rectangle.NO_BORDER);
        badge.addCell(badgeCell);
        document.add(badge);

        Paragraph description = new Paragraph(
                "Le résumé consolidé de l'ensemble des directions — diagnostic, enjeux, cadre "
                        + "stratégique, budget et pilotage — pour le Conseil d'Administration et le comité "
                        + "de pilotage.",
                PdfFonts.font(11, Font.ITALIC, SLATE));
        description.setAlignment(Element.ALIGN_CENTER);
        description.setSpacingBefore(24);
        description.setIndentationLeft(50);
        description.setIndentationRight(50);
        document.add(description);

        Paragraph place = new Paragraph(
                "Dakar, le " + java.time.LocalDate.now().format(LONG_DATE),
                PdfFonts.font(12, Font.BOLD, PRIMARY));
        place.setAlignment(Element.ALIGN_CENTER);
        place.setSpacingBefore(60);
        document.add(place);

        Paragraph meta = new Paragraph(
                "Export généré le " + java.time.LocalDateTime.now().format(DATE_FORMAT)
                        + " — document interne, confidentiel",
                PdfFonts.font(9, Font.ITALIC, SLATE));
        meta.setAlignment(Element.ALIGN_CENTER);
        meta.setSpacingBefore(8);
        document.add(meta);
    }

    /**
     * Sommaire de la note, comme dans un PSD publie : les parties en gras, leurs sous-parties en
     * retrait, et pour chacune la page, reliee au titre par des points de conduite. Les numeros
     * viennent de la premiere passe de generation ; absents, la ligne reste sans numero.
     */
    private void addSynthesisNoteSommairePage(Document document, List<ExportBlock.Heading> toc,
                                              Map<String, Integer> pages) throws DocumentException {
        Paragraph header = new Paragraph(PdfFonts.phrase("SOMMAIRE", PdfFonts.font(18, Font.BOLD, PRIMARY)));
        header.setSpacingAfter(4);
        document.add(header);

        LineSeparator separator = new LineSeparator();
        separator.setLineColor(PRIMARY);
        separator.setLineWidth(1.2f);
        document.add(new Chunk(separator));

        Paragraph spacer = new Paragraph(" ", PdfFonts.font(8, Font.NORMAL, SLATE));
        spacer.setSpacingAfter(4);
        document.add(spacer);

        Color ink = new Color(0x1F, 0x29, 0x37);
        for (ExportBlock.Heading heading : toc) {
            boolean part = heading.level() == 1;
            Font font = part ? PdfFonts.font(10.5f, Font.BOLD, ink) : PdfFonts.font(9.5f, Font.NORMAL, ink);
            Paragraph line = new Paragraph();
            line.add(PdfFonts.phrase(heading.text(), font));
            com.lowagie.text.pdf.draw.DottedLineSeparator leader = new com.lowagie.text.pdf.draw.DottedLineSeparator();
            leader.setGap(2.5f);
            leader.setLineWidth(0.8f);
            leader.setLineColor(BORDER_DARK);
            leader.setOffset(-2);
            line.add(new Chunk(leader));
            Integer page = pages.get(PdfBlockEmitter.TOC_TAG + heading.text());
            line.add(new Chunk(page == null ? "" : " " + (page - 1), font));
            line.setIndentationLeft(part ? 0 : 18);
            // Serre juste assez pour que le sommaire tienne sur une page : sa derniere ligne
            // debordait seule sur une page blanche.
            line.setSpacingBefore(part ? 3f : 0.5f);
            line.setLeading(part ? 13f : 11f);
            document.add(line);
        }
    }

    private void addPsdFinalCoverPage(Document document) throws DocumentException {
        Font titleFont = PdfFonts.font(22, Font.BOLD, PRIMARY);
        Font subtitleFont = PdfFonts.font(14, Font.NORMAL, SLATE);
        Font metaFont = PdfFonts.font(11, Font.NORMAL, SLATE);

        Paragraph topSpacer = new Paragraph(" ");
        topSpacer.setSpacingAfter(70);
        document.add(topSpacer);

        document.add(senicoLogo());

        Paragraph spacer = new Paragraph(" ");
        spacer.setSpacingAfter(60);
        document.add(spacer);

        Paragraph title = new Paragraph("SENICO SA — Plan Stratégique", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);

        Paragraph subtitle = new Paragraph("Horizon 2027-2031", subtitleFont);
        subtitle.setAlignment(Element.ALIGN_CENTER);
        subtitle.setSpacingBefore(10);
        document.add(subtitle);

        Paragraph docTitle = new Paragraph("PLAN STRATÉGIQUE DE SENICO", PdfFonts.font(16, Font.BOLD, Color.DARK_GRAY));
        docTitle.setAlignment(Element.ALIGN_CENTER);
        docTitle.setSpacingBefore(40);
        document.add(docTitle);

        Paragraph meta = new Paragraph(
                "Export généré le " + java.time.LocalDateTime.now().format(DATE_FORMAT),
                metaFont);
        meta.setAlignment(Element.ALIGN_CENTER);
        meta.setSpacingBefore(10);
        document.add(meta);
    }

    private void addPsdFinalSommairePage(Document document, List<Entry> entries, List<WorkGroup> groups) throws DocumentException {
        Font headerFont = PdfFonts.font(16, Font.BOLD, PRIMARY);
        Paragraph header = new Paragraph("Sommaire", headerFont);
        header.setSpacingAfter(4);
        document.add(header);

        LineSeparator separator = new LineSeparator();
        separator.setLineColor(PRIMARY);
        document.add(new Chunk(separator));
        document.add(new Paragraph(" "));

        Font majorFont = PdfFonts.font(11, Font.BOLD, Color.DARK_GRAY);
        Font itemFont = PdfFonts.font(10, Font.NORMAL, Color.DARK_GRAY);
        for (Entry entry : entries) {
            // Switch exhaustif sur l'interface scellee Entry plutot qu'une cascade de
            // instanceof terminee par un cast : c'est justement ce cast qui faisait
            // planter le sommaire depuis l'ajout de SynthesisEntry (« Synthese du PSD »),
            // que le corps du document rendait deja. Ajouter un type d'entree ne
            // compilera desormais plus tant que le sommaire ne le traite pas.
            Paragraph p = switch (entry) {
                case MajorHeading heading -> {
                    Paragraph major = new Paragraph(heading.title(), majorFont);
                    major.setSpacingBefore(8);
                    yield major;
                }
                case NarrativeEntry narrative -> sommaireItem(narrative.label(), itemFont);
                case SynthesisEntry synthesis -> sommaireItem(synthesis.label(), itemFont);
                case SectionEntry sectionEntry -> sommaireItem(sectionEntry.label(), itemFont);
            };
            document.add(p);
        }

        Paragraph legendTitle = new Paragraph("Légende des directions", PdfFonts.font(13, Font.BOLD, PRIMARY));
        legendTitle.setSpacingBefore(20);
        legendTitle.setSpacingAfter(6);
        document.add(legendTitle);

        PdfPTable legend = new PdfPTable(2);
        legend.setWidthPercentage(100);
        legend.setWidths(new float[]{30, 70});
        addTableHeaderRow(legend, "Couleur", "Direction");
        for (WorkGroup group : groups) {
            PdfPCell colorCell = new PdfPCell(new Paragraph(" "));
            colorCell.setBackgroundColor(hexToColor(group.getColor()));
            colorCell.setFixedHeight(20);
            colorCell.setBorderColor(BORDER);
            legend.addCell(colorCell);
            legend.addCell(bodyCell(group.getName(), Element.ALIGN_LEFT, Color.WHITE));
        }
        document.add(legend);
    }

    /** Ligne de sommaire : meme forme pour toutes les entrees hors intertitres. */
    private Paragraph sommaireItem(String label, Font itemFont) {
        Paragraph item = new Paragraph("• " + label, itemFont);
        item.setIndentationLeft(15);
        return item;
    }

    private void addPsdSynthesisPage(Document document, SynthesisEntry synthesis, List<WorkGroup> groups,
                                      Map<String, SectionResponse> responsesByKey,
                                      Map<String, GroupSectionStatus> statusesByKey) throws DocumentException {
        Paragraph header = new Paragraph(synthesis.label(), PdfFonts.font(16, Font.BOLD, PRIMARY));
        header.setSpacingAfter(4);
        document.add(header);

        LineSeparator separator = new LineSeparator();
        separator.setLineColor(PRIMARY);
        document.add(new Chunk(separator));
        document.add(new Paragraph(" "));

        pdfBlockEmitter.emit(document, buildSynthesisBlocks(groups, responsesByKey, statusesByKey));
    }

    private List<ExportBlock> buildSynthesisBlocks(List<WorkGroup> groups,
                                                    Map<String, SectionResponse> responsesByKey,
                                                    Map<String, GroupSectionStatus> statusesByKey) {
        Map<String, SectionDef> sectionsByCode = sectionDefRepository.findAllByOrderByOrderAsc().stream()
                .collect(Collectors.toMap(SectionDef::getCode, sd -> sd));
        long approved = statusesByKey.values().stream().filter(PsdApprovedContent::isApproved).count();
        return psdSynthesisBuilder.build(groups, sectionsByCode, responsesByKey,
                (int) approved, statusesByKey.size());
    }

    private void addPsdMajorHeadingPage(Document document, MajorHeading heading) throws DocumentException {
        Paragraph spacer = new Paragraph(" ");
        spacer.setSpacingAfter(150);
        document.add(spacer);

        Paragraph title = new Paragraph(heading.title(), PdfFonts.font(24, Font.BOLD, PRIMARY));
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);
    }

    private void addPsdNarrativePage(Document document, NarrativeEntry narrative, PsdNarrativeBlock block) throws DocumentException {
        Font headerFont = PdfFonts.font(16, Font.BOLD, PRIMARY);
        Paragraph header = new Paragraph(narrative.label(), headerFont);
        header.setSpacingAfter(4);
        document.add(header);

        LineSeparator separator = new LineSeparator();
        separator.setLineColor(PRIMARY);
        document.add(new Chunk(separator));
        document.add(new Paragraph(" "));

        String content = block != null && block.getContent() != null ? block.getContent() : "";
        if (content.isBlank()) {
            document.add(new Paragraph("(contenu à renseigner)", PdfFonts.font(11, Font.ITALIC, SLATE)));
            return;
        }
        // Puces et intertitres rediges dans la zone de texte (cf. PsdNarrativeText).
        pdfBlockEmitter.emit(document, PsdNarrativeText.blocks(content));
    }

    private static final String[] PESTEL_AXES = {
            "POLITIQUE", "ECONOMIQUE", "SOCIAL_CULTUREL", "TECHNOLOGIQUE", "ENVIRONNEMENTAL", "LEGAL"
    };

    private void addPsdSectionEntryPage(Document document, SectionEntry sectionEntry, List<WorkGroup> groups,
                                         Map<String, SectionResponse> responsesByKey,
                                         Map<String, GroupSectionStatus> statusesByKey) throws DocumentException {
        Font headerFont = PdfFonts.font(16, Font.BOLD, PRIMARY);
        Paragraph header = new Paragraph(sectionEntry.label(), headerFont);
        header.setSpacingAfter(4);
        document.add(header);

        LineSeparator separator = new LineSeparator();
        separator.setLineColor(PRIMARY);
        document.add(new Chunk(separator));
        document.add(new Paragraph(" "));

        for (String code : sectionEntry.sectionCodes()) {
            SectionDef section = sectionDefRepository.findByCode(code)
                    .orElseThrow(() -> new IllegalStateException("Section introuvable : " + code));

            if (sectionEntry.sectionCodes().size() > 1) {
                Paragraph subHeader = new Paragraph(section.getCode() + " — " + section.getTitle(),
                        PdfFonts.font(13, Font.BOLD, SLATE));
                subHeader.setSpacingBefore(10);
                subHeader.setSpacingAfter(6);
                document.add(subHeader);
            }

            switch (code) {
                case "S01" -> addPsdMergedStakeholders(document, section, groups, responsesByKey);
                case "S04" -> addPsdMergedSwot(document, section, groups, responsesByKey);
                case "S03" -> addPsdMergedPestel(document, section, groups, responsesByKey);
                default -> addPsdPerGroupSection(document, section, groups, responsesByKey, statusesByKey);
            }
        }
    }

    /**
     * Rendu fusionne : un seul tableau par rubrique, toutes directions confondues, avec une
     * colonne "Direction" (cf. {@link PsdSectionMerger}). Remplace la repetition d'un bloc
     * complet par direction, qui obligeait a parcourir cinq fois la meme rubrique.
     */
    private void addPsdPerGroupSection(Document document, SectionDef section, List<WorkGroup> groups,
                                        Map<String, SectionResponse> responsesByKey,
                                        Map<String, GroupSectionStatus> statusesByKey) throws DocumentException {
        pdfBlockEmitter.emit(document, mergedSectionBlocks(section, groups, responsesByKey, statusesByKey));
    }

    private List<ExportBlock> mergedSectionBlocks(SectionDef section, List<WorkGroup> groups,
                                                   Map<String, SectionResponse> responsesByKey,
                                                   Map<String, GroupSectionStatus> statusesByKey) {
        List<PsdSectionMerger.GroupBlocks> perGroup = new ArrayList<>();
        for (WorkGroup group : groups) {
            ExportSectionData data = loadPsdExportData(group, section, responsesByKey, statusesByKey);
            boolean included = !data.withheldPendingApproval();
            perGroup.add(new PsdSectionMerger.GroupBlocks(
                    group,
                    included ? sectionExportRenderer.render(data) : List.of(),
                    included,
                    included ? null : exclusionReason(data)));
        }
        return PsdSectionMerger.merge(perGroup);
    }

    private String exclusionReason(ExportSectionData data) {
        SectionStatus status = data.status() != null ? data.status().getStatus() : SectionStatus.NOT_STARTED;
        if (status == SectionStatus.NOT_STARTED) {
            return "non renseignée";
        }
        if (status == SectionStatus.VALIDATED) {
            return "en attente d'approbation de la Direction Générale";
        }
        return "non validée — " + SectionExportRenderer.statusLabel(status.name()).toLowerCase();
    }

    /**
     * Rendu fusionne : les diagnostics des directions (parties prenantes, SWOT, PESTEL) se
     * recoupent souvent (ex. plusieurs directions citent "electricite" comme menace) ; on
     * n'affiche chaque element qu'une seule fois, avec un carre de couleur par direction
     * contributrice, au lieu de repeter le tableau complet de chaque direction.
     */
    private void addPsdMergedStakeholders(Document document, SectionDef section, List<WorkGroup> groups,
                                           Map<String, SectionResponse> responsesByKey) throws DocumentException {
        String[] headers = {"Catégorie", "Portée", "Rôles", "Attentes", "Stratégie d'adaptation", "Importance", "Influence", "Actions", "Directions"};
        float[] widths = {9, 7, 13, 13, 13, 7, 7, 14, 17};

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
            document.add(new Paragraph("Aucune donnée saisie pour cette section.", PdfFonts.font(10, Font.ITALIC, SLATE)));
            return;
        }

        PdfPTable table = new PdfPTable(headers.length);
        table.setWidthPercentage(100);
        table.setWidths(widths);
        table.setSpacingBefore(4);
        table.setSpacingAfter(4);
        addTableHeaderRow(table, headers);

        for (Map.Entry<String, String[]> entry : rowValuesByKey.entrySet()) {
            for (String value : entry.getValue()) {
                table.addCell(bodyCell(value, Element.ALIGN_LEFT, Color.WHITE));
            }
            Paragraph dots = new Paragraph();
            appendContributorDots(dots, contributorsByKey.get(entry.getKey()));
            PdfPCell dotsCell = new PdfPCell(dots);
            dotsCell.setBorderColor(BORDER);
            dotsCell.setPadding(5);
            table.addCell(dotsCell);
        }
        document.add(table);
        addMergeCaption(document);
    }

    private void addPsdMergedSwot(Document document, SectionDef section, List<WorkGroup> groups,
                                   Map<String, SectionResponse> responsesByKey) throws DocumentException {
        String[] fields = {"strengths", "weaknesses", "opportunities", "threats"};
        String[] labels = {"Forces", "Faiblesses", "Opportunités", "Menaces"};
        Color[] tints = {
                new Color(0xDC, 0xFC, 0xE7), new Color(0xFF, 0xED, 0xD5),
                new Color(0xDB, 0xEA, 0xFE), new Color(0xFE, 0xE2, 0xE2)
        };

        Map<WorkGroup, JsonNode> contentByGroup = new LinkedHashMap<>();
        for (WorkGroup group : groups) {
            contentByGroup.put(group, contentFor(group, section, responsesByKey));
        }

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingBefore(4);
        table.setSpacingAfter(4);

        for (int i = 0; i < fields.length; i++) {
            List<Map.Entry<WorkGroup, String>> entries = new ArrayList<>();
            for (WorkGroup group : groups) {
                for (String item : JsonUtil.strList(contentByGroup.get(group), fields[i])) {
                    entries.add(Map.entry(group, item));
                }
            }
            List<PsdCrossGroupMerge.MergedItem> merged = PsdCrossGroupMerge.merge(entries, false);

            Paragraph cellContent = new Paragraph();
            cellContent.add(new Chunk(labels[i] + "\n", PdfFonts.font(10, Font.BOLD, Color.DARK_GRAY)));
            if (merged.isEmpty()) {
                cellContent.add(new Chunk("Aucun élément.", PdfFonts.font(9, Font.NORMAL, Color.DARK_GRAY)));
            } else {
                for (PsdCrossGroupMerge.MergedItem item : merged) {
                    cellContent.add(new Chunk("•  " + item.text(), PdfFonts.font(9, Font.NORMAL, Color.DARK_GRAY)));
                    appendContributorDots(cellContent, item.contributors());
                    cellContent.add(Chunk.NEWLINE);
                }
            }
            PdfPCell cell = new PdfPCell(cellContent);
            cell.setBackgroundColor(tints[i]);
            cell.setBorderColor(BORDER);
            cell.setPadding(8);
            table.addCell(cell);
        }
        document.add(table);
        addMergeCaption(document);
    }

    private void addPsdMergedPestel(Document document, SectionDef section, List<WorkGroup> groups,
                                     Map<String, SectionResponse> responsesByKey) throws DocumentException {
        Map<WorkGroup, JsonNode> contentByGroup = new LinkedHashMap<>();
        for (WorkGroup group : groups) {
            contentByGroup.put(group, contentFor(group, section, responsesByKey));
        }

        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{15, 28, 28, 29});
        table.setSpacingBefore(4);
        table.setSpacingAfter(4);
        addTableHeaderRow(table, "Axe", "Menaces", "Opportunités", "Actions");

        String[] columns = {"threats", "opportunities", "actions"};
        for (String axisCode : PESTEL_AXES) {
            PdfPCell axisCell = bodyCell(SectionLabels.pestel(axisCode), Element.ALIGN_LEFT, Color.WHITE);
            axisCell.setVerticalAlignment(Element.ALIGN_TOP);
            table.addCell(axisCell);

            for (String column : columns) {
                List<Map.Entry<WorkGroup, String>> entries = new ArrayList<>();
                for (WorkGroup group : groups) {
                    for (JsonNode row : JsonUtil.arr(contentByGroup.get(group), "rows")) {
                        if (axisCode.equals(JsonUtil.text(row, "axis"))) {
                            String text = JsonUtil.text(row, column);
                            if (!text.isBlank()) {
                                entries.add(Map.entry(group, text));
                            }
                        }
                    }
                }
                List<PsdCrossGroupMerge.MergedItem> merged = PsdCrossGroupMerge.merge(entries, true);

                Paragraph cellContent = new Paragraph();
                if (merged.isEmpty()) {
                    cellContent.add(new Chunk("—", PdfFonts.font(9, Font.NORMAL, SLATE)));
                } else {
                    for (PsdCrossGroupMerge.MergedItem item : merged) {
                        cellContent.add(new Chunk("•  " + item.text(), PdfFonts.font(9, Font.NORMAL, Color.DARK_GRAY)));
                        appendContributorDots(cellContent, item.contributors());
                        cellContent.add(Chunk.NEWLINE);
                    }
                }
                PdfPCell cell = new PdfPCell(cellContent);
                cell.setBorderColor(BORDER);
                cell.setPadding(6);
                table.addCell(cell);
            }
        }
        document.add(table);
        addMergeCaption(document);
    }

    /** Pastilles dessinees plutot que glyphe « ■ », absent de la police des documents (cf. PdfFonts). */
    private void appendContributorDots(Paragraph content, List<WorkGroup> contributors) {
        for (WorkGroup group : contributors) {
            content.add(new Chunk(" ", PdfFonts.font(8, Font.NORMAL, SLATE)));
            content.add(PdfBlockEmitter.swatch(group.getColor(), 9));
        }
    }

    private void addMergeCaption(Document document) throws DocumentException {
        Paragraph caption = new Paragraph();
        caption.add(PdfBlockEmitter.swatch("#64748B", 8));
        caption.add(new Chunk(" = direction(s) ayant mentionné cet élément (voir légende des directions au sommaire).",
                PdfFonts.font(8, Font.ITALIC, SLATE)));
        caption.setSpacingAfter(10);
        document.add(caption);
    }

    private JsonNode contentFor(WorkGroup group, SectionDef section, Map<String, SectionResponse> responsesByKey) {
        SectionResponse response = responsesByKey.get(key(group.getId(), section.getId()));
        return exportContentReader.read(group.getId(), section, response);
    }

    private String key(Long groupId, Integer sectionId) {
        return groupId + ":" + sectionId;
    }

    private Image senicoLogo() throws DocumentException {
        if (logoBytes == null) {
            try (InputStream in = getClass().getResourceAsStream(LOGO_RESOURCE)) {
                if (in == null) {
                    throw new IllegalStateException("Logo SENICO introuvable sur le classpath: " + LOGO_RESOURCE);
                }
                logoBytes = in.readAllBytes();
            } catch (IOException e) {
                throw new IllegalStateException("Impossible de charger le logo SENICO", e);
            }
        }
        try {
            Image logo = Image.getInstance(logoBytes);
            logo.scaleToFit(190, 70);
            logo.setAlignment(Element.ALIGN_CENTER);
            return logo;
        } catch (IOException e) {
            throw new IllegalStateException("Impossible de charger le logo SENICO", e);
        }
    }

    private void addConsolidatedCoverPage(Document document) throws DocumentException {
        Paragraph topSpacer = new Paragraph(" ");
        topSpacer.setSpacingAfter(70);
        document.add(topSpacer);

        document.add(senicoLogo());

        Paragraph logoSpacer = new Paragraph(" ");
        logoSpacer.setSpacingAfter(28);
        document.add(logoSpacer);

        LineSeparator rule = new LineSeparator();
        rule.setLineColor(PRIMARY);
        rule.setLineWidth(1.5f);
        document.add(new Chunk(rule));

        Font titleFont = PdfFonts.font(22, Font.BOLD, PRIMARY);
        Paragraph title = new Paragraph("SENICO SA — Plan Stratégique", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingBefore(28);
        document.add(title);

        Font subtitleFont = PdfFonts.font(14, Font.NORMAL, SLATE);
        Paragraph subtitle = new Paragraph("Horizon 2027-2031", subtitleFont);
        subtitle.setAlignment(Element.ALIGN_CENTER);
        subtitle.setSpacingBefore(10);
        document.add(subtitle);

        PdfPTable badge = new PdfPTable(1);
        badge.setWidthPercentage(62);
        badge.setSpacingBefore(46);
        badge.setHorizontalAlignment(Element.ALIGN_CENTER);
        PdfPCell badgeCell = new PdfPCell(new Paragraph("DOCUMENT DE CONSOLIDATION",
                PdfFonts.font(14, Font.BOLD, Color.WHITE)));
        badgeCell.setBackgroundColor(PRIMARY);
        badgeCell.setPadding(14);
        badgeCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        badgeCell.setBorder(Rectangle.NO_BORDER);
        badge.addCell(badgeCell);
        document.add(badge);

        Paragraph description = new Paragraph(
                "Toutes les réponses de toutes les directions, réunies dans un seul document — "
                        + "code couleur par direction — pour permettre de trancher directement.",
                PdfFonts.font(11, Font.ITALIC, SLATE));
        description.setAlignment(Element.ALIGN_CENTER);
        description.setSpacingBefore(24);
        description.setIndentationLeft(50);
        description.setIndentationRight(50);
        document.add(description);

        Paragraph meta = new Paragraph(
                "Export généré le " + java.time.LocalDateTime.now().format(DATE_FORMAT),
                PdfFonts.font(11, Font.NORMAL, SLATE));
        meta.setAlignment(Element.ALIGN_CENTER);
        meta.setSpacingBefore(60);
        document.add(meta);

        Paragraph confidential = new Paragraph("Document interne — confidentiel",
                PdfFonts.font(9, Font.ITALIC, SLATE));
        confidential.setAlignment(Element.ALIGN_CENTER);
        confidential.setSpacingBefore(6);
        document.add(confidential);
    }

    private void addSommairePage(Document document, List<SectionDef> sections, List<WorkGroup> groups) throws DocumentException {
        Font headerFont = PdfFonts.font(16, Font.BOLD, PRIMARY);
        Paragraph header = new Paragraph("Sommaire", headerFont);
        header.setSpacingAfter(4);
        document.add(header);

        LineSeparator separator = new LineSeparator();
        separator.setLineColor(PRIMARY);
        document.add(new Chunk(separator));
        document.add(new Paragraph(" "));

        PdfPTable toc = new PdfPTable(2);
        toc.setWidthPercentage(100);
        toc.setWidths(new float[]{15, 85});
        toc.setSpacingAfter(20);
        addTableHeaderRow(toc, "Code", "Section");
        for (SectionDef section : sections) {
            toc.addCell(bodyCell(section.getCode(), Element.ALIGN_LEFT, Color.WHITE));
            toc.addCell(bodyCell(section.getTitle(), Element.ALIGN_LEFT, Color.WHITE));
        }
        document.add(toc);

        Paragraph legendTitle = new Paragraph("Légende des directions", PdfFonts.font(13, Font.BOLD, PRIMARY));
        legendTitle.setSpacingBefore(10);
        legendTitle.setSpacingAfter(6);
        document.add(legendTitle);

        PdfPTable legend = new PdfPTable(2);
        legend.setWidthPercentage(100);
        legend.setWidths(new float[]{30, 70});
        addTableHeaderRow(legend, "Couleur", "Direction");
        for (WorkGroup group : groups) {
            PdfPCell colorCell = new PdfPCell(new Paragraph(" "));
            colorCell.setBackgroundColor(hexToColor(group.getColor()));
            colorCell.setFixedHeight(20);
            colorCell.setBorderColor(BORDER);
            legend.addCell(colorCell);
            legend.addCell(bodyCell(group.getName(), Element.ALIGN_LEFT, Color.WHITE));
        }
        document.add(legend);
    }

    private void addConsolidatedSectionPage(Document document, SectionDef section, List<WorkGroup> groups,
                                             Map<String, SectionResponse> responsesByKey,
                                             Map<String, GroupSectionStatus> statusesByKey) throws DocumentException {
        Font headerFont = PdfFonts.font(16, Font.BOLD, PRIMARY);

        Paragraph header = new Paragraph(section.getCode() + " — " + section.getTitle(), headerFont);
        header.setSpacingAfter(4);
        document.add(header);

        LineSeparator separator = new LineSeparator();
        separator.setLineColor(PRIMARY);
        document.add(new Chunk(separator));
        document.add(new Paragraph(" "));

        for (WorkGroup group : groups) {
            document.add(groupBanner(group));

            ExportSectionData data = loadPsdExportData(group, section, responsesByKey, statusesByKey);
            List<ExportBlock> blocks = sectionExportRenderer.render(data);
            pdfBlockEmitter.emit(document, blocks);

            Paragraph spacing = new Paragraph(" ");
            spacing.setSpacingAfter(10);
            document.add(spacing);
        }
    }

    private PdfPTable groupBanner(WorkGroup group) throws DocumentException {
        PdfPTable banner = new PdfPTable(1);
        banner.setWidthPercentage(100);
        banner.setSpacingBefore(6);
        banner.setSpacingAfter(6);
        PdfPCell cell = new PdfPCell(new Paragraph(group.getName(), PdfFonts.font(11, Font.BOLD, Color.WHITE)));
        cell.setBackgroundColor(hexToColor(group.getColor()));
        cell.setPadding(6);
        cell.setBorder(Rectangle.NO_BORDER);
        banner.addCell(cell);
        return banner;
    }

    private void addTableHeaderRow(PdfPTable table, String... headers) {
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Paragraph(h, PdfFonts.font(9, Font.BOLD, Color.WHITE)));
            cell.setBackgroundColor(PRIMARY);
            cell.setPadding(5);
            cell.setBorderColor(BORDER);
            table.addCell(cell);
        }
    }

    private PdfPCell bodyCell(String text, int align, Color bg) {
        PdfPCell cell = new PdfPCell(new Paragraph(text == null ? "" : text, PdfFonts.font(9, Font.NORMAL, Color.DARK_GRAY)));
        cell.setHorizontalAlignment(align);
        cell.setPadding(5);
        cell.setBorderColor(BORDER);
        cell.setBackgroundColor(bg);
        return cell;
    }

    private Color hexToColor(String hex) {
        if (hex == null || !hex.matches("#[0-9A-Fa-f]{6}")) {
            return SLATE;
        }
        return new Color(
                Integer.parseInt(hex.substring(1, 3), 16),
                Integer.parseInt(hex.substring(3, 5), 16),
                Integer.parseInt(hex.substring(5, 7), 16)
        );
    }

    private ExportSectionData loadExportData(WorkGroup group, SectionDef section,
                                              Map<String, SectionResponse> responsesByKey,
                                              Map<String, GroupSectionStatus> statusesByKey) {
        String k = key(group.getId(), section.getId());
        SectionResponse response = responsesByKey.get(k);
        GroupSectionStatus status = statusesByKey.get(k);
        JsonNode content = exportContentReader.read(group.getId(), section, response);
        Integer version = response != null ? response.getVersion() : 0;
        return new ExportSectionData(section, content, version, status);
    }

    /**
     * Variante pour les documents qui font foi, dont les reponses ont deja ete filtrees sur les
     * seules sections approuvees par le DG : une reponse absente alors que le statut n'est pas
     * NOT_STARTED signale un contenu retenu faute d'approbation, pas une section vide.
     */
    private ExportSectionData loadPsdExportData(WorkGroup group, SectionDef section,
                                                 Map<String, SectionResponse> responsesByKey,
                                                 Map<String, GroupSectionStatus> statusesByKey) {
        ExportSectionData data = loadExportData(group, section, responsesByKey, statusesByKey);
        boolean withheld = !PsdApprovedContent.isApproved(data.status());
        return new ExportSectionData(data.section(), data.content(), data.version(), data.status(), withheld);
    }

    private void addCoverPage(Document document, WorkGroup group) throws DocumentException {
        Font titleFont = PdfFonts.font(22, Font.BOLD, PRIMARY);
        Font subtitleFont = PdfFonts.font(14, Font.NORMAL, SLATE);
        Font metaFont = PdfFonts.font(11, Font.NORMAL, SLATE);

        Paragraph topSpacer = new Paragraph(" ");
        topSpacer.setSpacingAfter(70);
        document.add(topSpacer);

        document.add(senicoLogo());

        Paragraph spacer = new Paragraph(" ");
        spacer.setSpacingAfter(60);
        document.add(spacer);

        Paragraph title = new Paragraph("SENICO SA — Plan Stratégique", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);

        Paragraph subtitle = new Paragraph("Horizon 2027-2031", subtitleFont);
        subtitle.setAlignment(Element.ALIGN_CENTER);
        subtitle.setSpacingBefore(10);
        document.add(subtitle);

        Paragraph docTitle = new Paragraph("PLAN STRATÉGIQUE SECTORIEL", PdfFonts.font(16, Font.BOLD, Color.DARK_GRAY));
        docTitle.setAlignment(Element.ALIGN_CENTER);
        docTitle.setSpacingBefore(40);
        document.add(docTitle);

        Paragraph groupName = new Paragraph(group.getName(), PdfFonts.font(14, Font.NORMAL, Color.DARK_GRAY));
        groupName.setAlignment(Element.ALIGN_CENTER);
        groupName.setSpacingBefore(8);
        document.add(groupName);

        Paragraph meta = new Paragraph(
                "Export généré le " + java.time.LocalDateTime.now().format(DATE_FORMAT),
                metaFont);
        meta.setAlignment(Element.ALIGN_CENTER);
        meta.setSpacingBefore(10);
        document.add(meta);
    }

    private void addSectionPage(Document document, SectionDef section, WorkGroup group) throws DocumentException {
        Font headerFont = PdfFonts.font(16, Font.BOLD, PRIMARY);

        Paragraph header = new Paragraph(section.getCode() + " — " + section.getTitle(), headerFont);
        header.setSpacingAfter(4);
        document.add(header);

        LineSeparator separator = new LineSeparator();
        separator.setLineColor(PRIMARY);
        document.add(new Chunk(separator));
        document.add(new Paragraph(" "));

        List<ExportBlock> blocks = sectionExportRenderer.render(loadExportData(group.getId(), section));
        pdfBlockEmitter.emit(document, blocks);
    }

    /**
     * Pied de page commun aux documents exportes : intitule du document a gauche, pagination a
     * droite. La page de garde n'en porte pas et n'est pas comptee, comme dans un PSD publie ou
     * la numerotation commence au sommaire.
     *
     * <p>Le total est reserve dans un gabarit puis rempli a la fermeture, quand le nombre de
     * pages est enfin connu : c'est le seul moyen d'imprimer « Page 3 / 12 » en une seule passe
     * de generation.</p>
     */
    private static final class DocumentFooter extends PdfPageEventHelper {

        /** Largeur reservee au nombre total de pages, rempli a la fermeture du document. */
        private static final float TOTAL_WIDTH = 22;

        private final String label;
        private final Font font = PdfFonts.font(8, Font.NORMAL, SLATE);
        private final Map<String, Integer> tagPages = new LinkedHashMap<>();
        private PdfTemplate totalPages;
        private int lastNumberedPage;

        private DocumentFooter(String label) {
            this.label = label;
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            totalPages = writer.getDirectContent().createTemplate(TOTAL_WIDTH, 12);
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            int page = writer.getPageNumber();
            if (page <= 1) {
                return;
            }
            lastNumberedPage = page - 1;
            PdfContentByte canvas = writer.getDirectContent();
            float baseline = document.bottom() - 30;

            // Filet fin au-dessus du pied de page : il le separe du corps sans l'alourdir.
            canvas.saveState();
            canvas.setColorStroke(BORDER);
            canvas.setLineWidth(0.6f);
            canvas.moveTo(document.left(), baseline + 12);
            canvas.lineTo(document.right(), baseline + 12);
            canvas.stroke();
            canvas.restoreState();

            ColumnText.showTextAligned(canvas, Element.ALIGN_LEFT,
                    new Phrase(label, font), document.left(), baseline, 0);
            // « Page 3 / » cale a droite, puis le total dans son gabarit, apres une espace fixe :
            // une espace en fin de texte aligne a droite serait avalee a l'affichage.
            float totalX = document.right() - TOTAL_WIDTH + 3;
            ColumnText.showTextAligned(canvas, Element.ALIGN_RIGHT,
                    new Phrase("Page " + lastNumberedPage + " /", font), totalX - 3, baseline, 0);
            canvas.addTemplate(totalPages, totalX, baseline);
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            ColumnText.showTextAligned(totalPages, Element.ALIGN_LEFT,
                    new Phrase(String.valueOf(lastNumberedPage), font), 0, 0, 0);
        }

        /** Page ou tombe chaque intertitre marque (cf. PdfBlockEmitter#TOC_TAG), pour le sommaire. */
        @Override
        public void onGenericTag(PdfWriter writer, Document document, Rectangle rect, String text) {
            tagPages.putIfAbsent(text, writer.getPageNumber());
        }

        private Map<String, Integer> tagPages() {
            return Map.copyOf(tagPages);
        }
    }

    private ExportSectionData loadExportData(Long groupId, SectionDef section) {
        SectionResponse response = sectionResponseRepository.findByGroupIdAndSectionId(groupId, section.getId()).orElse(null);
        GroupSectionStatus status = groupSectionStatusRepository.findByGroupIdAndSectionId(groupId, section.getId()).orElse(null);
        JsonNode content = exportContentReader.read(groupId, section, response);
        Integer version = response != null ? response.getVersion() : 0;
        return new ExportSectionData(section, content, version, status);
    }
}
