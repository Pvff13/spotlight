package com.spotlight;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.audio.AudioPlayer;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Plays the user's custom .wav files through RuneLite's AudioPlayer.
 * AudioPlayer is fire-and-forget (no looping or "is it still playing"), so repeats are
 * scheduled here using the clip length read from the wav header.
 */
@Slf4j
public class SpotlightSounds {

    // Used when a wav header can't be read, so repeats still get spaced out
    private static final long FALLBACK_DURATION_MS = 2000;

    private final AudioPlayer audioPlayer;
    private final File[] files;

    private final long[] cachedMTime;
    private final long[] durationMs;
    private final int[] playsRemaining;
    private final long[] playingUntil;

    public SpotlightSounds(AudioPlayer audioPlayer, File[] files) {
        this.audioPlayer = audioPlayer;
        this.files = files;
        cachedMTime = new long[files.length];
        durationMs = new long[files.length];
        playsRemaining = new int[files.length];
        playingUntil = new long[files.length];
    }

    /** Plays the sound {@code times} times back to back, restarting it if it is already playing. */
    public void play(int index, int times) {
        if (!files[index].exists()) {
            return;
        }
        playsRemaining[index] = Math.max(1, times);
        playingUntil[index] = 0;
        startNext(index);
    }

    public boolean isPlaying(int index) {
        return playsRemaining[index] > 0 || System.currentTimeMillis() < playingUntil[index];
    }

    /** Call frequently (every frame) to start queued repeats once the previous play finishes. */
    public void update() {
        long now = System.currentTimeMillis();
        for (int i = 0; i < files.length; i++) {
            if (playsRemaining[i] > 0 && now >= playingUntil[i]) {
                startNext(i);
            }
        }
    }

    public void stopAll() {
        for (int i = 0; i < files.length; i++) {
            playsRemaining[i] = 0;
        }
    }

    private void startNext(int index) {
        playsRemaining[index]--;
        File file = files[index];
        try {
            audioPlayer.play(file, 0f);
            playingUntil[index] = System.currentTimeMillis() + getDurationMs(index);
        } catch (Exception e) {
            log.warn("Unable to play sound {}", file.getName(), e);
            playsRemaining[index] = 0;
        }
    }

    private long getDurationMs(int index) {
        File file = files[index];
        long mtime = file.lastModified();
        if (mtime != cachedMTime[index]) {
            cachedMTime[index] = mtime;
            durationMs[index] = readWavDurationMs(file);
        }
        return durationMs[index];
    }

    /** Reads byte rate from the "fmt " chunk and the "data" chunk size from a RIFF/WAVE header. */
    static long readWavDurationMs(File file) {
        try (InputStream in = new FileInputStream(file); DataInputStream data = new DataInputStream(in)) {
            byte[] tag = new byte[4];
            data.readFully(tag);
            if (!"RIFF".equals(new String(tag, "US-ASCII"))) {
                return FALLBACK_DURATION_MS;
            }
            readIntLE(data);
            data.readFully(tag);
            if (!"WAVE".equals(new String(tag, "US-ASCII"))) {
                return FALLBACK_DURATION_MS;
            }
            long byteRate = 0;
            while (true) {
                data.readFully(tag);
                String chunk = new String(tag, "US-ASCII");
                long size = readIntLE(data) & 0xFFFFFFFFL;
                if (chunk.equals("fmt ")) {
                    data.skipBytes(8); // audio format, channels, sample rate
                    byteRate = readIntLE(data) & 0xFFFFFFFFL;
                    skipFully(data, size - 12 + (size & 1));
                } else if (chunk.equals("data")) {
                    if (byteRate <= 0) {
                        return FALLBACK_DURATION_MS;
                    }
                    return Math.max(100, size * 1000 / byteRate);
                } else {
                    skipFully(data, size + (size & 1));
                }
            }
        } catch (IOException e) {
            log.debug("Could not read wav header of {}", file.getName(), e);
            return FALLBACK_DURATION_MS;
        }
    }

    private static int readIntLE(DataInputStream in) throws IOException {
        int b0 = in.readUnsignedByte(), b1 = in.readUnsignedByte(), b2 = in.readUnsignedByte(), b3 = in.readUnsignedByte();
        return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
    }

    private static void skipFully(DataInputStream in, long n) throws IOException {
        while (n > 0) {
            long skipped = in.skip(n);
            if (skipped <= 0) {
                throw new IOException("Unexpected end of wav file");
            }
            n -= skipped;
        }
    }
}
