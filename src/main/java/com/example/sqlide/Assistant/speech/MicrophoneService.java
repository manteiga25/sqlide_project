package com.example.sqlide.Assistant.speech;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class MicrophoneService {

    private final File wavFile = new File("Audio/RecordAudio.wav");

    private final AudioFileFormat.Type fileType = AudioFileFormat.Type.WAVE;

    private TargetDataLine line;

    private AudioFormat getAudioFormat() {
        float sampleRate = 16000;
        int sampleSizeInBits = 8;
        int channels = 2;
        boolean signed = true;
        boolean bigEndian = true;
        return new AudioFormat(sampleRate, sampleSizeInBits,
                channels, signed, bigEndian);
    }

    /**
     * Captures the sound and record into a WAV file
     */
    public void start() throws Exception {
            AudioFormat format = getAudioFormat();
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

            // checks if system supports the data line
            if (!AudioSystem.isLineSupported(info)) {
                System.out.println("Line not supported");
                throw new Exception("No microphone found");
            }
            line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(format);
            line.start();   // start capturing

            System.out.println("Start capturing...");

            AudioInputStream ais = new AudioInputStream(line);

            System.out.println("Start recording...");

            // start recording
            AudioSystem.write(ais, fileType, wavFile);

    }

    /**
     * Closes the target data line to finish capturing and recording
     */
    public void finish() {
        line.stop();
        line.close();
        System.out.println("Finished");
    }

    public void refresh() throws IOException {
        Files.deleteIfExists(wavFile.toPath());
    }

    public MicrophoneService() throws IOException {
        final Path audio = Path.of("Audio");
        if (Files.notExists(audio)) {
            Files.createDirectory(audio);
        }
    }

}
