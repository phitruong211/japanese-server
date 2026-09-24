package com.japaneselearning.japanese_learning_api.model;

public final class DomainTypes {
    private DomainTypes() {}

    public enum Role { USER, ADMIN }
    public enum UserStatus { ACTIVE, LOCKED, DISABLED }
    public enum SourceType { MANUAL, IMPORT, SYSTEM }
    public enum Visibility { PRIVATE, PUBLIC }
    public enum CardKind { VOCABULARY, KANJI, GRAMMAR, GENERAL }
    public enum AnkiState { NEW, LEARNING, REVIEW, RELEARNING }
    public enum AnkiRating { AGAIN, HARD, GOOD, EASY }
}
