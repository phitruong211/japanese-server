package com.japaneselearning.japanese_learning_api.dictionary;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.nio.file.Path;
@Component @ConditionalOnProperty(name="dictionary.import.file")
public class DictionaryImportCommand implements ApplicationRunner {
    private final DictionaryImportService importer;
    @Value("${dictionary.import.file}") private String file;
    @Value("${dictionary.import.minimum-entries:150000}") private int minimum;
    public DictionaryImportCommand(DictionaryImportService importer) { this.importer=importer; }
    @Override public void run(ApplicationArguments args) throws Exception {
        System.out.println("Activated dictionary: " + importer.importFile(Path.of(file), minimum));
    }
}
