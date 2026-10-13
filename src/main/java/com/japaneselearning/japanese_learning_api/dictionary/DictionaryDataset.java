package com.japaneselearning.japanese_learning_api.dictionary;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Table(name="dictionary_datasets") @Getter @Setter @NoArgsConstructor
public class DictionaryDataset {
    @Id @Column(length=64) private String id;
    @Column(nullable=false,length=20) private String status;
    @Column(name="imported_at",nullable=false) private Instant importedAt;
    @Column(name="entry_count",nullable=false) private int entryCount;
    @Column(name="source_url",nullable=false,length=500) private String sourceUrl;
}
