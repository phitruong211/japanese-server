package com.japaneselearning.japanese_learning_api.dictionary;

import com.japaneselearning.japanese_learning_api.common.ApiException;
import com.japaneselearning.japanese_learning_api.deck.*;
import com.japaneselearning.japanese_learning_api.model.*;
import com.japaneselearning.japanese_learning_api.repository.*;
import com.japaneselearning.japanese_learning_api.security.JwtService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.config.import=","spring.datasource.url=jdbc:h2:mem:dictionary;MODE=MySQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=create-drop","spring.flyway.enabled=false"})
@AutoConfigureMockMvc
class DictionaryIntegrationTest {
    @DynamicPropertySource static void mysql(DynamicPropertyRegistry r) {
        String url=System.getProperty("dictionary.mysql.url"); if(url==null)return;
        r.add("spring.datasource.url",()->url);r.add("spring.datasource.driver-class-name",()->"com.mysql.cj.jdbc.Driver");
        r.add("spring.datasource.username",()->"root");r.add("spring.datasource.password",()->"");r.add("spring.flyway.enabled",()->"true");r.add("spring.jpa.hibernate.ddl-auto",()->"validate");
    }
    @Autowired DictionaryService dictionary;
    @Autowired DictionaryImportService importer;
    @Autowired DictionaryCardService cards;
    @Autowired DeckService decks;
    @Autowired DeckShareService sharing;
    @Autowired UserRepository users;
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired ObjectMapper mapper;
    String version;
    @BeforeEach void data() throws Exception { version=importer.importFile(Path.of("src/test/resources/dictionary-fixture.xml"),6); }
    User user(){var u=new User();u.setId(UUID.randomUUID());u.setEmail(u.getId()+"@dictionary.test");u.setPasswordHash("test");u.setDisplayName("Learner");return users.saveAndFlush(u);}
    UUID deck(User u,DomainTypes.SourceType type){return decks.create(u.getId(),new DeckDtos.CreateDeckRequest("Test deck",null,type,null,null,DomainTypes.Visibility.PRIVATE,null,List.of(),null,null,null,List.of())).deck().id();}
    DictionaryCardService.AddRequest request(UUID d,String front){var w=dictionary.word("jmdict:10001",version);return new DictionaryCardService.AddRequest(d,version,"猫","ねこ",List.of(w.senses().getFirst().id()),front,"Con mèo","");}
    @Test void publicSearchNormalizesKanaSupportsAmbiguityAndBindsCursor() throws Exception {
        mvc.perform(get("/api/v1/dictionary/search").param("q","ﾈｺ")).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].word.key").value("jmdict:10001"));
        assertThat(dictionary.search("neko","ねこ",null,20).items().getFirst().word().key()).isEqualTo("jmdict:10001");
        assertThat(dictionary.search("cat",null,null,20).items()).hasSize(1);
        assertThat(dictionary.search("%",null,null,20).items()).isEmpty();
        var first=dictionary.search("はし",null,null,1);assertThat(first.items()).hasSize(1);assertThat(first.hasMore()).isTrue();
        String cursor=dictionary.cursor("はし",null,1,first);
        assertThat(dictionary.search("はし",null,cursor,1).items().getFirst().word().key()).isNotEqualTo(first.items().getFirst().word().key());
        assertThatThrownBy(()->dictionary.search("猫",null,cursor,1)).isInstanceOf(ApiException.class);
        mvc.perform(post("/api/v1/dictionary/words/jmdict:10001/cards").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
    }
    @Test void ownerChecksIdempotencyAndEditedCardRemainIndependent() throws Exception {
        var u=user();var d=deck(u,DomainTypes.SourceType.IMPORT);String key=UUID.randomUUID().toString();var req=request(d,"猫");
        var result=cards.add(u.getId(),"jmdict:10001",key,req);assertThat(result.outcome()).isEqualTo("added");
        assertThat(cards.add(u.getId(),"jmdict:10001",key,req).cardId()).isEqualTo(result.cardId());
        assertThatThrownBy(()->cards.add(u.getId(),"jmdict:10001",key,request(d,"edited"))).isInstanceOf(ApiException.class);
        decks.updateCard(u.getId(),result.cardId(),new DeckDtos.UpdateCardRequest("私の猫","edited",null,null,null,null,Map.of("custom","kept"),null));
        assertThat(cards.add(u.getId(),"jmdict:10001",UUID.randomUUID().toString(),req).outcome()).isEqualTo("alreadyExists");
        var saved=decks.get(u.getId(),d);assertThat(saved.deck().cardCount()).isEqualTo(1);assertThat(saved.cards().getFirst().front()).isEqualTo("私の猫");
        assertThat(saved.cards().getFirst().extraData()).containsKey("dictionaryRef");assertThat(saved.cards().getFirst().progress()).isNull();
        assertThatThrownBy(()->cards.add(user().getId(),"jmdict:10001",UUID.randomUUID().toString(),req)).isInstanceOf(ApiException.class);
        mvc.perform(post("/api/v1/dictionary/words/jmdict:10001/cards").header("Authorization","Bearer "+jwt.createAccessToken(u)).header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json").content(mapper.writeValueAsString(req)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("alreadyExists"));
    }
    @Test void deinflectionChecksDictionaryPartOfSpeech() {
        for(var pair:List.of(new String[]{"食べました","jmdict:10002"},new String[]{"書いて","jmdict:10007"},new String[]{"行った","jmdict:10008"},new String[]{"高かった","jmdict:10009"},new String[]{"勉強しました","jmdict:10010"})) {
            var result=dictionary.search(pair[0],null,null,20);
            assertThat(result.items()).anyMatch(hit->hit.word().key().equals(pair[1]) && hit.matchType().equals("deinflected"));
        }
        assertThat(dictionary.search("猫ました",null,null,20).items()).isEmpty();
        assertThat(dictionary.search("高いました",null,null,20).items()).isEmpty();
        assertThat(dictionary.search("行いて",null,null,20).items()).isEmpty();
        assertThat(dictionary.search("猫いだ",null,null,20).items()).isEmpty();
        assertThat(dictionary.search("泳いだ",null,null,20).items()).anyMatch(h->h.word().key().equals("jmdict:10011"));
    }
    @Test void simultaneousAddsAndNormalAddHaveCorrectCountAndPositions() throws Exception {
        var u=user();UUID d=deck(u,DomainTypes.SourceType.MANUAL);var req=request(d,"猫");
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<Object>> tasks=new ArrayList<>();
            for(int i=0;i<6;i++)tasks.add(()->cards.add(u.getId(),"jmdict:10001",UUID.randomUUID().toString(),req));
            tasks.add(()->decks.addCard(u.getId(),d,new DeckDtos.CardRequest("犬","dog","いぬ",null,DomainTypes.CardKind.VOCABULARY,null,null,null,null)));
            for(var future:executor.invokeAll(tasks))future.get(20,TimeUnit.SECONDS);
        }
        var saved=decks.get(u.getId(),d);assertThat(saved.deck().cardCount()).isEqualTo(2);assertThat(saved.cards()).extracting(DeckDtos.CardResponse::position).containsExactly(0,1);
    }
    @Test void deleteMoveShareCopyAndRecreateRespectIdentity() {
        var u=user();var d=deck(u,DomainTypes.SourceType.IMPORT);var target=deck(u,DomainTypes.SourceType.MANUAL);var req=request(d,"猫");String key=UUID.randomUUID().toString();
        var added=cards.add(u.getId(),"jmdict:10001",key,req);
        decks.moveCards(u.getId(),new DeckDtos.MoveCardsRequest(List.of(added.cardId()),target,null));
        assertThat(cards.add(u.getId(),"jmdict:10001",key,req).available()).isFalse();
        assertThat(cards.add(u.getId(),"jmdict:10001",UUID.randomUUID().toString(),request(target,"猫")).outcome()).isEqualTo("alreadyExists");
        var again=cards.add(u.getId(),"jmdict:10001",UUID.randomUUID().toString(),req);
        assertThatThrownBy(()->decks.moveCards(u.getId(),new DeckDtos.MoveCardsRequest(List.of(again.cardId()),target,null))).isInstanceOf(ApiException.class);
        var recipient=user();var copied=sharing.copy(recipient.getId(),sharing.enable(u.getId(),d).token());
        assertThat(decks.get(recipient.getId(),copied.deckId()).cards().getFirst().extraData()).containsKey("dictionaryRef");
        assertThat(cards.add(recipient.getId(),"jmdict:10001",UUID.randomUUID().toString(),request(copied.deckId(),"猫")).outcome()).isEqualTo("alreadyExists");
        decks.deleteCard(u.getId(),again.cardId());
        assertThat(cards.add(u.getId(),"jmdict:10001",UUID.randomUUID().toString(),req).outcome()).isEqualTo("added");
    }
    @Test void restrictionsCannotBeBypassedAndCreateDeckIsRetrySafe() {
        var u=user();var w=dictionary.word("jmdict:10003",version);var created=cards.create(u.getId(),"create-test-123456",new DictionaryCardService.CreateRequest("My deck"));
        assertThat(cards.create(u.getId(),"create-test-123456",new DictionaryCardService.CreateRequest("My deck"))).isEqualTo(created);
        var invalid=new DictionaryCardService.AddRequest(created.deckId(),version,"生まれ","なま",List.of(w.senses().getFirst().id()),"生まれ","birth","");
        assertThatThrownBy(()->cards.add(u.getId(),w.key(),UUID.randomUUID().toString(),invalid)).isInstanceOf(ApiException.class);
    }
    @Test void failedImportAndExternalEntityCannotChangeCatalog() throws Exception {
        Path file=Files.createTempFile("dictionary-malformed",".xml");
        try {
            Files.writeString(file,"<!DOCTYPE JMdict [<!ENTITY secret SYSTEM 'file:///private'>]><JMdict><entry><ent_seq>9999</ent_seq><r_ele><reb>&secret;</reb></r_ele></entry></JMdict>");
            assertThatThrownBy(()->importer.importFile(file,1)).isInstanceOf(Exception.class);
            assertThat(dictionary.word("jmdict:10001",null).version()).isEqualTo(version);
            List<DictionaryData.Word> parsed=new ArrayList<>();
            try(var in=Files.newInputStream(Path.of("src/test/resources/dictionary-fixture.xml"))){new JmdictReader().read(in,"test",parsed::add);}
            assertThat(parsed.getFirst().senses().getFirst().partsOfSpeech()).containsExactly("noun");
        } finally { Files.deleteIfExists(file); }
    }
    @Test void meaningSearchSelectsOneSenseRatherThanCombiningUnrelatedMeanings() {
        var word=dictionary.word("jmdict:10003",version);
        var result=dictionary.search("birth",null,null,20);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().matchedSenseId()).isEqualTo(word.senses().get(1).id());
        assertThat(dictionary.search("raw birth",null,null,20).items()).isEmpty();
    }
    @Test void paginationReachesEveryBoundedCandidateIncludingTheLastPage() throws Exception {
        Path file=Files.createTempFile("dictionary-pagination",".xml");
        StringBuilder xml=new StringBuilder("<JMdict>");
        for(int i=0;i<251;i++)xml.append("<entry><ent_seq>").append(200000+i).append("</ent_seq><r_ele><reb>はし</reb></r_ele><sense><pos>noun</pos><gloss>homophone</gloss></sense></entry>");
        xml.append("</JMdict>");
        try {
            Files.writeString(file,xml.toString());importer.importFile(file,251);
            for(int limit:List.of(1,20,50)) {
                Set<String> seen=new HashSet<>();String cursor=null;int pages=0;
                do {
                    var result=dictionary.search("はし",null,cursor,limit);
                    for(var hit:result.items())assertThat(seen.add(hit.word().key())).isTrue();
                    cursor=dictionary.cursor("はし",null,limit,result);
                    assertThat(++pages).isLessThanOrEqualTo(250);
                }while(cursor!=null);
                assertThat(seen).hasSize(250);
            }
        }finally{importer.activate(version);Files.deleteIfExists(file);}
    }
    @Test void expiredDatasetAndCursorHaveRecoverableErrorCodes() throws Exception {
        var u=user();UUID d=deck(u,DomainTypes.SourceType.MANUAL);var normal=request(d,"猫");
        var expired=new DictionaryCardService.AddRequest(d,"0".repeat(64),normal.writtenForm(),normal.reading(),normal.senseIds(),normal.front(),normal.back(),normal.notes());
        mvc.perform(post("/api/v1/dictionary/words/jmdict:10001/cards").header("Authorization","Bearer "+jwt.createAccessToken(u)).header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json").content(mapper.writeValueAsString(expired)))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DICTIONARY_VERSION_EXPIRED"));
        mvc.perform(get("/api/v1/dictionary/search").param("q","猫").param("cursor","invalid"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DICTIONARY_CURSOR_EXPIRED"));
    }
}
