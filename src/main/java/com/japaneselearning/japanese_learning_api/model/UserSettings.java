package com.japaneselearning.japanese_learning_api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_settings")
@Getter @Setter @NoArgsConstructor
public class UserSettings {
    @Id @Column(name = "user_id") private UUID userId;
    @MapsId @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id") private User user;
    @Column(nullable = false, length = 30) private String theme = "light";
    @Column(name = "font_size", nullable = false, length = 20) private String fontSize = "medium";
    @Column(name = "show_furigana", nullable = false) private boolean showFurigana = true;
    @Column(name = "auto_play_audio", nullable = false) private boolean autoPlayAudio;
    @Column(name = "daily_goal", nullable = false) private int dailyGoal = 20;
    @Column(name = "anki_session_minutes", nullable = false) private int ankiSessionMinutes;
    @Column(name = "reduced_motion", nullable = false) private boolean reducedMotion;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;
}
