package com.japaneselearning.japanese_learning_api.font;

import com.japaneselearning.japanese_learning_api.common.ApiException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FontFileValidatorTest {
    private static void rejects(String filename, byte[] data, String reason) {
        assertThatThrownBy(() -> FontFileValidator.validate(filename, data)).as(reason).isInstanceOf(ApiException.class);
    }

    @Test void acceptsStructurallyValidTtfAndWoff2() {
        var ttf = FontFileValidator.validate("My Font.TTF", FontTestData.ttf(1, 400));
        assertThat(ttf.format()).isEqualTo("ttf");
        assertThat(ttf.sha256()).hasSize(64);
        assertThat(FontFileValidator.validate("a.woff2", FontTestData.woff2(1, 600)).format()).isEqualTo("woff2");
        assertThat(FontFileValidator.validate("a.ttf", FontTestData.ttf(2, 400)).sha256()).isNotEqualTo(ttf.sha256());
    }

    @Test void rejectsWrongOrMismatchedTypes() {
        rejects("a.otf", FontTestData.ttf(1, 400), "extension not allowed");
        rejects("a.woff", FontTestData.ttf(1, 400), "woff not allowed");
        rejects("a.woff2", FontTestData.ttf(1, 400), "ttf bytes under a woff2 name");
        rejects("a.ttf", FontTestData.woff2(1, 600), "woff2 bytes under a ttf name");
        rejects("a.ttf", new byte[2000], "zeroes");
        rejects("a.ttf", "<html>not a font</html>".repeat(40).getBytes(), "text");
        rejects("a.png", new byte[]{(byte) 0x89, 'P', 'N', 'G'}, "png");
        rejects(null, FontTestData.ttf(1, 400), "no name");
    }

    @Test void rejectsTruncatedOversizedAndEmptyFiles() {
        rejects("a.ttf", new byte[0], "empty");
        rejects("a.ttf", java.util.Arrays.copyOf(FontTestData.ttf(1, 400), 100), "tiny");
        var whole = FontTestData.ttf(1, 400);
        rejects("a.ttf", java.util.Arrays.copyOf(whole, 120), "truncated directory");
        rejects("a.ttf", new byte[FontFileValidator.MAX_BYTES + 1], "over the limit");
    }

    @Test void rejectsBrokenTtfStructure() {
        rejects("a.ttf", FontTestData.ttf(1, 400, Map.of("drop:cmap", "")), "missing cmap");
        rejects("a.ttf", FontTestData.ttf(1, 400, Map.of("drop:glyf", "", "drop:loca", "")), "no outlines");
        rejects("a.ttf", FontTestData.ttf(1, 400, Map.of("magic", "")), "bad head magic");
        rejects("a.ttf", FontTestData.ttf(1, 400, Map.of("overrun:glyf", "")), "table outside the file");
        rejects("a.ttf", FontTestData.ttf(1, 400, Map.of("rename:loca", "glyf")), "duplicate table");
    }

    @Test void rejectsBrokenWoff2Header() {
        rejects("a.woff2", FontTestData.woff2(1, 600, 7, 0), "declared length differs");
        rejects("a.woff2", FontTestData.woff2(1, 600, 0, 64L * 1024 * 1024), "decompression bomb size");
        rejects("a.woff2", FontTestData.woff2(1, 100), "too small");
    }
}
