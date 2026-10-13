package com.japaneselearning.japanese_learning_api.dictionary;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="dictionary_catalog") @Getter @Setter @NoArgsConstructor
public class DictionaryCatalog {
    @Id private Integer id;
    @Column(name="dataset_id",nullable=false,length=64) private String datasetId;
}
