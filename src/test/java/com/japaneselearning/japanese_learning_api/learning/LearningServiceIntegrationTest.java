package com.japaneselearning.japanese_learning_api.learning;
import com.japaneselearning.japanese_learning_api.auth.*;
import com.japaneselearning.japanese_learning_api.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.japaneselearning.japanese_learning_api.learning.LearningDtos.*;
@SpringBootTest(properties = {
 "spring.config.import=",
 "spring.datasource.url=jdbc:h2:mem:learning_tests;MODE=MySQL;DB_CLOSE_DELAY=-1",
 "spring.datasource.driver-class-name=org.h2.Driver",
 "spring.datasource.username=sa", "spring.datasource.password=",
 "spring.jpa.properties.hibernate.generate_statistics=true",
 "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false"
}) @Transactional
class LearningServiceIntegrationTest {
 @Autowired jakarta.persistence.EntityManager em;
 @Autowired LearningService service; @Autowired AuthService auth;
 UUID owner(){return auth.register(new AuthDtos.RegisterRequest("learn-"+UUID.randomUUID()+"@example.com","TestPassword123!","Learning","test")).user().id();}
 Bookmark bookmark(){return new Bookmark("vocab-n3-1","vocabulary","2026-09-26T00:00:00Z",null);}
 SrsCard card(int reps){return new SrsCard("vocab-n3-1","vocabulary","review",2.5,1440.0,null,"2026-09-27T00:00:00Z",reps,0,null);}
 SrsCard fsrsCard(){return new SrsCard("kanji-n4-1","kanji","review",2.5,null,3.0,"2026-10-13T00:00:00Z",4,1,"2026-10-10T00:00:00Z","fsrs-6",3.25,5.1,1,3,0,"2026-10-01T00:00:00Z","good");}
 StudyDay day(int count){return new StudyDay("2026-09-26",count,count,0,count,80,2);}
 @Test void migrationIsIdempotentAndAccountProgressWins(){
  UUID user=owner();service.put(user,new Update(0,List.of(),List.of(card(8)),List.of(day(2))));
  Guest guest=new Guest("migration-one",List.of(bookmark()),List.of(card(1)),List.of(day(3)));
  Result first=service.migrate(user,guest);Result second=service.migrate(user,guest);
  assertThat(first.bookmarksCreated()).isEqualTo(1);assertThat(first.srsSkipped()).isEqualTo(1);
  assertThat(second.state().revision()).isEqualTo(first.state().revision());
  assertThat(second.state().srsCards().getFirst().reps()).isEqualTo(8);
  assertThat(second.state().studyDays().getFirst().cardsReviewed()).isEqualTo(5);
  assertThatThrownBy(()->service.migrate(user,new Guest("migration-one",List.of(),List.of(),List.of()))).isInstanceOf(ApiException.class);
 }
 @Test void revisionPreventsStaleDeletesAndOwnershipIsolated(){
  UUID a=owner(),b=owner();Snapshot saved=service.put(a,new Update(0,List.of(bookmark()),List.of(card(1)),List.of()));
  assertThat(service.get(b).bookmarks()).isEmpty();
  assertThatThrownBy(()->service.put(a,new Update(0,List.of(),List.of(),List.of()))).isInstanceOf(ApiException.class);
  service.put(a,new Update(saved.revision(),List.of(),List.of(),List.of()));
  assertThat(service.get(a).bookmarks()).isEmpty();assertThat(service.get(a).srsCards()).isEmpty();
 }
 @Test void invalidDatesAndDuplicateStableKeysDoNotWrite(){
  UUID user=owner();
  assertThatThrownBy(()->service.put(user,new Update(0,List.of(bookmark(),bookmark()),List.of(),List.of()))).isInstanceOf(ApiException.class);
  assertThatThrownBy(()->service.put(user,new Update(0,List.of(),List.of(),List.of(new StudyDay("2026-02-30",1,1,0,1,80,2))))).isInstanceOf(ApiException.class);
  assertThat(service.get(user).revision()).isZero();
 }
 @Test void canonicalFsrsStateRoundTripsWithoutLosingSchedulerFields(){
  UUID user=owner();
  Snapshot saved=service.put(user,new Update(0,List.of(),List.of(fsrsCard()),List.of()));
  assertThat(saved.srsCards()).containsExactly(fsrsCard());
  assertThat(service.get(user).srsCards()).containsExactly(fsrsCard());
 }
 @Test void builtInReviewsAreAuthoritativeIdempotentAndRejectStaleState(){
  UUID user=owner(),operation=UUID.randomUUID();
  var rating=com.japaneselearning.japanese_learning_api.model.DomainTypes.AnkiRating.GOOD;
  var request=new com.japaneselearning.japanese_learning_api.anki.AnkiDtos.ReviewRequest(operation,rating,null,0,0,false,null,null,null,0);
  var review=new BuiltInReview("vocab-n3-1","vocabulary",request);
  var first=service.review(user,review);
  var retry=service.review(user,review);
  assertThat(first.card().reps()).isEqualTo(1);
  assertThat(retry.card()).isEqualTo(first.card());
  assertThat(retry.state().revision()).isEqualTo(first.state().revision());
  assertThat(service.get(user).srsCards()).containsExactly(first.card());
  var stale=new com.japaneselearning.japanese_learning_api.anki.AnkiDtos.ReviewRequest(UUID.randomUUID(),rating,null,null,null,null,null,null,null,0);
  assertThatThrownBy(()->service.review(user,new BuiltInReview("vocab-n3-1","vocabulary",stale))).isInstanceOf(ApiException.class);
 }
 @Test @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
 void concurrentSessionsCannotApplyTwoRatingsToOneVersion() throws Exception {
  UUID user=owner();
  var gate=new java.util.concurrent.CountDownLatch(1);
  try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
   java.util.concurrent.Callable<Boolean> task=()->{
    gate.await();
    var req=new com.japaneselearning.japanese_learning_api.anki.AnkiDtos.ReviewRequest(UUID.randomUUID(),
      com.japaneselearning.japanese_learning_api.model.DomainTypes.AnkiRating.GOOD,null,null,null,null,null,null,null,0);
    try { service.review(user,new BuiltInReview("concurrent","kanji",req));return true; }
    catch(ApiException conflict){assertThat(conflict.status().value()).isEqualTo(409);return false;}
   };
   var a=executor.submit(task);var b=executor.submit(task);gate.countDown();
   assertThat(List.of(a.get(20,java.util.concurrent.TimeUnit.SECONDS),b.get(20,java.util.concurrent.TimeUnit.SECONDS)))
      .containsExactlyInAnyOrder(true,false);
  }
  assertThat(service.get(user).srsCards().getFirst().reps()).isEqualTo(1);
 }
 @Test void batchKeepsOrderAndDeduplicatesRetriesInsideAndAcrossRequests() {
  UUID user=owner();
  var rating=com.japaneselearning.japanese_learning_api.model.DomainTypes.AnkiRating.GOOD;
  var first=new BuiltInReview("batch-card","vocabulary",new com.japaneselearning.japanese_learning_api.anki.AnkiDtos.ReviewRequest(UUID.randomUUID(),rating,null,null,null,null,null,null,null,0));
  var second=new BuiltInReview("batch-card","vocabulary",new com.japaneselearning.japanese_learning_api.anki.AnkiDtos.ReviewRequest(UUID.randomUUID(),rating,null,null,null,null,null,null,null,1));
  var request=new BuiltInReviewBatch(List.of(first,first,second));
  var saved=service.reviewBatch(user,request);
  assertThat(saved.cards()).extracting(SrsCard::reps).containsExactly(1,1,2);
  assertThat(saved.state().revision()).isEqualTo(2);
  var retry=service.reviewBatch(user,request);
  assertThat(retry.cards()).isEqualTo(saved.cards());
  assertThat(retry.state().revision()).isEqualTo(2);
  assertThat(service.get(user).srsCards().getFirst().reps()).isEqualTo(2);
 }
 @Test @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
 void conflictingBatchRollsBackAllReviews() {
  UUID user=owner();
  var rating=com.japaneselearning.japanese_learning_api.model.DomainTypes.AnkiRating.GOOD;
  var first=new BuiltInReview("atomic-card","vocabulary",new com.japaneselearning.japanese_learning_api.anki.AnkiDtos.ReviewRequest(UUID.randomUUID(),rating,null,null,null,null,null,null,null,0));
  var stale=new BuiltInReview("atomic-card","vocabulary",new com.japaneselearning.japanese_learning_api.anki.AnkiDtos.ReviewRequest(UUID.randomUUID(),rating,null,null,null,null,null,null,null,0));
  assertThatThrownBy(()->service.reviewBatch(user,new BuiltInReviewBatch(List.of(first,stale)))).isInstanceOf(ApiException.class);
  assertThat(service.get(user).srsCards()).isEmpty();
  assertThat(service.reviewBatch(user,new BuiltInReviewBatch(List.of(first))).cards().getFirst().reps()).isEqualTo(1);
 }
 @Test void twentyReviewsWriteTheLearningSnapshotOnlyOnce() {
  UUID user=owner();service.get(user);em.flush();
  var statistics=em.getEntityManagerFactory().unwrap(org.hibernate.SessionFactory.class).getStatistics();
  statistics.clear();
  var requests=new ArrayList<BuiltInReview>();
  for(int i=0;i<20;i++) requests.add(new BuiltInReview("batch-"+i,"vocabulary",new com.japaneselearning.japanese_learning_api.anki.AnkiDtos.ReviewRequest(UUID.randomUUID(),com.japaneselearning.japanese_learning_api.model.DomainTypes.AnkiRating.GOOD,null,null,null,null,null,null,null,0)));
  var result=service.reviewBatch(user,new BuiltInReviewBatch(requests));
  assertThat(result.cards()).hasSize(20);
  assertThat(statistics.getEntityUpdateCount()).isEqualTo(1);
  assertThat(statistics.getEntityInsertCount()).isEqualTo(20);
 }
}
