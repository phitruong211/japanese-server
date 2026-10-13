package com.japaneselearning.japanese_learning_api.dictionary;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="dictionary_forms",indexes=@Index(name="ix_dictionary_form_search",columnList="dataset_id,normalized,form_type,entry_key")) @Getter @Setter @NoArgsConstructor
public class DictionaryForm {
    @Id @Column(length=64) private String id;
    @Column(name="dataset_id",nullable=false,length=64) private String datasetId;
    @Column(name="entry_key",nullable=false,length=40) private String entryKey;
    @Column(nullable=false,length=255) private String normalized;
    @Column(name="form_type",nullable=false,length=10) private String formType;
}
