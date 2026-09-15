package db.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Harmonisation des textes saisis par les directions, avant l'envoi du Plan Strategique au client :
 * <ul>
 *   <li>la vision et la mission de la Direction Logistique repetaient la meme phrase, et « Foi » figurait
 *   parmi ses valeurs : elles sont remplacees par un texte tire de ses axes, a faire valider par la direction ;</li>
 *   <li>les effets immediats et les extrants du cadre logique etaient restes a leur formulation generique :
 *   ils reprennent, comme ceux de la Direction Logistique, les orientations et les extrants du plan d'actions ;</li>
 *   <li>le plan d'actions ne cochait que 2027-2029 la ou le budget court jusqu'en 2031 : chaque exercice
 *   budgete est coche, sans toucher aux montants ;</li>
 *   <li>« les quatre axes du plan » devient « ses quatre axes » : le plan en compte cinq ;</li>
 *   <li>les accents sont retablis, d'apres un dictionnaire verifie mot a mot (db/reference/accents-fr.tsv).</li>
 * </ul>
 * Chaque correction ne s'applique qu'aux formulations d'origine : un texte deja reecrit par une direction
 * reste tel quel.
 */
public class V27__Harmonisation_textes_directions extends BaseJavaMigration {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String[] YEARS = {"2027", "2028", "2029", "2030", "2031"};

    static final String LOGISTIQUE_PLACEHOLDER = "Leader dans le marché national et international";
    static final String GENERIC_IMMEDIATE_EFFECTS = "Ameliorer concretement les resultats operationnels lies a l'axe";
    static final String GENERIC_OUTPUTS = "Produire les livrables et realisations prevues dans le plan d'actions";

    /** Tournures a double lecture, tranchees d'apres le seul contexte ou elles apparaissent. */
    private static final String[][] PHRASES = {
            {"la ou la demande", "là où la demande"},
            {"temps ou les", "temps où les"},
            {"trop base sur", "trop basé sur"},
            {"commercial partage", "commercial partagé"},
            {"freine par", "freiné par"},
            {"peu structures", "peu structurés"},
            {"financement structures", "financement structurés"},
            {"sont mesures", "sont mesurés"},
            {"les quatre axes du plan", "ses quatre axes"},
    };

    /** Mots qui, apres « a », en font l'auxiliaire avoir et non la preposition « à ». */
    private static final Set<String> AFTER_AUXILIARY = Set.of("mobilise", "mobilisé", "été", "eu", "pas");

    private static final Pattern WORD = Pattern.compile("\\p{L}+");
    private static final Map<String, String> WORDS = loadWords();

    private record Row(long id, long groupId, String code, JsonNode original, JsonNode content) {
    }

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        Map<Long, Row> rows = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT sr.id, sr.group_id, s.code, sr.content_json "
                     + "FROM section_responses sr JOIN sections s ON s.id = sr.section_id")) {
            while (rs.next()) {
                JsonNode original = MAPPER.readTree(rs.getString("content_json"));
                rows.put(rs.getLong("id"), new Row(rs.getLong("id"), rs.getLong("group_id"), rs.getString("code"),
                        original, original.deepCopy()));
            }
        }
        Map<String, Row> byGroupAndCode = new HashMap<>();
        rows.values().forEach(row -> byGroupAndCode.put(row.groupId() + ":" + row.code(), row));

        for (Row row : rows.values()) {
            if (!(row.content() instanceof ObjectNode content)) {
                continue;
            }
            switch (row.code()) {
                case "S07B" -> replaceLogistiqueFramework(content);
                case "S09" -> specifyLogframe(content, contentOf(byGroupAndCode.get(row.groupId() + ":S10")));
                case "S10" -> scheduleBudgetedYears(content, contentOf(byGroupAndCode.get(row.groupId() + ":S11")));
                default -> {
                }
            }
        }

        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE section_responses SET content_json = ? WHERE id = ?")) {
            for (Row row : rows.values()) {
                accentuateTree(row.content());
                if (!row.content().equals(row.original())) {
                    update.setString(1, MAPPER.writeValueAsString(row.content()));
                    update.setLong(2, row.id());
                    update.addBatch();
                }
            }
            update.executeBatch();
        }
    }

    private static JsonNode contentOf(Row row) {
        return row == null ? null : row.content();
    }

    /** Vision, mission et valeurs de la Direction Logistique, tant qu'elles repetent la phrase d'origine. */
    static void replaceLogistiqueFramework(ObjectNode content) {
        if (!LOGISTIQUE_PLACEHOLDER.equals(content.path("vision").asText())) {
            return;
        }
        content.put("vision", "Faire de la Direction Logistique un acteur de référence de l'acheminement du courrier "
                + "et des colis sur le marché national et international, grâce à un parc modernisé, des tournées "
                + "optimisées et un maillage de centres de tri renforcé, à l'horizon 2031.");
        ArrayNode mission = content.putArray("mission");
        mission.add("Assurer l'acheminement fiable et dans les délais du courrier et des colis sur l'ensemble du territoire");
        mission.add("Moderniser et optimiser les moyens logistiques : parc de véhicules, tournées, centres de tri et entrepôts");
        mission.add("Adapter la chaîne logistique à la croissance des volumes du e-commerce");
        ArrayNode values = content.putArray("values");
        values.add("Fiabilité");
        values.add("Intégrité");
        values.add("Engagement");
        values.add("Responsabilité");
    }

    /**
     * Effets immediats et extrants du cadre logique encore generiques : ils nomment les orientations et les
     * extrants que le plan d'actions (S10) rattache au meme axe.
     */
    static void specifyLogframe(ObjectNode logframe, JsonNode actionPlan) {
        if (actionPlan == null) {
            return;
        }
        for (JsonNode axis : logframe.path("axes")) {
            JsonNode planAxis = axisOf(actionPlan, axis.path("axisCode").asText());
            if (planAxis == null) {
                continue;
            }
            List<String> orientations = new ArrayList<>();
            LinkedHashSet<String> outputs = new LinkedHashSet<>();
            for (JsonNode effect : planAxis.path("effects")) {
                String label = effect.path("effectLabel").asText("").trim();
                if (!label.isEmpty()) {
                    orientations.add(label);
                }
                for (JsonNode row : effect.path("rows")) {
                    String output = row.path("extrant").asText("").trim();
                    if (!output.isEmpty()) {
                        outputs.add(output);
                    }
                }
            }
            for (JsonNode node : axis.path("rows")) {
                if (!(node instanceof ObjectNode row)) {
                    continue;
                }
                String level = row.path("level").asText();
                String logic = row.path("interventionLogic").asText("");
                if ("EFFETS_IMMEDIATS".equals(level) && GENERIC_IMMEDIATE_EFFECTS.equals(logic) && !orientations.isEmpty()) {
                    row.put("interventionLogic", "Mise en œuvre effective des orientations : " + String.join(" ; ", orientations));
                } else if ("EXTRANTS".equals(level) && GENERIC_OUTPUTS.equals(logic) && !outputs.isEmpty()) {
                    row.put("interventionLogic", "Production des extrants : " + String.join(" ; ", outputs));
                }
            }
        }
    }

    /** Coche, dans le plan d'actions, chaque exercice ou la meme activite du budget (S11) porte un montant. */
    static void scheduleBudgetedYears(ObjectNode actionPlan, JsonNode budget) {
        if (budget == null) {
            return;
        }
        Map<String, JsonNode> amountsByActivity = new HashMap<>();
        for (JsonNode axis : budget.path("axes")) {
            for (JsonNode effect : axis.path("effects")) {
                for (JsonNode row : effect.path("rows")) {
                    String key = activityKey(axis, row);
                    if (key != null) {
                        amountsByActivity.putIfAbsent(key, row.path("years"));
                    }
                }
            }
        }
        for (JsonNode axis : actionPlan.path("axes")) {
            for (JsonNode effect : axis.path("effects")) {
                for (JsonNode row : effect.path("rows")) {
                    String key = activityKey(axis, row);
                    JsonNode amounts = key == null ? null : amountsByActivity.get(key);
                    if (amounts == null || !(row.path("years") instanceof ObjectNode ticks)) {
                        continue;
                    }
                    for (String year : YEARS) {
                        if (amounts.path(year).asDouble(0) > 0 && !ticks.path(year).asBoolean(false)) {
                            ticks.put(year, true);
                        }
                    }
                }
            }
        }
    }

    private static JsonNode axisOf(JsonNode content, String axisCode) {
        for (JsonNode axis : content.path("axes")) {
            if (axisCode.equals(axis.path("axisCode").asText())) {
                return axis;
            }
        }
        return null;
    }

    private static String activityKey(JsonNode axis, JsonNode row) {
        String activity = row.path("activities").asText("").trim();
        if (activity.isEmpty()) {
            activity = row.path("extrant").asText("").trim();
        }
        if (activity.isEmpty()) {
            return null;
        }
        String normalized = Normalizer.normalize(activity, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return axis.path("axisCode").asText() + "|" + normalized;
    }

    /** Retablit les accents dans toutes les valeurs textuelles ; les codes en majuscules restent intacts. */
    static void accentuateTree(JsonNode node) {
        if (node instanceof ObjectNode object) {
            List<String> names = new ArrayList<>();
            object.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                JsonNode child = object.get(name);
                if (child.isTextual()) {
                    object.set(name, TextNode.valueOf(accentuate(child.asText())));
                } else {
                    accentuateTree(child);
                }
            }
        } else if (node instanceof ArrayNode array) {
            for (int i = 0; i < array.size(); i++) {
                JsonNode child = array.get(i);
                if (child.isTextual()) {
                    array.set(i, TextNode.valueOf(accentuate(child.asText())));
                } else {
                    accentuateTree(child);
                }
            }
        }
    }

    static String accentuate(String text) {
        String phrased = text;
        for (String[] phrase : PHRASES) {
            phrased = phrased.replace(phrase[0], phrase[1]);
        }
        Matcher matcher = WORD.matcher(phrased);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String word = matcher.group();
            String replacement = word;
            boolean ascii = word.chars().allMatch(ch -> ch < 128);
            boolean code = word.length() > 1 && word.equals(word.toUpperCase(Locale.ROOT));
            if (ascii && !code) {
                if (word.equals("a")) {
                    replacement = prepositionOrAuxiliary(phrased, matcher.start(), matcher.end());
                } else {
                    String accented = WORDS.get(word.toLowerCase(Locale.ROOT));
                    if (accented != null) {
                        replacement = Character.isUpperCase(word.charAt(0))
                                ? Character.toUpperCase(accented.charAt(0)) + accented.substring(1)
                                : accented;
                    }
                }
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** « a » isole entre deux espaces est la preposition « à », sauf devant un participe (« a mobilisé »). */
    private static String prepositionOrAuxiliary(String text, int start, int end) {
        if (start == 0 || !Character.isWhitespace(text.charAt(start - 1))
                || end >= text.length() || !Character.isWhitespace(text.charAt(end))) {
            return "a";
        }
        Matcher next = WORD.matcher(text);
        if (next.find(end) && AFTER_AUXILIARY.contains(next.group().toLowerCase(Locale.ROOT))) {
            return "a";
        }
        return "à";
    }

    private static Map<String, String> loadWords() {
        Map<String, String> words = new HashMap<>();
        try (InputStream in = V27__Harmonisation_textes_directions.class.getResourceAsStream("/db/reference/accents-fr.tsv");
             BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(in,
                     "db/reference/accents-fr.tsv introuvable"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\t");
                words.put(parts[0].trim(), parts[1].trim());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return words;
    }
}
