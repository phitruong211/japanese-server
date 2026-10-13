package com.japaneselearning.japanese_learning_api.dictionary;

import com.japaneselearning.japanese_learning_api.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.*;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import static com.japaneselearning.japanese_learning_api.dictionary.DictionaryData.*;

@Service
public class DictionaryService {
    @PersistenceContext private EntityManager em;
    private final ObjectMapper mapper;
    private final DictionarySearchCache cache;
    public DictionaryService(ObjectMapper mapper,DictionarySearchCache cache) { this.mapper=mapper;this.cache=cache; }
    public String activeVersion() {
        DictionaryCatalog catalog=em.find(DictionaryCatalog.class,1);
        if(catalog==null) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"Từ điển đang được chuẩn bị. Vui lòng thử lại sau.");
        return catalog.getDatasetId();
    }
    @Transactional(readOnly=true)
    public Word word(String key, String version) {
        if(!key.matches("jmdict:[0-9]{1,12}")) throw ApiException.notFound("Không tìm thấy mục từ");
        String v=version==null?activeVersion():version;
        DictionaryDataset dataset=em.find(DictionaryDataset.class,v);
        if(dataset==null || !"READY".equals(dataset.getStatus()))throw new ApiException(HttpStatus.CONFLICT,"DICTIONARY_VERSION_EXPIRED","Phiên bản từ điển không còn khả dụng. Hãy tải lại từ và kiểm tra nghĩa.");
        DictionaryEntry entry=em.find(DictionaryEntry.class,v+":"+key);
        if(entry==null) {
            if(version!=null && em.find(DictionaryDataset.class,v)==null) throw ApiException.conflict("Phiên bản từ điển đã hết hạn. Hãy tải lại từ và xác nhận nghĩa.");
            throw ApiException.notFound("Không tìm thấy mục từ");
        }
        return mapper.readValue(entry.getPayload(),Word.class);
    }
    @Transactional(readOnly=true)
    public Map<String,Object> sources() {
        DictionaryCatalog catalog=em.find(DictionaryCatalog.class,1);
        DictionaryDataset dataset=catalog==null?null:em.find(DictionaryDataset.class,catalog.getDatasetId());
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("name",SOURCE); out.put("license",LICENSE); out.put("url","https://www.edrdg.org/edrdg/licence.html");
        out.put("documentationUrl","https://www.edrdg.org/jmdict/edict_doc.html");
        out.put("ready",dataset!=null); out.put("version",dataset==null?null:dataset.getId());
        out.put("entryCount",dataset==null?0:dataset.getEntryCount());
        out.put("importedAt",dataset==null?null:dataset.getImportedAt()); out.put("languages",List.of("ja","en"));
        return out;
    }
    @Transactional(readOnly=true)
    public SearchResult search(String query, String kana, String cursor, int limit) {
        if(query==null || query.codePointCount(0,query.length())>100 || limit<1 || limit>50)
            throw new ApiException(HttpStatus.BAD_REQUEST,"Từ khóa tối đa 100 ký tự; số kết quả từ 1 đến 50.");
        String q=DictionaryNormalizer.normalize(query); String version=activeVersion();
        LinkedHashSet<String> variants=new LinkedHashSet<>(); variants.add(q);
        if(kana!=null && kana.length()<=100 && kana.matches("[\\p{IsHiragana}\\p{IsKatakana}ー]+")) variants.add(DictionaryNormalizer.normalize(kana));
        String fingerprint=DictionaryNormalizer.hash(q+variants+limit);
        int offset=0;
        if(cursor!=null && !cursor.isBlank()) {
            try {
                String[] parts=new String(Base64.getUrlDecoder().decode(cursor),java.nio.charset.StandardCharsets.UTF_8).split(":");
                if(parts.length!=3 || !parts[0].equals(version) || !parts[1].equals(fingerprint)) throw new IllegalArgumentException();
                offset=Integer.parseInt(parts[2]); if(offset<0 || offset>=250 || offset%limit!=0) throw new IllegalArgumentException();
            }catch(Exception e){throw new ApiException(HttpStatus.CONFLICT,"DICTIONARY_CURSOR_EXPIRED","Kết quả đã thay đổi. Hãy tìm lại từ đầu.");}
        }
        if(q.isBlank()) return new SearchResult(List.of(),0,false,version,query);
        List<Hit> sorted=cache.get(version+":"+fingerprint,()->ranked(version,q,query,variants));
        int end=Math.min(offset+limit,sorted.size());
        return new SearchResult(offset>=sorted.size()?List.of():sorted.subList(offset,end),offset/limit,end<sorted.size(),version,query);
    }
    private List<Hit> ranked(String version,String q,String query,Set<String> variants) {
        // Bounded ranked candidates, index-backed exact then prefix then gloss tokens.
        LinkedHashMap<String,Hit> hits=new LinkedHashMap<>();
        for(String v:variants) collect(hits,version,v,false,"exact",250);
        JapaneseDeinflector deinflector=new JapaneseDeinflector();
        List<JapaneseDeinflector.Candidate> candidates=variants.stream().flatMap(v->deinflector.candidates(v).stream()).limit(128).toList();
        if(!candidates.isEmpty()) {
            List<DictionaryForm> transformedForms=em.createQuery("select f from DictionaryForm f where f.datasetId=:v and f.normalized in :forms order by f.entryKey",DictionaryForm.class)
                .setParameter("v",version).setParameter("forms",candidates.stream().map(JapaneseDeinflector.Candidate::form).distinct().toList()).setMaxResults(300).getResultList();
            Map<String,Hit> transformed=new LinkedHashMap<>();loadHits(transformed,version,transformedForms.stream().map(DictionaryForm::getEntryKey).distinct().toList(),"deinflected",q);
            for(var form:transformedForms) {
                Hit hit=transformed.get(form.getEntryKey());if(hit==null)continue;
                hit.word().senses().stream().filter(s->validSenseForForm(hit.word(),s,form.getNormalized()) && candidates.stream().anyMatch(c->c.form().equals(form.getNormalized()) && deinflector.matches(c.pos(),s.partsOfSpeech())))
                    .findFirst().ifPresent(s->hits.putIfAbsent(hit.word().key(),new Hit(hit.word(),"deinflected",form.getNormalized(),s.id())));
            }
        }
        for(String v:variants) collect(hits,version,v,true,"prefix",250);
        List<String> tokens=DictionaryNormalizer.tokens(q);
        if(!tokens.isEmpty()) {
            List<String> keys=em.createQuery("select t.entryKey from DictionaryToken t where t.datasetId=:v and t.token in :tokens group by t.entryKey having count(distinct t.token)=:n order by t.entryKey",String.class)
                .setParameter("v",version).setParameter("tokens",tokens).setParameter("n",(long)tokens.size()).setMaxResults(250).getResultList();
            Map<String,Hit> meaningHits=new LinkedHashMap<>();
            loadHits(meaningHits,version,keys,"meaning",query);
            for(Hit hit:meaningHits.values()) {
                // Tokens must belong to one sense; never combine unrelated meanings.
                hit.word().senses().stream().filter(s-> {
                    Set<String> senseTokens=new HashSet<>();
                    s.glosses().forEach(g->senseTokens.addAll(DictionaryNormalizer.tokens(g.text())));
                    return senseTokens.containsAll(tokens);
                }).findFirst().ifPresent(s->hits.putIfAbsent(hit.word().key(),new Hit(hit.word(),"meaning",query,s.id())));
            }
        }
        return hits.values().stream().sorted(Comparator.comparingInt((Hit h)->rank(h.matchType()))
            .thenComparing(h->!h.word().common()).thenComparing(h->h.word().key())).limit(250).toList();
    }
    public String cursor(String query,String kana,int limit,SearchResult result) {
        if(!result.hasMore()) return null;
        LinkedHashSet<String> variants=new LinkedHashSet<>(); String q=DictionaryNormalizer.normalize(query); variants.add(q);
        if(kana!=null && kana.length()<=100 && kana.matches("[\\p{IsHiragana}\\p{IsKatakana}ー]+")) variants.add(DictionaryNormalizer.normalize(kana));
        String value=result.version()+":"+DictionaryNormalizer.hash(q+variants+limit)+":"+((result.page()+1)*limit);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private int rank(String match){return switch(match){case "exact"->0;case "deinflected"->1;case "prefix"->2;default->3;};}
    private boolean validSenseForForm(Word word,Sense sense,String form) {
        for(Reading reading:word.readings()) {
            List<String> written=reading.kanaOnly() || word.writtenForms().isEmpty()?List.of(reading.text()):reading.writtenForms().isEmpty()?word.writtenForms():reading.writtenForms();
            for(String spelling:written)if((DictionaryNormalizer.normalize(spelling).equals(form) || DictionaryNormalizer.normalize(reading.text()).equals(form))
                && (sense.writtenForms().isEmpty() || sense.writtenForms().contains(spelling)) && (sense.readings().isEmpty() || sense.readings().contains(reading.text())))return true;
        }
        return false;
    }
    private void collect(Map<String,Hit> hits,String version,String query,boolean prefix,String match,int max) {
        // Escape LIKE wildcards so user input is literal.
        String value=query.replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        List<String> keys=em.createQuery("select distinct f.entryKey from DictionaryForm f where f.datasetId=:v and f.normalized "+(prefix?"like :q escape '!'":"= :q")+" order by f.entryKey",String.class)
            .setParameter("v",version).setParameter("q",prefix?value:query).setMaxResults(max).getResultList();
        loadHits(hits,version,keys,match,query);
    }
    private void loadHits(Map<String,Hit> hits,String version,List<String> keys,String match,String form) {
        keys=keys.stream().filter(k->!hits.containsKey(k)).toList();
        if(keys.isEmpty()) return;
        var entries=em.createQuery("select e from DictionaryEntry e where e.datasetId=:v and e.entryKey in :keys",DictionaryEntry.class)
            .setParameter("v",version).setParameter("keys",keys).getResultList();
        for(var e:entries) hits.putIfAbsent(e.getEntryKey(),new Hit(mapper.readValue(e.getPayload(),Word.class),match,form));
    }
}
