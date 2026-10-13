package com.japaneselearning.japanese_learning_api.dictionary;

import com.japaneselearning.japanese_learning_api.security.CurrentUser;
import com.japaneselearning.japanese_learning_api.model.*;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/v1")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="app.dictionary.enabled",havingValue="true",matchIfMissing=true)
public class DictionaryController {
    private final DictionaryService service;
    private final DictionaryCardService cards;
    @PersistenceContext private EntityManager em;
    public DictionaryController(DictionaryService service,DictionaryCardService cards){this.service=service;this.cards=cards;}
    @GetMapping("/dictionary/search")
    ResponseEntity<?> search(@RequestParam String q,@RequestParam(required=false) String kana,@RequestParam(required=false) String cursor,@RequestParam(defaultValue="20") int limit){
        var result=service.search(q,kana,cursor,limit);
        Map<String,Object> response=new LinkedHashMap<>();response.put("items",result.items());response.put("version",result.version());response.put("query",result.query());
        response.put("nextCursor",service.cursor(q,kana,limit,result));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }
    @GetMapping("/dictionary/words/{key}")
    ResponseEntity<?> word(@PathVariable String key,@RequestParam(required=false) String version){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.word(key,version));}
    @GetMapping("/dictionary/sources")
    Object sources(){return service.sources();}
    @PostMapping("/dictionary/words/{key}/cards")
    Object add(Authentication auth,@PathVariable String key,@RequestHeader("Idempotency-Key") String id,@Valid @RequestBody DictionaryCardService.AddRequest request){return cards.add(CurrentUser.id(auth),key,id,request);}
    @PostMapping("/dictionary/decks")
    Object create(Authentication auth,@RequestHeader("Idempotency-Key") String id,@Valid @RequestBody DictionaryCardService.CreateRequest request){return cards.create(CurrentUser.id(auth),id,request);}
    public record DeckOption(UUID id,String name,UUID groupId,String groupName,DomainTypes.SourceType sourceType,Map<String,Object> templateConfig) {}
    @GetMapping("/decks/options") @org.springframework.transaction.annotation.Transactional(readOnly=true)
    Object options(Authentication auth,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") int page){
        if(q.length()>200 || page<0 || page>10000)throw new com.japaneselearning.japanese_learning_api.common.ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,"Tìm bộ thẻ không hợp lệ");
        String pattern="%"+q.strip().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        List<Deck> result=em.createQuery("select d from Deck d where d.owner.id=:u and d.deletedAt is null and d.sourceType<>:system and lower(d.name) like :q escape '!' order by d.name,d.id",Deck.class)
            .setParameter("u",CurrentUser.id(auth)).setParameter("system",DomainTypes.SourceType.SYSTEM).setParameter("q",pattern).setFirstResult(page*20).setMaxResults(21).getResultList();
        Map<UUID,String> names=new HashMap<>();
        var groups=em.createQuery("select g from DeckGroup g where g.owner.id=:u",DeckGroup.class).setParameter("u",CurrentUser.id(auth)).getResultList();
        groups.forEach(g->names.put(g.getId(),g.getName()));
        return Map.of("items",result.stream().limit(20).map(d->new DeckOption(d.getId(),d.getName(),d.getGroupId(),names.get(d.getGroupId()),d.getSourceType(),d.getTemplateConfig())).toList(),"hasMore",result.size()>20,"page",page);
    }
}
