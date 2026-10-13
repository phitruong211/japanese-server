package com.japaneselearning.japanese_learning_api.dictionary;

import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import static com.japaneselearning.japanese_learning_api.dictionary.DictionaryData.*;

/** Bounded public-data cache. Version is part of every key; never caches account data or errors. */
@Component
public class DictionarySearchCache {
    private record Value(List<Hit> hits, long created, int weight) {}
    private final LinkedHashMap<String,Value> completed=new LinkedHashMap<>(16,0.75f,true);
    private final ConcurrentHashMap<String,CompletableFuture<List<Hit>>> running=new ConcurrentHashMap<>();
    private final int maxEntries,maxWeight;
    private final long ttl;
    private final LongSupplier clock;
    private int weight;
    public DictionarySearchCache(){this(32,16_000_000,java.time.Duration.ofMinutes(1).toNanos(),System::nanoTime);}
    DictionarySearchCache(int maxEntries,int maxWeight,long ttl,LongSupplier clock){this.maxEntries=maxEntries;this.maxWeight=maxWeight;this.ttl=ttl;this.clock=clock;}
    public List<Hit> get(String key,Supplier<List<Hit>> loader){
        synchronized(completed){
            Value value=completed.get(key);
            if(value!=null){
                if(clock.getAsLong()-value.created()<ttl)return value.hits();
                completed.remove(key);weight-=value.weight();
            }
        }
        CompletableFuture<List<Hit>> mine=new CompletableFuture<>();
        CompletableFuture<List<Hit>> previous=running.putIfAbsent(key,mine);
        if(previous!=null){
            try{return previous.join();}catch(CompletionException e){if(e.getCause() instanceof RuntimeException cause)throw cause;throw e;}
        }
        try{
            List<Hit> hits=List.copyOf(loader.get());
            int size=estimate(hits);
            synchronized(completed){
                if(size<=maxWeight){
                    Value replaced=completed.put(key,new Value(hits,clock.getAsLong(),size));
                    if(replaced!=null)weight-=replaced.weight();
                    weight+=size;
                    while(completed.size()>maxEntries || weight>maxWeight){var iterator=completed.entrySet().iterator();var oldest=iterator.next();weight-=oldest.getValue().weight();iterator.remove();}
                }
            }
            mine.complete(hits);return hits;
        }catch(RuntimeException | Error e){mine.completeExceptionally(e);throw e;}
        finally{running.remove(key,mine);}
    }
    private int estimate(List<Hit> hits){
        long bytes=64;
        for(Hit hit:hits){
            Word word=hit.word();bytes+=512;
            for(String form:word.writtenForms())bytes+=48+2L*form.length();
            for(Reading r:word.readings())bytes+=96+2L*r.text().length()+r.writtenForms().stream().mapToLong(s->48+2L*s.length()).sum();
            for(Sense s:word.senses()){
                bytes+=256;
                for(Gloss g:s.glosses())bytes+=96+2L*g.text().length();
                for(List<String> strings:List.of(s.partsOfSpeech(),s.writtenForms(),s.readings(),s.notes()))for(String v:strings)bytes+=48+2L*v.length();
            }
            if(bytes>maxWeight)return maxWeight+1;
        }
        return (int)bytes;
    }
}
