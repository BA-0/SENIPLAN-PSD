package com.senico.diagnostic.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.senico.diagnostic.domain.SectionDef;
import com.senico.diagnostic.domain.SectionResponse;
import com.senico.diagnostic.service.DerivedFieldsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Lecture du contenu d'une section pour l'export, champs calcules compris.
 *
 * <p>Les champs derives (totaux du budget, pourcentages du plan de financement, criticite des
 * risques, reprises inter-sections...) sont calcules a la lecture et jamais persistes : le JSON
 * stocke n'en contient aucun. Les exports lisaient ce JSON brut et sortaient donc des totaux a
 * zero et des tableaux de synthese vides. On applique ici le meme calcul que l'API de lecture
 * ({@link com.senico.diagnostic.service.SectionEngineService}), pour que documents et ecrans
 * affichent les memes chiffres.</p>
 *
 * <p>Note sur le document consolide : les reprises inter-sections (S03B depuis S02, S09B depuis
 * S09) relisent la section source telle qu'elle est en base, sans egard pour son statut. Une
 * section de synthese validee peut donc y rappeler le contenu d'une source pas encore validee.
 * C'est le propre d'une synthese, et le cas reste marginal.</p>
 */
@Component
@RequiredArgsConstructor
class ExportContentReader {

    private final ObjectMapper objectMapper;
    private final DerivedFieldsService derivedFieldsService;

    JsonNode read(Long groupId, SectionDef section, SectionResponse response) {
        if (response == null) {
            return objectMapper.createObjectNode();
        }
        ObjectNode content;
        try {
            JsonNode parsed = objectMapper.readTree(response.getContentJson());
            if (!(parsed instanceof ObjectNode object)) {
                return objectMapper.createObjectNode();
            }
            content = object;
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
        return derivedFieldsService.apply(section.getType(), groupId, content);
    }
}
