package com.japaneselearning.japanese_learning_api.dictionary;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="dictionary_entries",uniqueConstraints=@UniqueConstraint(columnNames={"dataset_id","entry_key"})) @Getter @Setter @NoArgsConstructor
public class DictionaryEntry {
    @Id @Column(length=100) private String id;
    @Column(name="dataset_id",nullable=false,length=64) private String datasetId;
    @Column(name="entry_key",nullable=false,length=40) private String entryKey;
    @Column(name="common_word",nullable=false) private boolean commonWord;
    @Column(nullable=false,columnDefinition="longtext") private String payload;
}
