package com.japaneselearning.japanese_learning_api.font;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

/** The font bytes, kept apart from UserFont so listing fonts never loads them. */
@Entity @Table(name = "user_font_files")
@Getter @Setter
public class UserFontFile {
    @Id @Column(name = "font_id") private UUID fontId;
    @Column(nullable = false, columnDefinition = "longblob") private byte[] data;
}
