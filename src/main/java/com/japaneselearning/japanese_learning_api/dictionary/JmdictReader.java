package com.japaneselearning.japanese_learning_api.dictionary;

import javax.xml.stream.*;
import java.io.*;
import java.util.*;
import java.util.function.Consumer;
import static com.japaneselearning.japanese_learning_api.dictionary.DictionaryData.*;

/** Streaming JMdict parser. Internal DTD entities are needed for POS; no external resources. */
public final class JmdictReader {
    public int read(InputStream input, String version, Consumer<Word> consumer) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, true);
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        factory.setProperty("jdk.xml.entityExpansionLimit", "5000000");
        factory.setProperty("jdk.xml.totalEntitySizeLimit", "50000000");
        factory.setProperty("jdk.xml.maxGeneralEntitySizeLimit", "10000");
        factory.setProperty("jdk.xml.maxElementDepth", "64");
        factory.setXMLResolver((publicID, systemID, baseURI, namespace) -> { throw new XMLStreamException("External XML resources are forbidden"); });
        XMLStreamReader r = factory.createXMLStreamReader(input, "UTF-8");
        int count = 0;
        try {
            while (r.hasNext()) {
                int type = r.next();
                if (type == XMLStreamConstants.DTD && r.getText().matches("(?s).*\\b(?:SYSTEM|PUBLIC)\\s+[\"'].*")) throw new XMLStreamException("External DTD/entity forbidden");
                if (type == XMLStreamConstants.START_ELEMENT && r.getLocalName().equals("entry")) {
                    Word word = entry(r, version);
                    if (!word.readings().isEmpty() && !word.senses().isEmpty()) { consumer.accept(word); count++; }
                }
            }
        } finally { r.close(); }
        return count;
    }
    private Word entry(XMLStreamReader r, String version) throws XMLStreamException {
        String key = null; boolean common = false;
        List<String> written = new ArrayList<>(); List<Reading> readings = new ArrayList<>(); List<Sense> senses = new ArrayList<>();
        List<String> inheritedPos = List.of();
        while (r.hasNext()) {
            int type = r.next();
            if (type == XMLStreamConstants.END_ELEMENT && r.getLocalName().equals("entry")) break;
            if (type != XMLStreamConstants.START_ELEMENT) continue;
            String tag = r.getLocalName();
            switch (tag) {
                case "ent_seq" -> key = "jmdict:" + r.getElementText();
                case "k_ele" -> {
                    while (r.hasNext()) {
                        int t = r.next();
                        if (t == XMLStreamConstants.END_ELEMENT && r.getLocalName().equals("k_ele")) break;
                        if (t == XMLStreamConstants.START_ELEMENT && r.getLocalName().equals("keb")) written.add(r.getElementText());
                        else if (t == XMLStreamConstants.START_ELEMENT && r.getLocalName().equals("ke_pri")) common |= priority(r.getElementText());
                    }
                }
                case "r_ele" -> {
                    String text = ""; List<String> restrict = new ArrayList<>(); boolean kanaOnly = false;
                    while (r.hasNext()) {
                        int t = r.next();
                        if (t == XMLStreamConstants.END_ELEMENT && r.getLocalName().equals("r_ele")) break;
                        if (t != XMLStreamConstants.START_ELEMENT) continue;
                        switch (r.getLocalName()) {
                            case "reb" -> text = r.getElementText();
                            case "re_restr" -> restrict.add(r.getElementText());
                            case "re_nokanji" -> kanaOnly = true;
                            case "re_pri" -> common |= priority(r.getElementText());
                        }
                    }
                    readings.add(new Reading(text, List.copyOf(restrict), kanaOnly));
                }
                case "sense" -> {
                    List<String> pos = new ArrayList<>(), wr = new ArrayList<>(), rd = new ArrayList<>(), notes = new ArrayList<>();
                    List<Gloss> glosses = new ArrayList<>();
                    while (r.hasNext()) {
                        int t = r.next();
                        if (t == XMLStreamConstants.END_ELEMENT && r.getLocalName().equals("sense")) break;
                        if (t != XMLStreamConstants.START_ELEMENT) continue;
                        switch (r.getLocalName()) {
                            case "pos" -> pos.add(r.getElementText());
                            case "stagk" -> wr.add(r.getElementText());
                            case "stagr" -> rd.add(r.getElementText());
                            case "s_inf", "misc", "field", "dial" -> notes.add(r.getElementText());
                            case "gloss" -> {
                                String lang = r.getAttributeValue("http://www.w3.org/XML/1998/namespace", "lang");
                                String text = r.getElementText();
                                // This import contract is the EDRDG Japanese/English component only.
                                if (lang == null || lang.equals("eng")) glosses.add(new Gloss(text, "en"));
                            }
                        }
                    }
                    if (pos.isEmpty()) pos.addAll(inheritedPos); else inheritedPos = List.copyOf(pos);
                    if (!glosses.isEmpty()) {
                        String fingerprint = DictionaryNormalizer.hash(String.join("|", pos) + "\n" + wr + "\n" + rd + "\n" + glosses + "\n" + notes);
                        senses.add(new Sense(fingerprint, List.copyOf(pos), List.copyOf(wr), List.copyOf(rd), List.copyOf(glosses), List.copyOf(notes)));
                    }
                }
            }
        }
        if (key == null || !key.matches("jmdict:[0-9]+")) throw new XMLStreamException("Entry has no valid ent_seq");
        return new Word(key, version, List.copyOf(written), List.copyOf(readings), List.copyOf(senses), common, SOURCE, LICENSE);
    }
    private boolean priority(String value) { return value.endsWith("1") || value.matches("nf0[1-9]"); }
}
