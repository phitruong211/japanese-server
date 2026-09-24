package com.japaneselearning.japanese_learning_api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_users")
@Getter @Setter @NoArgsConstructor
public class User {
    @Id private UUID id;
    @Column(nullable = false, length = 320) private String email;
    @Column(name = "password_hash", nullable = false) private String passwordHash;
    @Column(name = "display_name", nullable = false, length = 100) private String displayName;
    @Column(name = "avatar_url", columnDefinition = "text") private String avatarUrl;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private DomainTypes.Role role = DomainTypes.Role.USER;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private DomainTypes.UserStatus status = DomainTypes.UserStatus.ACTIVE;
    @Column(name = "email_verified_at") private Instant emailVerifiedAt;
    @Column(nullable = false, length = 10) private String locale = "vi-VN";
    @Column(nullable = false, length = 50) private String timezone = "Asia/Bangkok";
    @Column(name = "last_login_at") private Instant lastLoginAt;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;
}
