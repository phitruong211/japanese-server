package com.japaneselearning.japanese_learning_api.learning;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
public final class LearningDtos {
 public record BuiltInReview(@NotBlank @Size(max=200) String cardId,
      @NotNull @Pattern(regexp="vocabulary|kanji|grammar") String deckType,
      @NotNull @Valid com.japaneselearning.japanese_learning_api.anki.AnkiDtos.ReviewRequest review) {}
 public record BuiltInReviewBatch(@NotNull @Size(min=1,max=20) List<@Valid BuiltInReview> reviews) {}
 public record BuiltInReviewBatchResult(Snapshot state, List<SrsCard> cards) {}
 public record BuiltInReviewResult(Snapshot state, SrsCard card) {}
 public record Bookmark(@NotBlank @Size(max=200) String itemId, @Pattern(regexp="vocabulary|kanji|grammar") @NotNull String itemType, @NotBlank @Size(max=50) String createdAt, @Size(max=5000) String note) {}
 public record SrsCard(@NotBlank @Size(max=200) String cardId, @NotNull @Pattern(regexp="vocabulary|kanji|grammar") String deckType, @NotNull @Pattern(regexp="new|learning|review|relearning") String state, @DecimalMin("1.0") @DecimalMax("10.0") double easeFactor, @Min(0) @Max(100000000) Double intervalMinutes, @Min(0) @Max(1000000) Double intervalDays, @NotBlank @Size(max=50) String dueDate, @Min(0) int reps, @Min(0) int lapses, @Size(max=50) String lastReviewedAt,
                       @Pattern(regexp="fsrs-6") String algorithm, @DecimalMin("0.001") Double stability,
                       @DecimalMin("1.0") @DecimalMax("10.0") Double difficulty,
                       @Min(0) Integer elapsedDays, @Min(0) Integer scheduledDays, @Min(0) Integer learningSteps,
                       @Size(max=50) String firstReviewedAt, @Pattern(regexp="again|hard|good|easy") String lastRating) {
  public SrsCard(String cardId,String deckType,String state,double easeFactor,Double intervalMinutes,Double intervalDays,String dueDate,int reps,int lapses,String lastReviewedAt){
   this(cardId,deckType,state,easeFactor,intervalMinutes,intervalDays,dueDate,reps,lapses,lastReviewedAt,null,null,null,null,null,null,null,null);
  }
 }
 public record StudyDay(@NotNull @Pattern(regexp="\\d{4}-\\d{2}-\\d{2}") String date, @Min(0) @Max(10000000) int cardsReviewed, @Min(0) Integer flashcardReviewed, @Min(0) Integer srsReviewed, @Min(0) int newCardsLearned, @DecimalMin("0") @DecimalMax("100") double accuracy, @DecimalMin("0") @DecimalMax("10000000") double timeSpent, @Min(0) @Max(10000000) Integer flashcardViewed) {
  public StudyDay(String date,int cardsReviewed,Integer flashcardReviewed,Integer srsReviewed,int newCardsLearned,double accuracy,double timeSpent){
   this(date,cardsReviewed,flashcardReviewed,srsReviewed,newCardsLearned,accuracy,timeSpent,null);
  }
 }
 public record Snapshot(long revision, List<Bookmark> bookmarks, List<SrsCard> srsCards, List<StudyDay> studyDays) {}
 public record Update(@Min(0) long revision, @NotNull @Size(max=50000) List<@Valid Bookmark> bookmarks, @NotNull @Size(max=50000) List<@Valid SrsCard> srsCards, @NotNull @Size(max=20000) List<@Valid StudyDay> studyDays) {}
 public record Guest(@NotBlank @Size(max=100) String idempotencyKey, @NotNull @Size(max=50000) List<@Valid Bookmark> bookmarks, @NotNull @Size(max=50000) List<@Valid SrsCard> srsCards, @NotNull @Size(max=20000) List<@Valid StudyDay> studyDays) {}
 public record Result(Snapshot state, int bookmarksCreated, int srsCreated, int srsSkipped, int activitiesMerged) {}
}
