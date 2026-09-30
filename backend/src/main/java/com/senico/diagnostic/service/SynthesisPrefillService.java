package com.senico.diagnostic.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.senico.diagnostic.domain.SectionResponse;
import com.senico.diagnostic.domain.SectionType;
import com.senico.diagnostic.export.SectionLabels;
import com.senico.diagnostic.repository.SectionResponseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Pre-remplit les tableaux de synthese (S03B, S06B, S07, S09B, S17) a partir des tableaux que la
 * direction a deja saisis, tant qu'elle n'a pas commence a les remplir elle-meme.
 *
 * <p>Chaque champ n'est propose qu'une fois : les champs repris sont notes dans {@code autoFilled},
 * qui part en base avec la premiere sauvegarde (ou la soumission). Un champ note n'est plus jamais
 * repris, meme vide : la direction peut effacer une proposition sans la voir revenir. Tant que rien
 * n'est enregistre, la proposition suit les tableaux sources a chaque lecture.</p>
 */
@Service
@RequiredArgsConstructor
public class SynthesisPrefillService {

    public static final String AUTO_FILLED = "autoFilled";

    private static final JsonNodeFactory F = JsonNodeFactory.instance;

    // IDs fixes du referentiel (V2__seed_data.sql, V20, V41...)
    private static final int SECTION_STAKEHOLDERS_ID = 1;
    private static final int SECTION_RESOURCES_MATRIX_ID = 2;
    private static final int SECTION_PESTEL_ID = 3;
    private static final int SECTION_SWOT_ID = 4;
    private static final int SECTION_CAUSAL_ID = 6;
    private static final int SECTION_AXES_ID = 8;
    private static final int SECTION_LOGICAL_FRAMEWORK_ID = 9;
    private static final int SECTION_ACTION_PLAN_ID = 10;
    private static final int SECTION_STRATEGIC_FRAMEWORK_ID = 18;

    /** Nombre d'elements cites dans une note de synthese avant « … ». */
    private static final int MAX_CITED = 3;
    /** Parties prenantes d'influence ou d'importance forte citees dans la note S07. */
    private static final int MAX_KEY_ACTORS = 5;

    private final SectionResponseRepository sectionResponseRepository;
    private final ObjectMapper objectMapper;

    /**
     * Complete {@code content} en place avec les propositions des champs encore vides.
     *
     * @return les champs repris a cette lecture (vide si rien n'a ete propose)
     */
    public List<String> apply(SectionType type, Long groupId, ObjectNode content) {
        List<String> filled = new ArrayList<>();
        switch (type) {
            case RESOURCES_SYNTHESIS -> prefillResourcesSynthesis(groupId, content, filled);
            case CONSTRAINTS_SYNTHESIS -> prefillConstraintsSynthesis(groupId, content, filled);
            case INVENTORY -> prefillInventoryNote(groupId, content, filled);
            case LOGFRAME_SYNTHESIS -> prefillLogframeNote(groupId, content, filled);
            case STRATEGIC_SUMMARY -> prefillStrategicSummary(groupId, content, filled);
            default -> {
                return filled;
            }
        }
        if (!filled.isEmpty()) {
            ArrayNode marker = content.get(AUTO_FILLED) instanceof ArrayNode existing ? existing : F.arrayNode();
            filled.forEach(marker::add);
            content.set(AUTO_FILLED, marker);
        }
        return filled;
    }

    // ---- S03B : forces, faiblesses, defis et note, depuis la matrice des ressources (S02) ----
    private void prefillResourcesSynthesis(Long groupId, ObjectNode content, List<String> filled) {
        List<JsonNode> rows = resourceRows(groupId);
        if (rows.isEmpty()) {
            return;
        }
        fillArray(content, "majorStrengths", perResource(rows, "strengths"), filled);
        fillArray(content, "majorWeaknesses", perResource(rows, "weaknesses"), filled);
        fillArray(content, "priorityChallenges", perResource(rows, "challenges"), filled);
        fillText(content, "synthesisNote", resourcesNote(rows), filled);
    }

    /** Une entree par ressource renseignee : « Competences : a ; b ». */
    private ArrayNode perResource(List<JsonNode> rows, String field) {
        ArrayNode out = F.arrayNode();
        for (JsonNode row : rows) {
            List<String> items = splitItems(row.path(field).asText(""));
            if (!items.isEmpty()) {
                out.add(resourceLabel(row) + " : " + String.join(" ; ", items));
            }
        }
        return out;
    }

    private String resourcesNote(List<JsonNode> rows) {
        List<String> domains = new ArrayList<>();
        List<String> withStrengths = new ArrayList<>();
        List<String> withWeaknesses = new ArrayList<>();
        List<String> challenges = new ArrayList<>();
        for (JsonNode row : rows) {
            String label = resourceLabel(row);
            List<String> strengths = splitItems(row.path("strengths").asText(""));
            List<String> weaknesses = splitItems(row.path("weaknesses").asText(""));
            List<String> rowChallenges = splitItems(row.path("challenges").asText(""));
            if (strengths.isEmpty() && weaknesses.isEmpty() && rowChallenges.isEmpty()) {
                continue;
            }
            domains.add(lowerFirst(label));
            if (!strengths.isEmpty()) withStrengths.add(lowerFirst(label));
            if (!weaknesses.isEmpty()) withWeaknesses.add(lowerFirst(label));
            challenges.addAll(rowChallenges);
        }
        if (domains.isEmpty()) {
            return "";
        }
        StringBuilder note = new StringBuilder("L'analyse des ressources et des compétences porte sur ")
                .append(domains.size()).append(domains.size() > 1 ? " domaines : " : " domaine : ")
                .append(enumerate(domains, domains.size())).append('.');
        if (!withStrengths.isEmpty()) {
            note.append(" Les forces de la direction tiennent surtout à : ").append(enumerate(withStrengths, MAX_CITED)).append('.');
        }
        if (!withWeaknesses.isEmpty()) {
            note.append(" Les faiblesses relevées concernent principalement : ").append(enumerate(withWeaknesses, MAX_CITED)).append('.');
        }
        if (!challenges.isEmpty()) {
            note.append(" Défis prioritaires : ").append(enumerate(lowerFirstAll(challenges), MAX_CITED)).append('.');
        }
        return note.toString();
    }

    // ---- S06B : un domaine par ressource, contraintes = faiblesses, defis = defis (S02) ----
    private void prefillConstraintsSynthesis(Long groupId, ObjectNode content, List<String> filled) {
        if (alreadyFilled(content, "rows") || hasFilledRow(content.get("rows"))) {
            return;
        }
        ArrayNode rows = F.arrayNode();
        for (JsonNode resource : resourceRows(groupId)) {
            List<String> constraints = splitItems(resource.path("weaknesses").asText(""));
            List<String> challenges = splitItems(resource.path("challenges").asText(""));
            if (constraints.isEmpty() && challenges.isEmpty()) {
                continue;
            }
            ObjectNode row = F.objectNode();
            row.put("domain", resourceLabel(resource));
            row.set("constraints", toArray(constraints));
            row.set("challenges", toArray(challenges));
            rows.add(row);
        }
        if (!rows.isEmpty()) {
            content.set("rows", rows);
            filled.add("rows");
        }
    }

    private boolean hasFilledRow(JsonNode rows) {
        if (rows == null || !rows.isArray()) {
            return false;
        }
        for (JsonNode row : rows) {
            if (!row.path("domain").asText("").isBlank()
                    || !isBlankArray(row.get("constraints")) || !isBlankArray(row.get("challenges"))) {
                return true;
            }
        }
        return false;
    }

    // ---- S07 : note de synthese des recommandations, depuis S01, S03, S04 et S06 ----
    private void prefillInventoryNote(Long groupId, ObjectNode content, List<String> filled) {
        if (alreadyFilled(content, "synthesisNote") || !content.path("synthesisNote").asText("").isBlank()) {
            return;
        }
        List<String> paragraphs = new ArrayList<>();

        JsonNode stakeholders = source(groupId, SECTION_STAKEHOLDERS_ID).path("rows");
        if (stakeholders.isArray() && !stakeholders.isEmpty()) {
            int internal = 0;
            int external = 0;
            List<String> key = new ArrayList<>();
            for (JsonNode row : stakeholders) {
                String scope = row.path("scope").asText("");
                if ("INTERNE".equals(scope)) internal++;
                if ("EXTERNE".equals(scope)) external++;
                if ("FORT".equals(row.path("influence").asText("")) || "FORT".equals(row.path("importance").asText(""))) {
                    String actor = SectionLabels.stakeholderCategory(row.path("category").asText("").trim());
                    if (!actor.isBlank() && !key.contains(actor)) key.add(actor);
                }
            }
            StringBuilder p = new StringBuilder("Parties prenantes : ").append(stakeholders.size())
                    .append(stakeholders.size() > 1 ? " acteurs identifiés" : " acteur identifié");
            if (internal + external > 0) {
                p.append(" (").append(internal).append(" interne").append(internal > 1 ? "s" : "")
                        .append(", ").append(external).append(" externe").append(external > 1 ? "s" : "").append(')');
            }
            if (!key.isEmpty()) {
                p.append(", dont les plus déterminants : ").append(enumerate(key, MAX_KEY_ACTORS));
            }
            paragraphs.add(p.append('.').toString());
        }

        JsonNode pestel = source(groupId, SECTION_PESTEL_ID).path("rows");
        List<String> threats = new ArrayList<>();
        List<String> opportunities = new ArrayList<>();
        if (pestel.isArray()) {
            for (JsonNode row : pestel) {
                threats.addAll(splitItems(row.path("threats").asText("")));
                opportunities.addAll(splitItems(row.path("opportunities").asText("")));
            }
        }
        if (!threats.isEmpty() || !opportunities.isEmpty()) {
            StringBuilder p = new StringBuilder("Environnement (PESTEL) :");
            if (!opportunities.isEmpty()) p.append(" opportunités à saisir : ").append(enumerate(opportunities, MAX_CITED)).append('.');
            if (!threats.isEmpty()) p.append(" Menaces à anticiper : ").append(enumerate(threats, MAX_CITED)).append('.');
            paragraphs.add(p.toString());
        }

        JsonNode swot = source(groupId, SECTION_SWOT_ID);
        List<String> strengths = textItems(swot.get("strengths"));
        List<String> weaknesses = textItems(swot.get("weaknesses"));
        if (!strengths.isEmpty() || !weaknesses.isEmpty()) {
            StringBuilder p = new StringBuilder("SWOT :");
            if (!strengths.isEmpty()) p.append(" forces à consolider : ").append(enumerate(strengths, MAX_CITED)).append('.');
            if (!weaknesses.isEmpty()) p.append(" Faiblesses à corriger : ").append(enumerate(weaknesses, MAX_CITED)).append('.');
            paragraphs.add(p.toString());
        }

        JsonNode causal = source(groupId, SECTION_CAUSAL_ID).path("rows");
        List<String> rootCauses = new ArrayList<>();
        List<String> solutions = new ArrayList<>();
        if (causal.isArray()) {
            for (JsonNode row : causal) {
                String src = row.path("source").asText("");
                if ("CAUSES_PROFONDES".equals(src)) rootCauses.addAll(textItems(row.get("items")));
                if ("SOLUTIONS".equals(src)) solutions.addAll(textItems(row.get("items")));
            }
        }
        if (!rootCauses.isEmpty() || !solutions.isEmpty()) {
            StringBuilder p = new StringBuilder("Analyse causale :");
            if (!rootCauses.isEmpty()) p.append(" causes profondes : ").append(enumerate(rootCauses, MAX_CITED)).append('.');
            if (!solutions.isEmpty()) p.append(" Solutions recommandées : ").append(enumerate(solutions, MAX_CITED)).append('.');
            paragraphs.add(p.toString());
        }

        if (!paragraphs.isEmpty()) {
            content.put("synthesisNote", String.join("\n\n", paragraphs));
            filled.add("synthesisNote");
        }
    }

    // ---- S09B : note de synthese du cadre logique, depuis S09 (et les intitules d'axes de S08) ----
    private void prefillLogframeNote(Long groupId, ObjectNode content, List<String> filled) {
        if (alreadyFilled(content, "synthesisNote") || !content.path("synthesisNote").asText("").isBlank()) {
            return;
        }
        JsonNode axes = source(groupId, SECTION_LOGICAL_FRAMEWORK_ID).path("axes");
        JsonNode titles = source(groupId, SECTION_AXES_ID).path("axes");
        List<String> paragraphs = new ArrayList<>();
        int axisNumber = 0;
        if (axes.isArray()) {
            for (JsonNode axis : axes) {
                axisNumber++;
                // Un objectif saisi sur plusieurs lignes tient en une phrase dans la note.
                String objective = String.join(" ; ", splitItems(axis.path("objective").asText("")));
                String impact = firstLevel(axis.get("rows"), "IMPACT");
                List<String> effects = levelItems(axis.get("rows"), "EFFET");
                if (objective.isEmpty() && impact.isEmpty() && effects.isEmpty()) {
                    continue;
                }
                StringBuilder p = new StringBuilder("Axe ").append(axisNumber);
                String title = axisTitle(titles, axis.path("axisCode").asText(""));
                if (!title.isEmpty()) p.append(" – ").append(title);
                p.append(" :");
                if (!objective.isEmpty()) p.append(" objectif : ").append(withoutFinalDot(objective)).append('.');
                if (!impact.isEmpty()) p.append(" Impact visé : ").append(withoutFinalDot(impact)).append('.');
                if (!effects.isEmpty()) p.append(" Effets attendus : ").append(enumerate(effects, MAX_CITED)).append('.');
                paragraphs.add(p.toString());
            }
        }
        if (!paragraphs.isEmpty()) {
            String intro = "Le cadre logique de la direction s'articule autour de " + paragraphs.size()
                    + (paragraphs.size() > 1 ? " axes stratégiques." : " axe stratégique.");
            content.put("synthesisNote", intro + "\n\n" + String.join("\n\n", paragraphs));
            filled.add("synthesisNote");
        }
    }

    // ---- S17 : vision (S07B), orientations = extrants du plan d'actions (S10), actions = activites ----
    private void prefillStrategicSummary(Long groupId, ObjectNode content, List<String> filled) {
        if (!alreadyFilled(content, "vision") && content.path("vision").asText("").isBlank()) {
            String vision = source(groupId, SECTION_STRATEGIC_FRAMEWORK_ID).path("vision").asText("").trim();
            if (!vision.isEmpty()) {
                content.put("vision", vision);
                filled.add("vision");
            }
        }

        if (alreadyFilled(content, "axes") || !(content.get("axes") instanceof ArrayNode axes) || hasOrientation(axes)) {
            return;
        }
        JsonNode planAxes = source(groupId, SECTION_ACTION_PLAN_ID).path("axes");
        JsonNode strategicAxes = source(groupId, SECTION_AXES_ID).path("axes");
        boolean any = false;
        for (JsonNode axisNode : axes) {
            if (!(axisNode instanceof ObjectNode axis)) {
                continue;
            }
            String code = axis.path("axisCode").asText("");
            ArrayNode orientations = orientationsFromActionPlan(findAxis(planAxes, code));
            if (orientations.isEmpty()) {
                // Plan d'actions pas encore saisi pour cet axe : les objectifs specifiques (S08) tiennent lieu d'orientations.
                for (String os : textItems(findAxis(strategicAxes, code).get("specificObjectives"))) {
                    ObjectNode orientation = F.objectNode();
                    orientation.put("label", os);
                    orientation.set("actions", F.arrayNode());
                    orientations.add(orientation);
                }
            }
            if (!orientations.isEmpty()) {
                axis.set("orientations", orientations);
                any = true;
            }
        }
        if (any) {
            filled.add("axes");
        }
    }

    private ArrayNode orientationsFromActionPlan(JsonNode planAxis) {
        ArrayNode orientations = F.arrayNode();
        JsonNode effects = planAxis.get("effects");
        if (effects == null || !effects.isArray()) {
            return orientations;
        }
        for (JsonNode effect : effects) {
            JsonNode rows = effect.get("rows");
            if (rows == null || !rows.isArray()) {
                continue;
            }
            for (JsonNode row : rows) {
                String label = row.path("extrant").asText("").trim();
                List<String> activities = splitItems(row.path("activities").asText(""));
                if (label.isEmpty() && activities.isEmpty()) {
                    continue;
                }
                ObjectNode orientation = F.objectNode();
                orientation.put("label", label.isEmpty() ? effect.path("effectLabel").asText("").trim() : label);
                ArrayNode actions = F.arrayNode();
                for (String activity : activities) {
                    ObjectNode action = F.objectNode();
                    action.put("label", activity);
                    action.put("constraintsOrOpportunities", "");
                    actions.add(action);
                }
                orientation.set("actions", actions);
                orientations.add(orientation);
            }
        }
        return orientations;
    }

    private boolean hasOrientation(JsonNode axes) {
        for (JsonNode axis : axes) {
            JsonNode orientations = axis.get("orientations");
            if (orientations != null && orientations.isArray()) {
                for (JsonNode o : orientations) {
                    if (!o.path("label").asText("").isBlank() || !isBlankArray(o.get("actions"))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ---- utilitaires ----

    private void fillText(ObjectNode content, String field, String value, List<String> filled) {
        if (alreadyFilled(content, field) || !content.path(field).asText("").isBlank() || value.isBlank()) {
            return;
        }
        content.put(field, value);
        filled.add(field);
    }

    private void fillArray(ObjectNode content, String field, ArrayNode value, List<String> filled) {
        if (alreadyFilled(content, field) || !isBlankArray(content.get(field)) || value.isEmpty()) {
            return;
        }
        content.set(field, value);
        filled.add(field);
    }

    /** Le champ a deja ete propose une fois et la proposition est en base : on n'y revient pas. */
    private boolean alreadyFilled(ObjectNode content, String field) {
        JsonNode marker = content.get(AUTO_FILLED);
        if (marker == null || !marker.isArray()) {
            return false;
        }
        for (JsonNode f : marker) {
            if (field.equals(f.asText())) {
                return true;
            }
        }
        return false;
    }

    /** Tableau absent, vide, ou dont tous les elements sont des chaines vides (cellule ajoutee puis laissee vide). */
    private static boolean isBlankArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return true;
        }
        for (JsonNode item : node) {
            if (!item.isTextual() || !item.asText().isBlank()) {
                return false;
            }
        }
        return true;
    }

    private List<JsonNode> resourceRows(Long groupId) {
        List<JsonNode> rows = new ArrayList<>();
        JsonNode stored = source(groupId, SECTION_RESOURCES_MATRIX_ID).path("rows");
        if (!stored.isArray()) {
            return rows;
        }
        // Ordre du modele client, comme la matrice elle-meme (DerivedFieldsService.normalizeResourceRows).
        Set<JsonNode> placed = Collections.newSetFromMap(new IdentityHashMap<>());
        for (String key : com.senico.diagnostic.validation.DefaultSectionContentFactory.RESOURCE_KEYS) {
            for (JsonNode row : stored) {
                if (key.equals(row.path("resourceKey").asText("")) && placed.add(row)) {
                    rows.add(row);
                    break;
                }
            }
        }
        for (JsonNode row : stored) {
            if (!placed.contains(row)) {
                rows.add(row);
            }
        }
        return rows;
    }

    /** Libelle court de la ressource : sans la precision entre parentheses du modele client. */
    private static String resourceLabel(JsonNode row) {
        String label = SectionLabels.resource(row.path("resourceKey").asText(""));
        String shortLabel = label.replaceAll("\\s*\\(.*\\)\\s*$", "").trim();
        return shortLabel.isEmpty() ? label : shortLabel;
    }

    private JsonNode source(Long groupId, int sectionId) {
        return sectionResponseRepository.findByGroupIdAndSectionId(groupId, sectionId)
                .map(this::readTree)
                .orElse(F.objectNode());
    }

    private static JsonNode findAxis(JsonNode axes, String code) {
        if (axes != null && axes.isArray()) {
            for (JsonNode axis : axes) {
                if (code.equals(axis.path("axisCode").asText(""))) {
                    return axis;
                }
            }
        }
        return F.objectNode();
    }

    private static String axisTitle(JsonNode strategicAxes, String code) {
        return findAxis(strategicAxes, code).path("title").asText("").trim();
    }

    private static String firstLevel(JsonNode rows, String level) {
        List<String> items = levelItems(rows, level);
        return items.isEmpty() ? "" : items.get(0);
    }

    private static List<String> levelItems(JsonNode rows, String level) {
        List<String> items = new ArrayList<>();
        if (rows != null && rows.isArray()) {
            for (JsonNode row : rows) {
                if (level.equals(row.path("level").asText(""))) {
                    items.addAll(splitItems(row.path("interventionLogic").asText("")));
                }
            }
        }
        return items;
    }

    /** Elements d'un tableau JSON de chaines, sans les cases vides. */
    private static List<String> textItems(JsonNode array) {
        List<String> items = new ArrayList<>();
        if (array != null && array.isArray()) {
            for (JsonNode item : array) {
                String text = clean(item.asText(""));
                if (!text.isEmpty()) {
                    items.add(text);
                }
            }
        }
        return items;
    }

    /**
     * Decoupe une cellule saisie en liste (« - a\n- b », « • a », « 1. a ») en ses elements, sans
     * puces ni lignes vides.
     */
    static List<String> splitItems(String text) {
        List<String> items = new ArrayList<>();
        if (text == null) {
            return items;
        }
        for (String line : text.split("\\r?\\n")) {
            String item = line.replaceFirst("^\\s*(?:[-–—•*·▪►]+|\\d+[.)])\\s*", "").trim();
            item = clean(item);
            if (!item.isEmpty()) {
                items.add(item);
            }
        }
        return items;
    }

    /** Sans ponctuation orpheline en tete (« : Renforcer… ») ni separateur en fin de ligne. */
    private static String clean(String text) {
        return text.replaceFirst("^[\\s:;,.]+", "").replaceFirst("[\\s;,]+$", "").trim();
    }

    /** « a, b et c », limite a {@code max} elements suivis de « … » au-dela. */
    private static String enumerate(List<String> items, int max) {
        List<String> cleaned = new ArrayList<>();
        for (String item : items) {
            String text = withoutFinalDot(item.trim());
            if (!text.isEmpty() && !cleaned.contains(text)) {
                cleaned.add(text);
            }
        }
        boolean truncated = cleaned.size() > max;
        List<String> kept = truncated ? cleaned.subList(0, max) : cleaned;
        String joined;
        if (kept.size() <= 1) {
            joined = String.join("", kept);
        } else if (truncated) {
            joined = String.join(" ; ", kept) + " ; …";
        } else {
            joined = String.join(" ; ", kept.subList(0, kept.size() - 1)) + " et " + kept.get(kept.size() - 1);
        }
        return joined;
    }

    private static String withoutFinalDot(String text) {
        return text.replaceFirst("[.;:\\s]+$", "");
    }

    private static String lowerFirst(String text) {
        if (text.length() < 2 || Character.isUpperCase(text.charAt(1))) {
            return text; // sigle (« SI », « RH ») : on n'y touche pas
        }
        return Character.toLowerCase(text.charAt(0)) + text.substring(1);
    }

    private static List<String> lowerFirstAll(List<String> items) {
        List<String> out = new ArrayList<>();
        items.forEach(i -> out.add(lowerFirst(i)));
        return out;
    }

    private static ArrayNode toArray(List<String> items) {
        ArrayNode array = F.arrayNode();
        items.forEach(array::add);
        return array;
    }

    private JsonNode readTree(SectionResponse response) {
        try {
            return objectMapper.readTree(response.getContentJson());
        } catch (Exception e) {
            return F.objectNode();
        }
    }
}
