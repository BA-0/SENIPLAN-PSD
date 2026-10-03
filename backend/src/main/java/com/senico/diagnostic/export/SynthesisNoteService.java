package com.senico.diagnostic.export;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.senico.diagnostic.domain.GroupSectionStatus;
import com.senico.diagnostic.domain.NarrativeBlockKey;
import com.senico.diagnostic.domain.PsdNarrativeBlock;
import com.senico.diagnostic.domain.SectionDef;
import com.senico.diagnostic.domain.SectionResponse;
import com.senico.diagnostic.domain.SynthesisNoteEdit;
import com.senico.diagnostic.domain.SynthesisNoteVersion;
import com.senico.diagnostic.domain.WorkGroup;
import com.senico.diagnostic.dto.synthesis.SynthesisNoteDto;
import com.senico.diagnostic.dto.synthesis.UpdateSynthesisNoteRequest;
import com.senico.diagnostic.repository.GroupSectionStatusRepository;
import com.senico.diagnostic.repository.PsdNarrativeBlockRepository;
import com.senico.diagnostic.repository.SectionDefRepository;
import com.senico.diagnostic.repository.SectionResponseRepository;
import com.senico.diagnostic.repository.SynthesisNoteEditRepository;
import com.senico.diagnostic.repository.SynthesisNoteVersionRepository;
import com.senico.diagnostic.repository.WorkGroupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Contenu de la note de synthese, commun au PDF et au Word.
 *
 * <p>Demande client du 03/10/2026 : la Direction Generale peut corriger la note avant de la
 * diffuser. Sa version corrigee remplace alors le document genere dans les deux exports, jusqu'a
 * ce qu'elle choisisse de repartir du document a jour.</p>
 *
 * <p>Une section approuvee apres la correction y entre d'elle-meme : la version corrigee est
 * fusionnee avec le document genere a jour (cf. {@link SynthesisNoteMerge}), a partir du document
 * genere sur lequel le DG a travaille, conserve a l'enregistrement.</p>
 *
 * <p>Plusieurs personnes la corrigent en meme temps, depuis des postes differents et souvent le
 * meme compte. Chacune envoie, avec sa version, celle dont elle est partie : ses corrections sont
 * fusionnees avec celles enregistrees entre-temps au lieu de les ecraser, puis chaque
 * enregistrement est annonce en temps reel sur {@value #TOPIC} pour que les autres postes
 * l'affichent.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SynthesisNoteService {

    private static final TypeReference<List<ExportBlock>> BLOCKS = new TypeReference<>() {
    };

    /** Annonce de chaque enregistrement (poste, version), sans contenu : les postes ouverts rechargent la note. */
    public static final String TOPIC = "/topic/synthesis-note";

    /** Les enregistrements passent un par un : chacun fusionne sur le precedent, deja ecrit. */
    private final ReentrantLock saveLock = new ReentrantLock();

    /** Versions deja lues, par empreinte : une version ne change jamais, inutile de la relire a chaque copie recue. */
    private final Map<String, List<ExportBlock>> parsedVersions = new ConcurrentHashMap<>();

    private final SectionDefRepository sectionDefRepository;
    private final SectionResponseRepository sectionResponseRepository;
    private final GroupSectionStatusRepository groupSectionStatusRepository;
    private final WorkGroupRepository workGroupRepository;
    private final PsdNarrativeBlockRepository psdNarrativeBlockRepository;
    private final SynthesisNoteEditRepository synthesisNoteEditRepository;
    private final SynthesisNoteVersionRepository synthesisNoteVersionRepository;
    private final PsdBriefBuilder psdBriefBuilder;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;
    private final SimpMessagingTemplate messagingTemplate;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    /** Les blocs que les exports rendent : la version corrigee par le DG s'il y en a une. */
    @Transactional(readOnly = true)
    public List<ExportBlock> effectiveBlocks() {
        return currentEdit().map(edit -> withUpdates(edit, generatedBlocks())).orElseGet(this::generatedBlocks);
    }

    /** La note telle qu'elle s'affiche et s'exporte, avec sa version (cf. {@link #remember}). */
    @Transactional
    public SynthesisNoteDto view() {
        List<ExportBlock> generated = generatedBlocks();
        Optional<SynthesisNoteEdit> edit = currentEdit();
        if (edit.isEmpty()) {
            return new SynthesisNoteDto(generated, false, false, null, null, remember(generated), 0);
        }
        SynthesisNoteEdit e = edit.get();
        boolean sourceChanged = !hash(write(generated)).equals(e.getSourceHash());
        List<ExportBlock> blocks = withUpdates(e, generated);
        return new SynthesisNoteDto(blocks, true, sourceChanged, e.getUpdatedAt(), e.getUpdatedBy(), remember(blocks), 0);
    }

    /**
     * Garde la version qu'un poste vient de charger, pour fusionner ses corrections quand il les
     * enregistrera. Les versions de plus de 24 heures sont purgees a chaque enregistrement.
     */
    private String remember(List<ExportBlock> blocks) {
        String json = write(blocks);
        String version = hash(json);
        if (!synthesisNoteVersionRepository.existsById(version)) {
            synthesisNoteVersionRepository.save(new SynthesisNoteVersion(version, json, LocalDateTime.now()));
        }
        return version;
    }

    /**
     * La version corrigee, augmentee de ce que le document genere a recu depuis : nouvelles
     * approbations, contenus modifies. Les corrections du DG sur les blocs inchanges sont gardees.
     */
    private List<ExportBlock> withUpdates(SynthesisNoteEdit edit, List<ExportBlock> generated) {
        List<ExportBlock> ours = read(edit.getContent());
        String generatedJson = write(generated);
        if (hash(generatedJson).equals(edit.getSourceHash())) {
            return ours;
        }
        List<ExportBlock> base = baseOf(edit);
        // Sans document de depart fiable, la version corrigee reste telle quelle : la page signale
        // l'ecart et le DG peut repartir du document a jour. Sinon, les donnees approuvees font foi.
        return base == null ? ours : SynthesisNoteMerge.merge(base, ours, generated, SynthesisNoteMerge.Winner.THEIRS).blocks();
    }

    /**
     * Le document genere sur lequel le DG a travaille. Une correction enregistree avant qu'on le
     * conserve n'en a que l'empreinte : on le reconstitue a partir des seules sections approuvees a
     * cette date, et on ne le retient que si l'empreinte confirme qu'il est identique.
     */
    private List<ExportBlock> baseOf(SynthesisNoteEdit edit) {
        if (edit.getSourceContent() != null) {
            return read(edit.getSourceContent());
        }
        if (edit.getUpdatedAt() == null || edit.getSourceHash() == null) {
            return null;
        }
        List<ExportBlock> rebuilt = generatedBlocks(edit.getUpdatedAt());
        return hash(write(rebuilt)).equals(edit.getSourceHash()) ? rebuilt : null;
    }

    /**
     * Enregistre les corrections d'un poste, fusionnees avec celles enregistrees entre-temps par
     * les autres postes et avec les sections approuvees depuis. Sur un meme element modifie des deux
     * cotes, la derniere personne a enregistrer l'emporte.
     *
     * @param baseVersion version dont le poste est parti ; inconnue (purgee), ses blocs modifies
     *                    remplacent ceux de la note a la meme place
     * @param changes     blocs modifies par le poste, par leur place dans cette version
     * @param updatedBy   compte qui enregistre
     * @param clientId    poste qui enregistre, repris dans l'annonce pour qu'il ne se recharge pas lui-meme
     */
    public SynthesisNoteDto save(String baseVersion, List<UpdateSynthesisNoteRequest.BlockChange> changes,
                                 String updatedBy, String clientId) {
        if (changes == null || changes.stream().anyMatch(c -> c == null || c.block() == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Corrections de la note illisibles");
        }
        SynthesisNoteDto saved;
        saveLock.lock();
        try {
            saved = new TransactionTemplate(transactionManager).execute(status -> doSave(baseVersion, changes, updatedBy));
        } finally {
            saveLock.unlock();
        }
        announce(clientId, saved);
        return saved;
    }

    /**
     * Copie entiere envoyee par un onglet d'avant la fusion : gardee telle quelle, puis reintegree
     * dans la note (cf. {@link #reintegrate}). La note renvoyee remplace celle de l'onglet, qui
     * affiche ainsi aussi les corrections des autres postes.
     */
    public SynthesisNoteDto keepAndReintegrate(List<ExportBlock> blocks, String updatedBy) {
        KeyHolder key = new GeneratedKeyHolder();
        String json = write(blocks);
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO synthesis_note_rejected_saves (received_at, updated_by, content) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setObject(1, now);
            ps.setString(2, updatedBy);
            ps.setString(3, json);
            return ps;
        }, key);
        SynthesisNoteDto note = reintegrate(blocks, updatedBy);
        markReintegrated(key.getKey());
        return note;
    }

    /** Copies recues avant que le serveur sache les reintegrer : reprises une fois, dans l'ordre d'arrivee. */
    @EventListener(ApplicationReadyEvent.class)
    public void reintegratePending() {
        List<Map<String, Object>> pending = jdbcTemplate.queryForList(
                "SELECT id, updated_by, content FROM synthesis_note_rejected_saves WHERE reintegrated_at IS NULL ORDER BY id");
        for (Map<String, Object> row : pending) {
            try {
                reintegrate(read((String) row.get("content")), (String) row.get("updated_by"));
                markReintegrated((Number) row.get("id"));
            } catch (RuntimeException e) {
                log.warn("Copie {} de la note de synthese non reintegree", row.get("id"), e);
            }
        }
    }

    /**
     * Reintegre la copie entiere d'un onglet d'avant la fusion. L'onglet ne dit pas de quelle
     * version il est parti : c'est la version connue qui lui ressemble le plus. Seuls les blocs
     * qu'il a modifies et qu'aucune version enregistree n'a jamais contenus sont repris : les autres
     * sont des contenus perimes que l'onglet renvoie a chaque fois, et ecraseraient les corrections
     * faites depuis par les autres postes.
     */
    public SynthesisNoteDto reintegrate(List<ExportBlock> received, String updatedBy) {
        if (received == null || received.isEmpty() || received.stream().anyMatch(Objects::isNull)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La note de synthèse ne peut pas être vide");
        }
        // Comparees telles que relues du JSON, comme les versions connues : une note fusionnee en
        // memoire avec le document genere n'est pas egale, champ pour champ, a sa copie relue.
        List<ExportBlock> blocks = read(write(received));
        SynthesisNoteDto current = new TransactionTemplate(transactionManager).execute(status -> view());
        Map<String, List<ExportBlock>> known = new LinkedHashMap<>();
        for (SynthesisNoteVersion v : synthesisNoteVersionRepository.findAll()) {
            known.put(v.getVersion(), parsedVersions.computeIfAbsent(v.getVersion(), k -> read(v.getContent())));
        }
        known.putIfAbsent(current.version(), read(write(current.blocks())));
        parsedVersions.keySet().retainAll(known.keySet());

        String baseVersion = null;
        long bestSame = -1;
        for (Map.Entry<String, List<ExportBlock>> v : known.entrySet()) {
            List<ExportBlock> doc = v.getValue();
            if (doc.size() != blocks.size()) {
                continue;
            }
            long same = java.util.stream.IntStream.range(0, doc.size()).filter(i -> doc.get(i).equals(blocks.get(i))).count();
            if (same > bestSame) {
                bestSame = same;
                baseVersion = v.getKey();
            }
        }
        if (baseVersion == null) {
            log.warn("Copie de la note de synthese ({} blocs) sans version de meme structure : gardee, non reintegree", blocks.size());
            return current;
        }
        List<ExportBlock> base = known.get(baseVersion);
        List<UpdateSynthesisNoteRequest.BlockChange> changes = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            ExportBlock block = blocks.get(i);
            int index = i;
            boolean seen = known.values().stream().anyMatch(doc -> doc.size() > index && doc.get(index).equals(block));
            if (!block.equals(base.get(i)) && !seen) {
                changes.add(new UpdateSynthesisNoteRequest.BlockChange(i, block));
            }
        }
        if (changes.isEmpty()) {
            return current;
        }
        SynthesisNoteDto saved = save(baseVersion, changes, updatedBy, null);
        log.info("Copie de la note reintegree : version de depart {}, blocs {}, conflits {}, version obtenue {}",
                baseVersion.substring(0, 8), changes.stream().map(UpdateSynthesisNoteRequest.BlockChange::index).toList(),
                saved.conflicts(), saved.version().substring(0, 8));
        return saved;
    }

    private void markReintegrated(Number id) {
        if (id != null) {
            jdbcTemplate.update("UPDATE synthesis_note_rejected_saves SET reintegrated_at = ? WHERE id = ?",
                    LocalDateTime.now(), id.longValue());
        }
    }

    /**
     * Ancien format : la note entiere remplace la version courante, bloc pour bloc. Sert le temps
     * que les onglets ouverts avant la mise en ligne de la fusion soient recharges.
     */
    public SynthesisNoteDto replace(List<ExportBlock> blocks, String updatedBy) {
        if (blocks == null || blocks.isEmpty() || blocks.stream().anyMatch(Objects::isNull)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La note de synthèse ne peut pas être vide");
        }
        SynthesisNoteDto current = view();
        if (current.blocks().size() != blocks.size()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La note a changé de structure : rechargez la page avant d'enregistrer");
        }
        List<UpdateSynthesisNoteRequest.BlockChange> changes = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            if (!blocks.get(i).equals(current.blocks().get(i))) {
                changes.add(new UpdateSynthesisNoteRequest.BlockChange(i, blocks.get(i)));
            }
        }
        return save(current.version(), changes, updatedBy, null);
    }

    private SynthesisNoteDto doSave(String baseVersion, List<UpdateSynthesisNoteRequest.BlockChange> changes,
                                    String updatedBy) {
        List<ExportBlock> generatedBlocks = generatedBlocks();
        Optional<SynthesisNoteEdit> existing = currentEdit();
        List<ExportBlock> current = existing.map(e -> withUpdates(e, generatedBlocks)).orElse(generatedBlocks);
        List<ExportBlock> base = Optional.ofNullable(baseVersion)
                .flatMap(synthesisNoteVersionRepository::findById)
                .map(v -> read(v.getContent()))
                .orElse(current);
        List<ExportBlock> ours = new ArrayList<>(base);
        for (UpdateSynthesisNoteRequest.BlockChange change : changes) {
            if (change.index() < 0 || change.index() >= ours.size()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "La note a changé de structure : rechargez la page avant d'enregistrer");
            }
            ours.set(change.index(), change.block());
        }
        SynthesisNoteMerge.Result merged = base.equals(current)
                ? new SynthesisNoteMerge.Result(ours, 0)
                : SynthesisNoteMerge.merge(base, ours, current, SynthesisNoteMerge.Winner.OURS);
        List<ExportBlock> content = merged.blocks();

        SynthesisNoteEdit edit = existing
                .orElseGet(() -> SynthesisNoteEdit.builder().id(SynthesisNoteEdit.SINGLETON_ID).build());
        String generated = write(generatedBlocks);
        edit.setContent(write(content));
        edit.setSourceContent(generated);
        edit.setSourceHash(hash(generated));
        edit.setUpdatedAt(LocalDateTime.now());
        edit.setUpdatedBy(updatedBy);
        synthesisNoteEditRepository.save(edit);
        synthesisNoteVersionRepository.deleteOlderThan(LocalDateTime.now().minusHours(24));
        return new SynthesisNoteDto(content, true, false, edit.getUpdatedAt(), updatedBy, remember(content),
                merged.conflicts());
    }

    /** Annonce l'enregistrement aux postes ouverts sur la note, qui la rechargent. */
    private void announce(String clientId, SynthesisNoteDto note) {
        Map<String, Object> event = new HashMap<>();
        event.put("clientId", clientId);
        event.put("version", note.version());
        messagingTemplate.convertAndSend(TOPIC, event);
    }

    /** Abandonne les corrections : les exports reprennent le document genere, a jour. */
    public SynthesisNoteDto reset(String clientId) {
        SynthesisNoteDto fresh;
        saveLock.lock();
        try {
            fresh = new TransactionTemplate(transactionManager).execute(status -> {
                if (synthesisNoteEditRepository.existsById(SynthesisNoteEdit.SINGLETON_ID)) {
                    synthesisNoteEditRepository.deleteById(SynthesisNoteEdit.SINGLETON_ID);
                }
                List<ExportBlock> generated = generatedBlocks();
                return new SynthesisNoteDto(generated, false, false, null, null, remember(generated), 0);
            });
        } finally {
            saveLock.unlock();
        }
        announce(clientId, fresh);
        return fresh;
    }

    /** La note telle que le canevas la produit, a partir des seules sections approuvees par le DG. */
    List<ExportBlock> generatedBlocks() {
        return generatedBlocks(null);
    }

    /** @param approvedBy si renseigne, ignore les approbations posterieures a cette date */
    List<ExportBlock> generatedBlocks(LocalDateTime approvedBy) {
        List<WorkGroup> groups = workGroupRepository.findByEnabledTrueOrderByIdAsc();
        Map<String, SectionDef> sectionsByCode = sectionDefRepository.findAllByOrderByOrderAsc().stream()
                .collect(Collectors.toMap(SectionDef::getCode, sd -> sd));
        Map<String, SectionResponse> responsesByKey = sectionResponseRepository.findAll().stream()
                .collect(Collectors.toMap(r -> key(r.getGroup().getId(), r.getSection().getId()), r -> r));
        Map<String, GroupSectionStatus> statusesByKey = groupSectionStatusRepository.findAllWithGroupAndSection().stream()
                .collect(Collectors.toMap(s -> key(s.getGroup().getId(), s.getSection().getId()), s -> s));

        // Meme regle que les autres documents qui font foi : seules les sections approuvees
        // par le DG sont resumees.
        if (approvedBy != null) {
            statusesByKey = new HashMap<>(statusesByKey);
            statusesByKey.values().removeIf(s -> s.getDgApprovedAt() != null && s.getDgApprovedAt().isAfter(approvedBy));
        }
        responsesByKey = PsdApprovedContent.approvedOnly(responsesByKey, statusesByKey);

        return psdBriefBuilder.build(groups, sectionsByCode, responsesByKey, statusesByKey, narratives());
    }

    private Optional<SynthesisNoteEdit> currentEdit() {
        return synthesisNoteEditRepository.findById(SynthesisNoteEdit.SINGLETON_ID);
    }

    /** Blocs narratifs arretes par la Direction Generale, indexes par cle ; un bloc absent vaut vide. */
    private Map<NarrativeBlockKey, String> narratives() {
        Map<NarrativeBlockKey, String> narratives = new EnumMap<>(NarrativeBlockKey.class);
        for (PsdNarrativeBlock block : psdNarrativeBlockRepository.findAll()) {
            narratives.put(block.getKey(), block.getContent() == null ? "" : block.getContent());
        }
        return narratives;
    }

    String write(List<ExportBlock> blocks) {
        try {
            return objectMapper.writerFor(BLOCKS).writeValueAsString(blocks);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Note de synthese non serialisable", e);
        }
    }

    List<ExportBlock> read(String json) {
        try {
            return objectMapper.readValue(json, BLOCKS);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Note de synthese corrigee illisible", e);
        }
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String key(Long groupId, Integer sectionId) {
        return groupId + ":" + sectionId;
    }
}
