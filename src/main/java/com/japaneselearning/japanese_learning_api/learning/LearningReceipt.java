package com.japaneselearning.japanese_learning_api.learning;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;
@Entity @Table(name="learning_migration_receipts", uniqueConstraints=@UniqueConstraint(columnNames={"userId","requestKey"})) @Getter @Setter @NoArgsConstructor
public class LearningReceipt {
 @Id private UUID id;
 @Column(nullable=false) private UUID userId;
 @Column(nullable=false,length=100) private String requestKey;
 @Column(nullable=false,length=64) private String fingerprint;
 @Column(nullable=false,columnDefinition="longtext") private String payload;
}
