package com.example.sqlide.Assistant.llm;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

/**
 * Guarda as definições do assistente em {@code ~/.sqlide/assistant.json}.
 *
 * <p>Fica fora da pasta do projeto de propósito: o ficheiro contém a chave de API que o
 * utilizador introduziu e não tem nada que ir parar ao git.</p>
 */
public final class LlmConfigStore {

    private static final Path DIRECTORY = Paths.get(System.getProperty("user.home"), ".sqlide");
    private static final Path FILE = DIRECTORY.resolve("assistant.json");

    private static LlmConfig cached;

    private LlmConfigStore() {
    }

    public static synchronized LlmConfig load() {
        if (cached != null) return cached;
        try {
            if (Files.exists(FILE)) {
                String content = Files.readString(FILE, StandardCharsets.UTF_8);
                if (!content.isBlank()) {
                    cached = LlmConfig.fromJson(new JSONObject(content));
                    return cached;
                }
            }
        } catch (Exception e) {
            // Um ficheiro corrompido não deve impedir o assistente de abrir; recomeça em branco.
            System.err.println("Could not read " + FILE + ": " + e.getMessage());
        }
        cached = new LlmConfig();
        return cached;
    }

    /**
     * Relê do disco, ignorando a cópia em memória.
     *
     * <p>É o que a janela de definições usa ao abrir: assim mostra sempre o que está
     * gravado, em vez de um estado que ficou pendurado de uma sessão anterior.</p>
     */
    public static synchronized LlmConfig reload() {
        cached = null;
        return load();
    }

    public static synchronized void save(LlmConfig config) throws IOException {
        Files.createDirectories(DIRECTORY);
        Files.writeString(FILE, config.toJson().toString(2), StandardCharsets.UTF_8);
        restrictPermissions();
        cached = config.copy();
    }

    public static Path getFile() {
        return FILE;
    }

    /** Em sistemas POSIX deixa o ficheiro só legível pelo dono; no Windows não se aplica. */
    private static void restrictPermissions() {
        try {
            if (Files.getFileStore(FILE).supportsFileAttributeView("posix")) {
                Files.setPosixFilePermissions(FILE,
                        Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            }
        } catch (Exception _) {
            // Sem suporte a POSIX (Windows) — nada a fazer.
        }
    }

}
