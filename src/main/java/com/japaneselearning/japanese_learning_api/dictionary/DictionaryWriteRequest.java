package com.japaneselearning.japanese_learning_api.dictionary;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Table(name="dictionary_write_requests") @Getter @Setter @NoArgsConstructor
public class DictionaryWriteRequest {
    @Id @Column(length=180) private String id;
    @Column(name="request_hash",nullable=false,length=64) private String requestHash;
    @Column(name="result_payload",nullable=false,columnDefinition="text") private String resultPayload;
    @Column(name="created_at",nullable=false) private Instant createdAt;
}
