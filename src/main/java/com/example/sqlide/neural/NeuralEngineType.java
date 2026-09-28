package com.example.sqlide.neural;

import ai.djl.engine.Engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Motor de aprendizagem profunda a usar.
 *
 * <p>O DJL escolhe o motor pelo nome; a biblioteca nativa é descarregada no primeiro
 * arranque de cada um e fica em {@code ~/.djl.ai}. Por isso o programa pergunta antes de
 * treinar em vez de assumir: mudar de motor pode significar esperar por um download de
 * algumas centenas de megabytes.</p>
 */
public enum NeuralEngineType {

    PYTORCH("PyTorch", "PyTorch"),
    TENSORFLOW("TensorFlow", "TensorFlow");

    private final String label;
    private final String engineName;

    NeuralEngineType(String label, String engineName) {
        this.label = label;
        this.engineName = engineName;
    }

    /** Nome pelo qual o DJL conhece o motor. */
    public String getEngineName() {
        return engineName;
    }

    /**
     * True se o motor está no classpath.
     *
     * <p>É uma verificação barata: lê os motores registados sem tocar nas bibliotecas
     * nativas. Chamar {@code Engine.getEngine(...)} para saber se um motor existe
     * <em>descarrega</em> o nativo — várias centenas de megabytes — e bloqueia até acabar,
     * o que não pode acontecer só por se abrir uma janela.</p>
     */
    public boolean isRegistered() {
        try {
            return Engine.getAllEngines().contains(engineName);
        } catch (Throwable _) {
            return false;
        }
    }

    /** True se o nativo já está descarregado, sem o ir buscar caso não esteja. */
    public boolean isDownloaded() {
        final java.nio.file.Path cache = java.nio.file.Path.of(
                System.getProperty("user.home"), ".djl.ai", engineName.toLowerCase(java.util.Locale.ROOT));
        return java.nio.file.Files.isDirectory(cache);
    }

    /**
     * Carrega o motor, descarregando o nativo se ainda não estiver.
     *
     * <p>Só deve ser chamado a partir de uma thread de fundo, e depois de avisar o
     * utilizador quando {@link #isDownloaded()} é falso.</p>
     */
    public Engine load() {
        return Engine.getEngine(engineName);
    }

    /** Estado do motor, sem provocar downloads. */
    public String describe() {
        if (!isRegistered()) return label + " (not on the classpath)";
        return label + (isDownloaded() ? " (ready)" : " (native library not downloaded yet)");
    }

    /** Motores que existem no classpath, prontos ou por descarregar. */
    public static List<NeuralEngineType> registered() {
        final List<NeuralEngineType> found = new ArrayList<>();
        for (NeuralEngineType type : values()) if (type.isRegistered()) found.add(type);
        return found;
    }

    /** Motores que já podem ser usados sem esperar por um download. */
    public static List<NeuralEngineType> ready() {
        final List<NeuralEngineType> found = new ArrayList<>();
        for (NeuralEngineType type : values()) if (type.isRegistered() && type.isDownloaded()) found.add(type);
        return found;
    }

    @Override
    public String toString() {
        return label;
    }

}
