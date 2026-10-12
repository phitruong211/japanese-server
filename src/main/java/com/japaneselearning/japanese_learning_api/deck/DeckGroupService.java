package com.japaneselearning.japanese_learning_api.deck;

import com.japaneselearning.japanese_learning_api.common.ApiException;
import com.japaneselearning.japanese_learning_api.model.*;
import com.japaneselearning.japanese_learning_api.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.util.*;

@Service
@Transactional
public class DeckGroupService {
    private final DeckGroupRepository groups;
    private final DeckRepository decks;
    private final DeckService deckService;
    @PersistenceContext private EntityManager em;
    public DeckGroupService(DeckGroupRepository groups, DeckRepository decks, DeckService deckService) {
        this.groups = groups; this.decks = decks; this.deckService = deckService;
    }
    public record NameRequest(@NotBlank @Size(max=200) String name, Long version) {}
    public record MoveRequest(UUID groupId, UUID beforeId, @NotNull Long version) {}
    public record OrderRequest(@NotNull List<@NotNull UUID> ids) {}
    public record Summary(UUID id, String name, int position, long version, long deckCount, long cardCount, long dueCount) {}
    private void lock(UUID owner) {
        if (em.find(User.class, owner, LockModeType.PESSIMISTIC_WRITE) == null) throw ApiException.notFound("Không tìm thấy tài khoản");
    }
    private DeckGroup owned(UUID owner, UUID id) {
        return groups.findByIdAndOwnerId(id, owner).orElseThrow(() -> ApiException.notFound("Không tìm thấy nhóm"));
    }
    private void version(long actual, Long expected) {
        if (expected == null || actual != expected) throw new ApiException(HttpStatus.CONFLICT, "Dữ liệu đã thay đổi. Hãy tải lại và thử lại.");
    }
    @Transactional(readOnly=true)
    public List<Summary> list(UUID owner) {
        var counts = new HashMap<UUID, long[]>();
        for (var deck : deckService.list(owner)) {
            if (deck.groupId() == null) continue;
            var c = counts.computeIfAbsent(deck.groupId(), id -> new long[3]);
            c[0]++; c[1] += deck.cardCount(); c[2] += deck.dueCount();
        }
        return groups.findAllByOwnerIdOrderByPositionAscIdAsc(owner).stream().map(g -> {
            var c = counts.getOrDefault(g.getId(), new long[3]);
            return new Summary(g.getId(), g.getName(), g.getPosition(), g.getVersion(), c[0], c[1], c[2]);
        }).toList();
    }
    public void create(UUID owner, NameRequest request) {
        lock(owner);
        var group = new DeckGroup(); group.setId(UUID.randomUUID());
        group.setOwner(em.getReference(User.class, owner)); group.setName(request.name().strip());
        group.setPosition(groups.findAllByOwnerIdOrderByPositionAscIdAsc(owner).size());
        em.persist(group);
    }
    public void rename(UUID owner, UUID id, NameRequest request) {
        lock(owner); var group = owned(owner, id); version(group.getVersion(), request.version());
        group.setName(request.name().strip());
    }
    public void dissolve(UUID owner, UUID id, long expected) {
        lock(owner); var group = owned(owner, id); version(group.getVersion(), expected);
        var all = decks.findAllByOwnerIdAndDeletedAtIsNullOrderByPositionAscUpdatedAtDesc(owner);
        int next = all.stream().filter(d -> d.getGroupId() == null).mapToInt(Deck::getPosition).max().orElse(-1) + 1;
        for (var deck : all) if (id.equals(deck.getGroupId())) { deck.setGroupId(null); deck.setPosition(next++); }
        // Clear references from soft-deleted decks as well before removing the group.
        em.flush();
        em.createQuery("update Deck d set d.groupId = null where d.groupId = :id and d.owner.id = :owner and d.deletedAt is not null")
            .setParameter("id", id).setParameter("owner", owner).executeUpdate();
        groups.delete(group);
    }
    public void move(UUID owner, UUID id, MoveRequest request) {
        lock(owner);
        var deck = decks.findByIdAndOwnerIdAndDeletedAtIsNull(id, owner).orElseThrow(() -> ApiException.notFound("Không tìm thấy bộ thẻ"));
        version(deck.getVersion(), request.version());
        if (deck.getSourceType() == DomainTypes.SourceType.SYSTEM || "BUILT_IN".equals(deck.getSource()))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Bộ có sẵn không thể chuyển nhóm");
        if (request.groupId() != null) owned(owner, request.groupId());
        var target = new ArrayList<>(decks.findAllByOwnerIdAndDeletedAtIsNullOrderByPositionAscUpdatedAtDesc(owner)
                .stream().filter(d -> Objects.equals(d.getGroupId(), request.groupId()) && !d.getId().equals(id)).toList());
        int index = target.size();
        if (request.beforeId() != null) {
            index = -1;
            for (int i=0;i<target.size();i++) if (target.get(i).getId().equals(request.beforeId())) index=i;
            if (index < 0) throw new ApiException(HttpStatus.CONFLICT, "Vị trí đã thay đổi. Hãy thử lại.");
        }
        deck.setGroupId(request.groupId()); target.add(index, deck);
        for (int i=0;i<target.size();i++) target.get(i).setPosition(i);
    }
    public void reorder(UUID owner, OrderRequest request) {
        lock(owner); var all = groups.findAllByOwnerIdOrderByPositionAscIdAsc(owner);
        var ids = new HashSet<>(request.ids());
        if (ids.size() != request.ids().size() || !ids.equals(new HashSet<>(all.stream().map(DeckGroup::getId).toList())))
            throw new ApiException(HttpStatus.CONFLICT, "Danh sách nhóm đã thay đổi. Hãy thử lại.");
        for (var group : all) group.setPosition(request.ids().indexOf(group.getId()));
    }
}
