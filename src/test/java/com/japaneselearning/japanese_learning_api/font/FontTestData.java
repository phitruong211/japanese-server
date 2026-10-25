package com.japaneselearning.japanese_learning_api.font;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

/** Minimal binary fonts that pass the structural checks, built byte by byte. */
final class FontTestData {
    private FontTestData() {}

    static byte[] ttf(int salt, int extraBytes) { return ttf(salt, extraBytes, Map.of()); }

    /** `tweak` can rename ("tag" -> "newTag" via key "rename:head"), drop ("drop:cmap") or break ("magic") parts. */
    static byte[] ttf(int salt, int extraBytes, Map<String, String> tweak) {
        Map<String, Integer> tables = new LinkedHashMap<>();
        for (String tag : new String[]{"cmap", "head", "hhea", "hmtx", "maxp", "glyf", "loca"}) tables.put(tag, tag.equals("head") ? 54 : 32);
        tweak.keySet().stream().filter(k -> k.startsWith("drop:")).forEach(k -> tables.remove(k.substring(5)));
        tables.put("glyf", 32 + extraBytes);
        int count = tables.size();
        int dir = 12 + 16 * count;
        int total = dir + tables.values().stream().mapToInt(v -> (v + 3) & ~3).sum() + 256;
        ByteBuffer b = ByteBuffer.allocate(total);
        b.putInt(0x00010000).putShort((short) count).putShort((short) 0).putShort((short) 0).putShort((short) 0);
        int offset = dir, head = -1;
        for (var entry : tables.entrySet()) {
            String tag = tweak.getOrDefault("rename:" + entry.getKey(), entry.getKey());
            for (char c : tag.toCharArray()) b.put((byte) c);
            b.putInt(0).putInt(tweak.containsKey("overrun:" + entry.getKey()) ? total + 10 : offset).putInt(entry.getValue());
            if (entry.getKey().equals("head")) head = offset;
            offset += (entry.getValue() + 3) & ~3;
        }
        byte[] out = b.array();
        if (head >= 0 && !tweak.containsKey("magic")) ByteBuffer.wrap(out).putInt(head + 12, 0x5F0F3CF5);
        ByteBuffer.wrap(out).putInt(total - 4, salt); // distinct bytes give distinct hashes
        return out;
    }

    static byte[] woff2(int salt, int payload) { return woff2(salt, payload, 0, 0); }

    static byte[] woff2(int salt, int payload, int lengthDelta, long unpackedOverride) {
        int length = 48 + payload;
        ByteBuffer b = ByteBuffer.allocate(length);
        b.putInt(0x774F4632).putInt(0x00010000).putInt(length + lengthDelta).putShort((short) 12).putShort((short) 0);
        b.putInt((int) (unpackedOverride != 0 ? unpackedOverride : 4096)).putInt(payload).putShort((short) 1).putShort((short) 0);
        byte[] out = b.array();
        ByteBuffer.wrap(out).putInt(length - 4, salt);
        return out;
    }
}
