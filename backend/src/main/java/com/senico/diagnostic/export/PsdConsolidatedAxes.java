package com.senico.diagnostic.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Les axes strategiques de l'entreprise, arretes par la Direction Generale, et le rattachement a
 * chacun des axes proposes par les directions.
 *
 * <p>Chaque direction formule ses quatre axes dans son canevas (S08) : additionnes, cela faisait
 * vingt « axes strategiques » pour SENICO, la ou un PSD publie en compte quatre ou cinq, communs
 * a toute l'entreprise et portes chacun par plusieurs directions. Le regroupement ne se devine
 * pas a partir des intitules : c'est un arbitrage, saisi par l'admin sur l'ecran du Plan
 * Strategique de SENICO et stocke comme bloc narratif (cle AXES_CONSOLIDES), au format :</p>
 *
 * <pre>{"axes": [{"title": "...", "objective": "...", "links": [{"groupId": 2, "axisCode": "AXE1"}]}]}</pre>
 *
 * <p>Un contenu vide ou illisible donne une liste vide : les documents retombent alors sur les
 * axes des directions, en le signalant, plutot que de publier un cadre strategique faux.</p>
 */
public final class PsdConsolidatedAxes {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PsdConsolidatedAxes() {
    }

    /** Rattachement d'un axe de direction (S08) a un axe de l'entreprise. */
    public record Link(long groupId, String axisCode) {
    }

    /** Un axe de l'entreprise : son intitule, son objectif general, les axes de direction qu'il regroupe. */
    public record Axis(String title, String objective, List<Link> links) {
    }

    /** @return les axes, dans l'ordre saisi ; vide si le contenu est absent ou illisible */
    public static List<Axis> parse(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return read(MAPPER.readTree(json));
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Controle a l'enregistrement : un contenu non vide doit etre lisible et chaque axe titre.
     * Refuser la saisie vaut mieux que de decouvrir, a l'export, un cadre strategique disparu.
     *
     * @return le motif du refus, ou null si le contenu est acceptable
     */
    public static String validationError(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (Exception e) {
            return "Le contenu des axes stratégiques n'est pas un JSON valide.";
        }
        JsonNode axes = root.get("axes");
        if (axes == null || !axes.isArray()) {
            return "Le contenu des axes stratégiques doit comporter une liste « axes ».";
        }
        for (JsonNode axis : axes) {
            if (axis.path("title").asText("").isBlank()) {
                return "Chaque axe stratégique doit avoir un intitulé.";
            }
        }
        return null;
    }

    private static List<Axis> read(JsonNode root) {
        List<Axis> axes = new ArrayList<>();
        for (JsonNode axis : JsonUtil.arr(root, "axes")) {
            String title = JsonUtil.text(axis, "title").trim();
            if (title.isEmpty()) {
                continue;
            }
            List<Link> links = new ArrayList<>();
            for (JsonNode link : JsonUtil.arr(axis, "links")) {
                String code = JsonUtil.text(link, "axisCode").trim();
                long groupId = link.path("groupId").asLong(0);
                if (groupId > 0 && !code.isEmpty()) {
                    links.add(new Link(groupId, code));
                }
            }
            axes.add(new Axis(title, JsonUtil.text(axis, "objective").trim(), List.copyOf(links)));
        }
        return List.copyOf(axes);
    }
}
