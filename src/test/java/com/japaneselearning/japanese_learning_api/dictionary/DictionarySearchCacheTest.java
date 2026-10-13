package com.japaneselearning.japanese_learning_api.dictionary;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;
class DictionarySearchCacheTest {
    @Test void expiresEvictsAndSeparatesVersions(){
        AtomicLong clock=new AtomicLong();AtomicInteger calls=new AtomicInteger();
        var cache=new DictionarySearchCache(2,10000,100,clock::get);
        java.util.function.Supplier<List<DictionaryData.Hit>> load=()->{calls.incrementAndGet();return List.of();};
        cache.get("v1:q",load);cache.get("v1:q",load);assertThat(calls.get()).isEqualTo(1);
        cache.get("v2:q",load);assertThat(calls.get()).isEqualTo(2);
        cache.get("other",load);cache.get("v1:q",load);assertThat(calls.get()).isEqualTo(4);
        clock.set(101);cache.get("v1:q",load);assertThat(calls.get()).isEqualTo(5);
    }
    @Test void concurrentRequestsShareOneLoadAndFailuresCanRetry() throws Exception {
        var cache=new DictionarySearchCache();AtomicInteger calls=new AtomicInteger();
        CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()){
            var first=executor.submit(()->cache.get("key",()->{calls.incrementAndGet();started.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){throw new RuntimeException(e);}return List.of();}));
            assertThat(started.await(5,TimeUnit.SECONDS)).isTrue();
            var second=executor.submit(()->cache.get("key",()->{calls.incrementAndGet();return List.of();}));
            release.countDown();first.get(5,TimeUnit.SECONDS);second.get(5,TimeUnit.SECONDS);
            assertThat(calls.get()).isEqualTo(1);
        }
        assertThatThrownBy(()->cache.get("failed",()->{throw new IllegalStateException("retry");})).isInstanceOf(IllegalStateException.class);
        assertThat(cache.get("failed",List::of)).isEmpty();
    }
}
