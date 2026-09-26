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
@SpringBootTest @Transactional
class LearningServiceIntegrationTest {
 @Autowired LearningService service; @Autowired AuthService auth;
 UUID owner(){return auth.register(new AuthDtos.RegisterRequest("learn-"+UUID.randomUUID()+"@example.com","TestPassword123!","Learning","test")).user().id();}
 Bookmark bookmark(){return new Bookmark("vocab-n3-1","vocabulary","2026-09-26T00:00:00Z",null);}
 SrsCard card(int reps){return new SrsCard("vocab-n3-1","vocabulary","review",2.5,1440.0,null,"2026-09-27T00:00:00Z",reps,0,null);}
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
}
