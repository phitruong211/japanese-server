package com.japaneselearning.japanese_learning_api.deck;

import com.japaneselearning.japanese_learning_api.common.ApiException;
import com.japaneselearning.japanese_learning_api.model.*;
import com.japaneselearning.japanese_learning_api.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * The public library, abuse reports and takedowns. Publishing is "share link + listed": a published deck is an
 * ordinary shared deck that also appears in search, so saving it goes through the same copy path as any link.
 */
@Service
public class LibraryService {
    public static final int MAX_PUBLISH_PER_DAY = 5, MAX_PUBLIC_DECKS = 50, MAX_REPORTS_PER_DAY = 10, AUTO_HIDE_AFTER = 3;
    static final Set<String> LEVELS = Set.of("N5", "N4", "N3", "N2", "N1");
    static final Set<String> REASONS = Set.of("COPYRIGHT", "INAPPROPRIATE", "SPAM", "OTHER");
    private static final int PAGE_SIZE = 24;

    public record PublishRequest(boolean published, String level, String description) {}
    public record ReportRequest(String reason, String details) {}
    public record Item(String token, String name, String description, String author, String level, int cardCount, int downloads, Instant publishedAt, List<String> tags) {}
    public record Page(List<Item> items, int page, int totalPages, long total) {}
    public record ReportView(UUID id, UUID deckId, String deckToken, String deckName, String owner, String reason, String details,
                             String reporter, Instant createdAt, String status, long openReportsForDeck, String moderation) {}
    public record ReportPage(List<ReportView> reports, int page, int totalPages, long total) {}

    private final DeckRepository decks;
    private final CardRepository cards;
    private final UserRepository users;
    private final DeckReportRepository reports;
    private final DeckShareService sharing;
    private final SharedDeckContent content;
    @PersistenceContext private EntityManager em;

    public LibraryService(DeckRepository decks, CardRepository cards, UserRepository users, DeckReportRepository reports,
                          DeckShareService sharing, SharedDeckContent content) {
        this.decks = decks; this.cards = cards; this.users = users; this.reports = reports; this.sharing = sharing; this.content = content;
    }

    private static ApiException bad(String message) { return new ApiException(HttpStatus.BAD_REQUEST, message); }

    // ---------- publishing ----------
    @Transactional
    public DeckShareService.Link publish(UUID userId, UUID deckId, PublishRequest request) {
        content.lockOwners(userId);
        Deck deck = decks.findByIdAndOwnerIdAndDeletedAtIsNull(deckId, userId).orElseThrow(() -> ApiException.notFound("Không tìm thấy bộ thẻ"));
        em.refresh(deck, LockModeType.PESSIMISTIC_WRITE);
        if (deck.getSourceType() != DomainTypes.SourceType.IMPORT) throw bad("Chỉ đăng bộ thẻ tự import");
        sharing.requireNotRemoved(deck);
        if (!request.published()) {
            deck.setVisibility(DomainTypes.Visibility.PRIVATE);
            deck.setPublishedAt(null);
            return sharing.link(deck);
        }
        String level = request.level() == null || request.level().isBlank() ? null : request.level().trim().toUpperCase(Locale.ROOT);
        if (level != null && !LEVELS.contains(level)) throw bad("Cấp độ phải là N5, N4, N3, N2 hoặc N1");
        String description = request.description() == null ? "" : request.description().trim();
        if (description.length() > 500) throw bad("Mô tả tối đa 500 ký tự");
        if (cards.countByDeckIdAndDeletedAtIsNull(deckId) < 1) throw bad("Bộ thẻ cần ít nhất một thẻ để đăng");
        boolean already = deck.getVisibility() == DomainTypes.Visibility.PUBLIC;
        if (!already) {
            if (decks.countByOwnerIdAndVisibilityAndDeletedAtIsNull(userId, DomainTypes.Visibility.PUBLIC) >= MAX_PUBLIC_DECKS)
                throw ApiException.conflict("Bạn đang công khai tối đa " + MAX_PUBLIC_DECKS + " bộ. Hãy gỡ bớt trước khi đăng thêm.");
            if (decks.countByOwnerIdAndPublishedAtAfter(userId, Instant.now().minus(1, ChronoUnit.DAYS)) >= MAX_PUBLISH_PER_DAY)
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Mỗi ngày chỉ đăng được " + MAX_PUBLISH_PER_DAY + " bộ. Hãy thử lại sau.");
            deck.setPublishedAt(Instant.now());
        }
        sharing.ensureShare(deck);
        deck.setShareExpiresAt(null);
        deck.setVisibility(DomainTypes.Visibility.PUBLIC);
        deck.setLibraryLevel(level);
        deck.setDescription(description.isEmpty() ? null : description);
        return sharing.link(deck);
    }

    // ---------- browsing (public) ----------
    @Transactional(readOnly = true)
    public Page list(String query, String level, String sort, int page) {
        if (page < 0) throw bad("Trang không hợp lệ");
        String wanted = level == null ? "" : level.trim().toUpperCase(Locale.ROOT);
        if (!wanted.isEmpty() && !LEVELS.contains(wanted)) throw bad("Cấp độ không hợp lệ");
        String text = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (text.length() > 100) throw bad("Từ khóa tối đa 100 ký tự");
        String pattern = "%" + text.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        Sort order = "popular".equals(sort) ? Sort.by(Sort.Order.desc("downloadCount"), Sort.Order.desc("publishedAt"), Sort.Order.asc("id"))
                : Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.asc("id"));
        var result = decks.searchLibrary(wanted, pattern, PageRequest.of(page, PAGE_SIZE, order));
        var items = result.getContent().stream().map(d -> new Item(d.getShareToken(), d.getName(), d.getDescription(), d.getOwner().getDisplayName(),
                d.getLibraryLevel(), d.getCardCount(), d.getDownloadCount(), d.getPublishedAt(), d.getTags() == null ? List.of() : d.getTags())).toList();
        return new Page(items, page, result.getTotalPages(), result.getTotalElements());
    }

    // ---------- reports ----------
    @Transactional
    public void report(UUID userId, String token, ReportRequest request) {
        if (token == null || !token.matches("[0-9a-fA-F-]{36}")) throw ApiException.notFound("Liên kết không tồn tại hoặc đã ngừng chia sẻ.");
        Deck deck = decks.findByShareTokenAndDeletedAtIsNull(token).orElseThrow(() -> ApiException.notFound("Liên kết không tồn tại hoặc đã ngừng chia sẻ."));
        String reason = request.reason() == null ? "" : request.reason().trim().toUpperCase(Locale.ROOT);
        if (!REASONS.contains(reason)) throw bad("Lý do báo cáo không hợp lệ");
        String details = request.details() == null ? null : request.details().trim();
        if (details != null && details.length() > 500) throw bad("Chi tiết tối đa 500 ký tự");
        if (reason.equals("OTHER") && (details == null || details.length() < 5)) throw bad("Hãy mô tả vấn đề (ít nhất 5 ký tự)");
        if (deck.getOwner().getId().equals(userId)) throw bad("Bạn không thể báo cáo bộ thẻ của chính mình");
        // Serialize this user's reports so the daily cap and the once-per-deck rule hold under concurrent requests.
        content.lockOwners(userId);
        if (reports.existsByDeckIdAndReporterId(deck.getId(), userId)) throw ApiException.conflict("Bạn đã báo cáo bộ thẻ này rồi");
        if (reports.countByReporterIdAndCreatedAtAfter(userId, Instant.now().minus(1, ChronoUnit.DAYS)) >= MAX_REPORTS_PER_DAY)
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Hôm nay bạn đã gửi đủ " + MAX_REPORTS_PER_DAY + " báo cáo.");
        var report = new DeckReport();
        report.setId(UUID.randomUUID());
        report.setDeckId(deck.getId());
        report.setReporterId(userId);
        report.setReason(reason);
        report.setDetails(details == null || details.isEmpty() ? null : details);
        report.setCreatedAt(Instant.now());
        reports.saveAndFlush(report);
        // Repeated reports hide the deck from the library until a moderator looks; the link itself keeps working.
        if ("ACTIVE".equals(deck.getModerationStatus()) && reports.countByDeckIdAndStatus(deck.getId(), DeckReport.OPEN) >= AUTO_HIDE_AFTER)
            deck.setModerationStatus("UNDER_REVIEW");
    }

    // ---------- moderation (admin) ----------
    private void requireAdmin(UUID userId) {
        var user = users.findById(userId).orElseThrow(() -> ApiException.unauthorized("Bạn cần đăng nhập"));
        if (user.getRole() != DomainTypes.Role.ADMIN) throw new ApiException(HttpStatus.FORBIDDEN, "Chỉ quản trị viên được thực hiện thao tác này");
    }

    @Transactional(readOnly = true)
    public ReportPage listReports(UUID adminId, String status, int page) {
        requireAdmin(adminId);
        String wanted = status == null || status.isBlank() ? DeckReport.OPEN : status.toUpperCase(Locale.ROOT);
        if (!Set.of(DeckReport.OPEN, DeckReport.DISMISSED, DeckReport.ACTIONED).contains(wanted)) throw bad("Trạng thái không hợp lệ");
        var result = reports.findAllByStatusOrderByCreatedAtAsc(wanted, PageRequest.of(Math.max(0, page), 30));
        var views = result.getContent().stream().map(r -> {
            Deck deck = em.find(Deck.class, r.getDeckId());
            var reporter = users.findById(r.getReporterId());
            return new ReportView(r.getId(), r.getDeckId(), deck == null ? null : deck.getShareToken(), deck == null ? "(đã xóa)" : deck.getName(),
                    deck == null ? "" : deck.getOwner().getDisplayName(), r.getReason(), r.getDetails(), reporter.map(User::getDisplayName).orElse("(đã xóa)"),
                    r.getCreatedAt(), r.getStatus(), reports.countByDeckIdAndStatus(r.getDeckId(), DeckReport.OPEN), deck == null ? null : deck.getModerationStatus());
        }).toList();
        return new ReportPage(views, result.getNumber(), result.getTotalPages(), result.getTotalElements());
    }

    @Transactional
    public void dismiss(UUID adminId, UUID reportId) {
        requireAdmin(adminId);
        var report = reports.findById(reportId).orElseThrow(() -> ApiException.notFound("Không tìm thấy báo cáo"));
        close(report, DeckReport.DISMISSED, adminId);
        reports.flush();
        Deck deck = em.find(Deck.class, report.getDeckId());
        if (deck != null && "UNDER_REVIEW".equals(deck.getModerationStatus()) && reports.countByDeckIdAndStatus(deck.getId(), DeckReport.OPEN) < AUTO_HIDE_AFTER)
            deck.setModerationStatus("ACTIVE");
    }

    /** Takes a deck down: links and code die, it leaves the library, and the owner sees the reason. Existing copies are theirs and stay. */
    @Transactional
    public void remove(UUID adminId, UUID deckId, String reason) {
        requireAdmin(adminId);
        String why = reason == null ? "" : reason.trim();
        if (why.isEmpty() || why.length() > 500) throw bad("Cần ghi lý do gỡ (tối đa 500 ký tự)");
        Deck deck = em.find(Deck.class, deckId, LockModeType.PESSIMISTIC_WRITE);
        if (deck == null) throw ApiException.notFound("Không tìm thấy bộ thẻ");
        deck.setModerationStatus("REMOVED");
        deck.setRemovedReason(why);
        deck.setRemovedAt(Instant.now());
        deck.setVisibility(DomainTypes.Visibility.PRIVATE);
        deck.setPublishedAt(null);
        deck.setShareToken(null);
        deck.setShareCode(null);
        deck.setShareExpiresAt(null);
        deck.setSharePersonalNotes(false);
        reports.findAllByDeckIdAndStatus(deckId, DeckReport.OPEN).forEach(r -> close(r, DeckReport.ACTIONED, adminId));
    }

    @Transactional
    public void restore(UUID adminId, UUID deckId) {
        requireAdmin(adminId);
        Deck deck = em.find(Deck.class, deckId, LockModeType.PESSIMISTIC_WRITE);
        if (deck == null) throw ApiException.notFound("Không tìm thấy bộ thẻ");
        deck.setModerationStatus("ACTIVE");
        deck.setRemovedReason(null);
        deck.setRemovedAt(null);
    }

    private void close(DeckReport report, String status, UUID adminId) {
        report.setStatus(status);
        report.setHandledAt(Instant.now());
        report.setHandledBy(adminId);
    }
}
