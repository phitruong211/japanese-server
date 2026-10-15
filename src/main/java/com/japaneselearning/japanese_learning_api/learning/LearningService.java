package com.japaneselearning.japanese_learning_api.learning;
import com.japaneselearning.japanese_learning_api.common.ApiException;
import com.japaneselearning.japanese_learning_api.model.User;
import jakarta.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.function.Function;
import java.time.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import static com.japaneselearning.japanese_learning_api.learning.LearningDtos.*;
import com.japaneselearning.japanese_learning_api.anki.AnkiScheduler;
import com.japaneselearning.japanese_learning_api.anki.ReviewTiming;
import com.japaneselearning.japanese_learning_api.anki.ReviewSchedulingTime;
import com.japaneselearning.japanese_learning_api.model.UserSettings;
import com.japaneselearning.japanese_learning_api.repository.UserSettingsRepository;
@Service @Transactional
public class LearningService {
 private final EntityManager em; private final ObjectMapper json;
 private final AnkiScheduler scheduler;
 private final UserSettingsRepository settings;
 public LearningService(EntityManager em,ObjectMapper json,AnkiScheduler scheduler,UserSettingsRepository settings){this.em=em;this.json=json;this.scheduler=scheduler;this.settings=settings;}
 private LearningState locked(UUID owner){
  if(em.find(User.class,owner,LockModeType.PESSIMISTIC_WRITE)==null) throw ApiException.unauthorized("Tài khoản không tồn tại");
  LearningState row=em.find(LearningState.class,owner);
  if(row==null){row=new LearningState();row.setUserId(owner);row.setRevision(0);row.setPayload(json.writeValueAsString(new Snapshot(0,List.of(),List.of(),List.of())));em.persist(row);}
  return row;
 }
 private Snapshot read(LearningState row){Snapshot s=json.readValue(row.getPayload(),Snapshot.class);return new Snapshot(row.getRevision(),s.bookmarks(),s.srsCards(),s.studyDays());}
 private Snapshot save(LearningState row,List<Bookmark>b,List<SrsCard>s,List<StudyDay>d){
  Snapshot snapshot=new Snapshot(row.getRevision()+1,b,s,d);row.setRevision(snapshot.revision());row.setPayload(json.writeValueAsString(snapshot));return snapshot;
 }
 public Snapshot get(UUID owner){return read(locked(owner));}
 public BuiltInReviewResult review(UUID owner, BuiltInReview req) {
  LearningState row=locked(owner);
  return reviewOne(owner,req,row,read(row),false);
 }
 public BuiltInReviewBatchResult reviewBatch(UUID owner, BuiltInReviewBatch batch) {
  LearningState row=locked(owner);
  Snapshot current=read(row);
  var results=new ArrayList<SrsCard>();
  var handled=new HashMap<UUID,BuiltInReview>();
  var completed=new HashMap<UUID,SrsCard>();
  // Queries for receipts must not flush the growing JSON snapshot between reviews.
  FlushModeType previousFlush=em.getFlushMode();
  em.setFlushMode(FlushModeType.COMMIT);
  try {
   for(var req:batch.reviews()) {
    UUID id=req.review().clientReviewId();
    if(id!=null && handled.containsKey(id)) {
     if(!handled.get(id).equals(req)) throw ApiException.conflict("Mã lượt ôn đã được dùng cho đánh giá khác");
     results.add(completed.get(id));
     continue;
    }
    var result=reviewOne(owner,req,row,current,true);
    current=result.state();results.add(result.card());
    handled.put(id,req);completed.put(id,result.card());
   }
   if(current.revision()!=row.getRevision()) {
    row.setRevision(current.revision());row.setPayload(json.writeValueAsString(current));
   }
   em.flush(); // Persist receipts and the final snapshot together, once per batch.
   return new BuiltInReviewBatchResult(current,results);
  } finally {em.setFlushMode(previousFlush);}
 }
 private BuiltInReviewResult reviewOne(UUID owner, BuiltInReview req, LearningState row, Snapshot previous, boolean deferred) {
  var request = req.review();
  if(request.clientReviewId()==null || request.expectedRepetitions()==null)
   throw new ApiException(HttpStatus.BAD_REQUEST,"Thiếu mã lượt ôn hoặc phiên bản thẻ");
  var prior=em.createQuery("select r from BuiltInReviewLog r where r.userId=:owner and r.requestId=:key",BuiltInReviewLog.class)
    .setParameter("owner",owner).setParameter("key",request.clientReviewId()).getResultList();
  if(!prior.isEmpty()) {
   var log=prior.getFirst();
   if(!log.getCardId().equals(req.cardId()) || !log.getDeckType().equals(req.deckType()) || !log.getRating().equals(request.rating().name()))
    throw ApiException.conflict("Mã lượt ôn đã được dùng cho thẻ hoặc đánh giá khác");
   return new BuiltInReviewResult(previous,json.readValue(log.getResultCard(),SrsCard.class));
  }
  var cardMap=index(previous.srsCards(),x->x.deckType()+":"+x.cardId());
  String key=req.deckType()+":"+req.cardId();
  Instant now=Instant.now();
  var progress=BuiltInSchedulerAdapter.read(cardMap.get(key),now);
  now=ReviewSchedulingTime.resolve(request.ratedAt(),progress.getLastReviewedAt(),now);
  if(progress.getRepetitions()!=request.expectedRepetitions()) throw ApiException.conflict("Thẻ đã thay đổi. Hãy mở lại phiên học.");
  var log=new BuiltInReviewLog();
  log.setId(UUID.randomUUID());log.setUserId(owner);log.setRequestId(request.clientReviewId());
  log.setCardId(req.cardId());log.setDeckType(req.deckType());log.setRating(request.rating().name());log.setReviewedAt(now);
  log.setPredictedRecall(scheduler.predictedRecall(progress,now));
  log.setElapsedDays(progress.getLastReviewedAt()==null?null:Math.max(0,Duration.between(progress.getLastReviewedAt(),now).toMillis()/86400000.0));
  log.setTimeValid(ReviewTiming.isValid(request));log.setTelemetry(json.writeValueAsString(request));log.setSchedulerVersion(AnkiScheduler.VERSION);
  scheduler.apply(progress,request.rating(),now,null,settings.findById(owner).orElseGet(UserSettings::new),true);
  SrsCard result=BuiltInSchedulerAdapter.write(req.cardId(),req.deckType(),progress);
  cardMap.put(key,result);
  Snapshot saved=deferred
   ? new Snapshot(previous.revision()+1,previous.bookmarks(),new ArrayList<>(cardMap.values()),previous.studyDays())
   : save(row,previous.bookmarks(),new ArrayList<>(cardMap.values()),previous.studyDays());
  log.setResultCard(json.writeValueAsString(result));em.persist(log);
  return new BuiltInReviewResult(saved,result);
 }
 public Snapshot put(UUID owner,Update req){
  validate(req.bookmarks(),req.srsCards(),req.studyDays());
  LearningState row=locked(owner);
  if(row.getRevision()!=req.revision())throw ApiException.conflict("Tiến độ đã thay đổi trên thiết bị khác. Tải lại trước khi lưu.");
  return save(row,req.bookmarks(),req.srsCards(),req.studyDays());
 }
 public Result migrate(UUID owner,Guest req){
  validate(req.bookmarks(),req.srsCards(),req.studyDays());
  LearningState row=locked(owner);
  String fingerprint=hash(json.writeValueAsString(req));
  List<LearningReceipt> prior=em.createQuery("select r from LearningReceipt r where r.userId=:owner and r.requestKey=:key",LearningReceipt.class).setParameter("owner",owner).setParameter("key",req.idempotencyKey()).getResultList();
  if(!prior.isEmpty()){
   if(!prior.getFirst().getFingerprint().equals(fingerprint))throw ApiException.conflict("Khóa chuyển dữ liệu đã dùng cho nội dung khác");
   Result saved=json.readValue(prior.getFirst().getPayload(),Result.class);
   return new Result(read(row),saved.bookmarksCreated(),saved.srsCreated(),saved.srsSkipped(),saved.activitiesMerged());
  }
  Snapshot old=read(row);
  Map<String,Bookmark>b=index(old.bookmarks(),x->x.itemType()+":"+x.itemId());
  Map<String,SrsCard>s=index(old.srsCards(),x->x.deckType()+":"+x.cardId());
  Map<String,StudyDay>d=index(old.studyDays(),StudyDay::date);
  int bc=0,sc=0,skip=0;
  for(Bookmark item:req.bookmarks())if(b.putIfAbsent(item.itemType()+":"+item.itemId(),item)==null)bc++;
  for(SrsCard item:req.srsCards())if(s.putIfAbsent(item.deckType()+":"+item.cardId(),item)==null)sc++;else skip++;
  for(StudyDay day:req.studyDays())d.merge(day.date(),day,LearningService::mergeDays);
  Snapshot next=save(row,new ArrayList<>(b.values()),new ArrayList<>(s.values()),new ArrayList<>(d.values()));
  Result result=new Result(next,bc,sc,skip,req.studyDays().size());
  LearningReceipt receipt=new LearningReceipt();receipt.setId(UUID.randomUUID());receipt.setUserId(owner);receipt.setRequestKey(req.idempotencyKey());receipt.setFingerprint(fingerprint);receipt.setPayload(json.writeValueAsString(result));em.persist(receipt);
  return result;
 }
 private static StudyDay mergeDays(StudyDay a,StudyDay b){
  int total=Math.addExact(a.cardsReviewed(),b.cardsReviewed());
  return new StudyDay(a.date(),total,Math.addExact(n(a.flashcardReviewed()),n(b.flashcardReviewed())),Math.addExact(n(a.srsReviewed()),n(b.srsReviewed())),Math.addExact(a.newCardsLearned(),b.newCardsLearned()),total==0?0:(a.accuracy()*a.cardsReviewed()+b.accuracy()*b.cardsReviewed())/total,a.timeSpent()+b.timeSpent());
 }
 private static int n(Integer x){return x==null?0:x;}
 private static <T> Map<String,T> index(List<T>items,Function<T,String>key){Map<String,T>m=new LinkedHashMap<>();for(T item:items)m.put(key.apply(item),item);return m;}
 private static void validate(List<Bookmark>b,List<SrsCard>s,List<StudyDay>d){
  if(index(b,x->x.itemType()+":"+x.itemId()).size()!=b.size()||index(s,x->x.deckType()+":"+x.cardId()).size()!=s.size()||index(d,StudyDay::date).size()!=d.size())throw new ApiException(HttpStatus.BAD_REQUEST,"Dữ liệu có khóa trùng");
  try{for(Bookmark x:b)Instant.parse(x.createdAt());for(SrsCard x:s){Instant.parse(x.dueDate());if(x.lastReviewedAt()!=null)Instant.parse(x.lastReviewedAt());if(x.firstReviewedAt()!=null)Instant.parse(x.firstReviewedAt());if("fsrs-6".equals(x.algorithm())&&(x.stability()==null||x.difficulty()==null||x.elapsedDays()==null||x.scheduledDays()==null||x.learningSteps()==null))throw new IllegalArgumentException("Thiếu trạng thái FSRS");}for(StudyDay x:d)LocalDate.parse(x.date());}
  catch(Exception e){throw new ApiException(HttpStatus.BAD_REQUEST,"Dữ liệu lịch ôn không hợp lệ");}
 }
 private static String hash(String payload){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
