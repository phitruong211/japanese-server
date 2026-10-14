package com.japaneselearning.japanese_learning_api.notes;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.*;

class PersonalCardNoteMigrationTest {
    @Test void migrationCreatesNotesAndPrivateSharingDefaults() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:note_migration;MODE=MySQL", "sa", "")) {
            var statement = connection.createStatement();
            statement.execute("create table app_users(id binary(16) primary key)");
            statement.execute("create table decks(id binary(16) primary key)");
            statement.execute("create table deck_groups(id binary(16) primary key)");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V13__personal_card_notes.sql"));
            statement.execute("insert into decks(id) values(X'00000000000000000000000000000001')");
            try (var result = statement.executeQuery("select share_personal_notes from decks")) {
                assertThat(result.next()).isTrue(); assertThat(result.getBoolean(1)).isFalse();
            }
            statement.execute("insert into app_users values(X'00000000000000000000000000000001')");
            statement.execute("insert into personal_card_notes values(X'00000000000000000000000000000002',X'00000000000000000000000000000001','"+"a".repeat(64)+"','私 — ghi chú',1,CURRENT_TIMESTAMP)");
            statement.execute("delete from app_users");
            try (var result = statement.executeQuery("select count(*) from personal_card_notes")) {
                result.next(); assertThat(result.getInt(1)).isZero();
            }
        }
    }
}
