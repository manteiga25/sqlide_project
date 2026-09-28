package com.example.sqlide.DataScience;

import java.util.*;

public class LabelEncoder {

    private final Map<String, Integer> encoder = new HashMap<>();
    private final Map<Integer, String> decoder = new HashMap<>();

    private int id = -1;

    public LabelEncoder() {}

    public LabelEncoder(Collection<String> label) {
        updateEncoder(label);
    }

    public void updateEncoder(Collection<String> label) {
        label.stream().filter(l->!encoder.containsKey(l)).distinct().forEach(label_key->{
            id++;
            encoder.put(label_key, id);
            decoder.put(id, label_key);
        });
    }

    public int encode(String label) {
        return encoder.getOrDefault(label, -1);
    }

    public int[] encode(Collection<String> labels) {
        return labels.stream().mapToInt(l -> encoder.getOrDefault(l, -1)).toArray();
    }

    public String decode(int token) {
        return decoder.getOrDefault(token, "");
    }

    public String[] decode(int[] tokens) {
        return Arrays.stream(tokens)
                .mapToObj(token -> decoder.getOrDefault(token, ""))
                .toArray(String[]::new);
    }

    /** Número de classes distintas vistas até agora. */
    public int size() {
        return encoder.size();
    }

    /** Classes por ordem de codificação, para legendas e matrizes de confusão. */
    public List<String> classes() {
        return java.util.stream.IntStream.range(0, encoder.size())
                .mapToObj(token -> decoder.getOrDefault(token, ""))
                .toList();
    }

    public void flush() {
        id = -1;
        encoder.clear();
        decoder.clear();
    }

}
