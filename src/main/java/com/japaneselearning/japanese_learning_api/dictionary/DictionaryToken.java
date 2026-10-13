package com.japaneselearning.japanese_learning_api.dictionary;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="dictionary_tokens",indexes=@Index(name="ix_dictionary_token_search",columnList="dataset_id,token,entry_key")) @Getter @Setter @NoArgsConstructor
public class DictionaryToken {
    @Id @Column(length=64) private String id;
    @Column(name="dataset_id",nullable=false,length=64) private String datasetId;
    @Column(name="entry_key",nullable=false,length=40) private String entryKey;
    @Column(nullable=false,length=100) private String token;
}
