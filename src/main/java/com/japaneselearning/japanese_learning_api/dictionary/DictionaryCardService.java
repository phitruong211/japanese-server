package com.japaneselearning.japanese_learning_api.dictionary;

import com.japaneselearning.japanese_learning_api.common.ApiException;
import com.japaneselearning.japanese_learning_api.deck.*;
import com.japaneselearning.japanese_learning_api.model.*;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import static com.japaneselearning.japanese_learning_api.dictionary.DictionaryData.*;

@Service
public class DictionaryCardService {
    public record AddRequest(@NotNull UUID targetDeckId, @NotBlank @Size(max=64) String datasetVersion,
        @NotBlank @Size(max=255) String writtenForm, @NotBlank @Size(max=255) String reading,
        @NotEmpty @Size(max=20) List<@NotBlank @Size(max=64) String> senseIds,
        @NotBlank @Size(max=20000) String front, @NotBlank @Size(max=20000) String back,
        @Size(max=20000) String notes) {}
    public record AddResult(UUID cardId, UUID deckId, String outcome, int cardCount, boolean available) {}
    public record CreateRequest(@NotBlank @Size(max=200) String name) {}
    public record CreatedDeck(UUID deckId, String name) {}
    @PersistenceContext private EntityManager em;
    @org.springframework.beans.factory.annotation.Value("${app.dictionary.cards-enabled:true}") private boolean cardsEnabled;
    private final DictionaryService dictionary;
    private final DeckService decks;
    private final ObjectMapper mapper;
    public DictionaryCardService(DictionaryService dictionary, DeckService decks,ObjectMapper mapper){this.dictionary=dictionary;this.decks=decks;this.mapper=mapper;}
    @Transactional
    public AddResult add(UUID userId,String key,String requestKey,AddRequest request) {
        lock(userId);
        String operation=operation(userId,"card",requestKey);
        String hash=DictionaryNormalizer.hash(key+mapper.writeValueAsString(request));
        DictionaryWriteRequest previous=em.find(DictionaryWriteRequest.class,operation,LockModeType.PESSIMISTIC_WRITE);
        if(previous!=null) {
            if(!previous.getRequestHash().equals(hash)) throw ApiException.conflict("Mã thao tác đã được dùng cho nội dung khác. Hãy xác nhận lại.");
            AddResult result=mapper.readValue(previous.getResultPayload(),AddResult.class);
            Card card=em.find(Card.class,result.cardId());
            boolean available=card!=null && card.getDeletedAt()==null && card.getDeck().getDeletedAt()==null && card.getDeck().getId().equals(result.deckId());
            return new AddResult(result.cardId(),result.deckId(),result.outcome(),available?card.getDeck().getCardCount():result.cardCount(),available);
        }
        Deck deck=em.find(Deck.class,request.targetDeckId(),LockModeType.PESSIMISTIC_WRITE);
        if(deck==null || !deck.getOwner().getId().equals(userId) || deck.getDeletedAt()!=null || deck.getSourceType()==DomainTypes.SourceType.SYSTEM)
            throw ApiException.notFound("Bộ thẻ không còn sử dụng được. Hãy chọn bộ khác.");
        Word word=dictionary.word(key,request.datasetVersion());
        Reading reading=word.readings().stream().filter(r->r.text().equals(request.reading())).findFirst().orElseThrow(()->bad("Cách đọc không hợp lệ"));
        boolean kana=request.writtenForm().equals(reading.text());
        if(!kana && (!word.writtenForms().contains(request.writtenForm()) || reading.kanaOnly() || (!reading.writtenForms().isEmpty() && !reading.writtenForms().contains(request.writtenForm()))))
            throw bad("Cách viết không tương ứng với cách đọc");
        List<String> senseIds=request.senseIds().stream().distinct().sorted().toList();
        for(String id:senseIds) {
            Sense sense=word.senses().stream().filter(s->s.id().equals(id)).findFirst().orElseThrow(()->bad("Nghĩa không hợp lệ"));
            if((!sense.readings().isEmpty() && !sense.readings().contains(reading.text())) || (!sense.writtenForms().isEmpty() && !sense.writtenForms().contains(request.writtenForm())))
                throw bad("Nghĩa không tương ứng với cách viết và cách đọc");
        }
        String identity=DictionaryNormalizer.hash(key+"\n"+request.writtenForm()+"\n"+reading.text()+"\n"+String.join(",",senseIds));
        List<Card> existing=em.createQuery("select c from Card c where c.deck.id=:d and c.dictionaryIdentity=:k and c.deletedAt is null",Card.class)
            .setParameter("d",deck.getId()).setParameter("k",identity).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
        AddResult result;
        if(!existing.isEmpty()) result=new AddResult(existing.getFirst().getId(),deck.getId(),"alreadyExists",deck.getCardCount(),true);
        else {
            var provenance=Map.of("entryKey",key,"version",word.version(),"writtenForm",request.writtenForm(),"reading",reading.text(),
                "senseIds",senseIds,"source",SOURCE,"license",LICENSE,"licenseUrl","https://www.edrdg.org/edrdg/licence.html","modified",true);
            String serialized=mapper.writeValueAsString(provenance);
            var card=decks.addCard(userId,deck.getId(),new DeckDtos.CardRequest(request.front(),request.back(),reading.text(),request.notes(),DomainTypes.CardKind.VOCABULARY,null,null,
                Map.of("dictionaryRef",serialized),List.of()));
            Card saved=em.find(Card.class,card.id()); saved.setDictionaryIdentity(identity); saved.setDictionaryProvenance(serialized);
            saved.getExtraData().put("dictionaryRef",serialized);
            result=new AddResult(card.id(),deck.getId(),"added",deck.getCardCount(),true);
        }
        remember(operation,hash,result);
        return result;
    }
    @Transactional
    public CreatedDeck create(UUID userId,String key,CreateRequest request) {
        lock(userId);
        String operation=operation(userId,"deck",key), hash=DictionaryNormalizer.hash(mapper.writeValueAsString(request));
        DictionaryWriteRequest previous=em.find(DictionaryWriteRequest.class,operation,LockModeType.PESSIMISTIC_WRITE);
        if(previous!=null) {
            if(!previous.getRequestHash().equals(hash)) throw ApiException.conflict("Mã tạo bộ đã dùng cho tên khác.");
            CreatedDeck result=mapper.readValue(previous.getResultPayload(),CreatedDeck.class);
            Deck deck=em.find(Deck.class,result.deckId());
            if(deck==null || deck.getDeletedAt()!=null) throw ApiException.conflict("Bộ vừa tạo đã bị xóa. Hãy tạo thao tác mới.");
            return result;
        }
        var deck=decks.create(userId,new DeckDtos.CreateDeckRequest(request.name(),null,DomainTypes.SourceType.MANUAL,null,null,DomainTypes.Visibility.PRIVATE,null,List.of(),"MANUAL",null,null,List.of()));
        CreatedDeck result=new CreatedDeck(deck.deck().id(),deck.deck().name()); remember(operation,hash,result); return result;
    }
    private void lock(UUID userId){
        if(!cardsEnabled)throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"Thêm từ vào bộ thẻ đang tạm dừng. Bản nháp vẫn được giữ.");
        if(em.find(User.class,userId,LockModeType.PESSIMISTIC_WRITE)==null)throw ApiException.notFound("Không tìm thấy tài khoản");
    }
    private String operation(UUID userId,String type,String key){
        if(key==null || !key.matches("[A-Za-z0-9_-]{16,100}"))throw bad("Idempotency-Key không hợp lệ");
        return userId+":"+type+":"+key;
    }
    private void remember(String key,String hash,Object result){
        DictionaryWriteRequest r=new DictionaryWriteRequest();r.setId(key);r.setRequestHash(hash);r.setResultPayload(mapper.writeValueAsString(result));r.setCreatedAt(Instant.now());em.persist(r);
    }
    private ApiException bad(String text){return new ApiException(HttpStatus.BAD_REQUEST,text);}
}
