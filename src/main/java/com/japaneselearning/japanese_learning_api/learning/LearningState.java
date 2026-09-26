package com.japaneselearning.japanese_learning_api.learning;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;
@Entity @Table(name="learning_states") @Getter @Setter @NoArgsConstructor
public class LearningState {
 @Id private UUID userId;
 @Column(nullable=false) private long revision;
 @Column(nullable=false,columnDefinition="longtext") private String payload;
}
