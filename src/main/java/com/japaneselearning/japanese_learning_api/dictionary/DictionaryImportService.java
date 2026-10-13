package com.japaneselearning.japanese_learning_api.dictionary;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.zip.GZIPInputStream;
import static com.japaneselearning.japanese_learning_api.dictionary.DictionaryData.*;

@Service
public class DictionaryImportService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;
    public DictionaryImportService(JdbcTemplate jdbc, ObjectMapper mapper, PlatformTransactionManager manager) {
        this.jdbc = jdbc; this.mapper = mapper; this.tx = new TransactionTemplate(manager);
    }
    /** File must be obtained from the documented source. Imports stage data; activate only after complete parse. */
    public synchronized String importFile(Path file, int minimumEntries) throws Exception {
        if (Files.size(file) > 100_000_000) throw new IllegalArgumentException("Compressed dictionary exceeds 100 MB");
        String version;
        try (InputStream in = Files.newInputStream(file)) {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[65536]; int length;
            while ((length = in.read(buffer)) != -1) digest.update(buffer, 0, length);
            version = HexFormat.of().formatHex(digest.digest());
        }
        // The dataset row serializes importers even when two JVMs import the same file.
        final String v = version;
        try { jdbc.update("insert into dictionary_datasets(id,status,imported_at,entry_count,source_url) values(?,?,?,?,?)",
            v, "STAGING", java.sql.Timestamp.from(Instant.now()), 0, SOURCE_URL); }
        catch (org.springframework.dao.DuplicateKeyException e) {
            String status = jdbc.queryForObject("select status from dictionary_datasets where id=?", String.class, v);
            if ("READY".equals(status)) { activate(v); return v; }
            throw new IllegalStateException("Dataset already staging/failed. Inspect and remove that inactive version before retrying.");
        }
        List<Word> batch = new ArrayList<>();
        java.util.concurrent.atomic.AtomicInteger processed = new java.util.concurrent.atomic.AtomicInteger();
        try (InputStream raw = Files.newInputStream(file);
             InputStream gzip = file.toString().endsWith(".gz") ? new GZIPInputStream(raw) : raw;
             InputStream bounded = new FilterInputStream(gzip) {
                 long total;
                 private void check(int n) throws IOException { if (n > 0 && (total += n) > 500_000_000) throw new IOException("Expanded XML exceeds 500 MB"); }
                 @Override public int read() throws IOException { int n = super.read(); check(n < 0 ? 0 : 1); return n; }
                 @Override public int read(byte[] b, int off, int len) throws IOException { int n = in.read(b, off, len); check(n); return n; }
             }) {
            int count = new JmdictReader().read(bounded, v, word -> {
                batch.add(word);
                if (batch.size() >= 200) {
                    writeBatch(batch); processed.addAndGet(batch.size()); batch.clear();
                    if (processed.get() % 10000 == 0) System.out.println("Dictionary staging: " + processed.get() + " entries");
                }
            });
            if (!batch.isEmpty()) writeBatch(batch);
            if (count < minimumEntries) throw new IllegalArgumentException("Dictionary has only " + count + " entries; minimum " + minimumEntries);
            tx.executeWithoutResult(s -> {
                jdbc.update("update dictionary_datasets set status='READY',entry_count=? where id=?", count, v);
                activate(v);
            });
            return v;
        } catch (Exception e) {
            jdbc.update("update dictionary_datasets set status='FAILED' where id=?", v);
            throw e;
        }
    }
    public void activate(String version) {
        tx.executeWithoutResult(s -> {
            String state = jdbc.queryForObject("select status from dictionary_datasets where id=?", String.class, version);
            if (!"READY".equals(state)) throw new IllegalArgumentException("Only READY datasets can be activated");
            int updated = jdbc.update("update dictionary_catalog set dataset_id=? where id=1", version);
            if (updated == 0) jdbc.update("insert into dictionary_catalog(id,dataset_id) values(1,?)", version);
        });
    }
    private void writeBatch(List<Word> words) {
        tx.executeWithoutResult(s -> {
            List<Object[]> entries = new ArrayList<>(), forms = new ArrayList<>(), tokens = new ArrayList<>();
            for (Word w : words) {
                entries.add(new Object[]{w.version()+":"+w.key(),w.version(),w.key(),w.common(),mapper.writeValueAsString(w)});
                Map<String,String> allForms = new LinkedHashMap<>();
                w.writtenForms().forEach(f -> allForms.put(DictionaryNormalizer.normalize(f),"written"));
                w.readings().forEach(f -> allForms.putIfAbsent(DictionaryNormalizer.normalize(f.text()),"reading"));
                allForms.forEach((f,t) -> { if (f.length() <= 255) forms.add(new Object[]{DictionaryNormalizer.hash(w.version()+w.key()+f),w.version(),w.key(),f,t}); });
                Set<String> seen = new HashSet<>();
                w.senses().forEach(sense -> sense.glosses().forEach(g -> seen.addAll(DictionaryNormalizer.tokens(g.text()))));
                seen.forEach(t -> tokens.add(new Object[]{DictionaryNormalizer.hash(w.version()+w.key()+t),w.version(),w.key(),t}));
            }
            jdbc.batchUpdate("insert into dictionary_entries(id,dataset_id,entry_key,common_word,payload) values(?,?,?,?,?)",entries);
            jdbc.batchUpdate("insert into dictionary_forms(id,dataset_id,entry_key,normalized,form_type) values(?,?,?,?,?)",forms);
            jdbc.batchUpdate("insert into dictionary_tokens(id,dataset_id,entry_key,token) values(?,?,?,?)",tokens);
        });
    }
}
