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
@Service @Transactional
public class LearningService {
 private final EntityManager em; private final ObjectMapper json;
 public LearningService(EntityManager em,ObjectMapper json){this.em=em;this.json=json;}
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
  try{for(Bookmark x:b)Instant.parse(x.createdAt());for(SrsCard x:s){Instant.parse(x.dueDate());if(x.lastReviewedAt()!=null)Instant.parse(x.lastReviewedAt());}for(StudyDay x:d)LocalDate.parse(x.date());}
  catch(Exception e){throw new ApiException(HttpStatus.BAD_REQUEST,"Ngày trong dữ liệu học không hợp lệ");}
 }
 private static String hash(String payload){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
