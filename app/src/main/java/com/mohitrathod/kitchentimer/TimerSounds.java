package com.mohitrathod.kitchentimer;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public final class TimerSounds {
    private static final int SAMPLE_RATE = 22050;
    private static final String ALARM_FILE = "alarm_v1.wav";

    private final SoundPool alarmPool;
    private int alarmId;

    private boolean alarmLoaded;
    private boolean alarmPending;
    private int alarmStreamId;

    public TimerSounds(Context context) {
        alarmPool = new SoundPool.Builder()
                .setMaxStreams(1)
                .setAudioAttributes(audioAttributes(
                        AudioAttributes.USAGE_ALARM,
                        AudioAttributes.CONTENT_TYPE_SONIFICATION
                ))
                .build();

        alarmPool.setOnLoadCompleteListener((soundPool, sampleId, status) -> {
            if (status == 0 && sampleId == alarmId) {
                alarmLoaded = true;
                if (alarmPending) {
                    alarmPending = false;
                    playAlarm();
                }
            }
        });

        alarmId = load(alarmPool, context, ALARM_FILE, generateAlarm());
    }

    public void startAlarm() {
        if (alarmStreamId != 0) {
            return;
        }
        if (!alarmLoaded) {
            alarmPending = true;
            return;
        }
        playAlarm();
    }

    public void stopAlarm() {
        alarmPending = false;
        if (alarmStreamId != 0) {
            alarmPool.stop(alarmStreamId);
            alarmStreamId = 0;
        }
    }

    public void release() {
        stopAlarm();
        alarmPool.release();
    }

    private void playAlarm() {
        alarmStreamId = alarmPool.play(alarmId, 0.9f, 0.9f, 1, -1, 1f);
        if (alarmStreamId == 0) {
            alarmPending = true;
        }
    }

    private static AudioAttributes audioAttributes(int usage, int contentType) {
        return new AudioAttributes.Builder()
                .setUsage(usage)
                .setContentType(contentType)
                .build();
    }

    private static int load(SoundPool pool, Context context, String name, short[] pcm) {
        File file = new File(context.getCacheDir(), name);
        if (!file.exists()) {
            try {
                writeWav(file, pcm);
            } catch (IOException exception) {
                return 0;
            }
        }
        return pool.load(file.getAbsolutePath(), 1);
    }

    private static short[] generateAlarm() {
        double[] melody = {1046.50, 1318.51, 1568.00, 1318.51,
                1174.66, 1396.91, 1760.00, 1396.91};
        float noteSeconds = 0.22f;
        float gapSeconds = 0.03f;
        int noteSamples = (int) (noteSeconds * SAMPLE_RATE);
        int gapSamples = (int) (gapSeconds * SAMPLE_RATE);
        short[] pcm = new short[melody.length * (noteSamples + gapSamples)];
        int offset = 0;

        for (double frequency : melody) {
            for (int i = 0; i < noteSamples; i++) {
                double t = i / (double) SAMPLE_RATE;
                double attack = Math.min(1.0, t / 0.012);
                double release = Math.min(1.0, (noteSeconds - t) / 0.055);
                double envelope = Math.max(0.0, attack * release);
                double value = Math.sin(2.0 * Math.PI * frequency * t) * 0.55
                        + Math.sin(2.0 * Math.PI * frequency * 2.0 * t) * 0.24
                        + Math.sin(2.0 * Math.PI * frequency * 3.0 * t) * 0.08;
                pcm[offset + i] = (short) (value * envelope * Short.MAX_VALUE * 0.72);
            }
            offset += noteSamples + gapSamples;
        }
        return pcm;
    }

    private static void writeWav(File file, short[] pcm) throws IOException {
        int dataLength = pcm.length * 2;
        ByteBuffer buffer = ByteBuffer.allocate(44 + dataLength).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(36 + dataLength);
        buffer.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        buffer.put("fmt ".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(16);
        buffer.putShort((short) 1);
        buffer.putShort((short) 1);
        buffer.putInt(SAMPLE_RATE);
        buffer.putInt(SAMPLE_RATE * 2);
        buffer.putShort((short) 2);
        buffer.putShort((short) 16);
        buffer.put("data".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(dataLength);
        for (short sample : pcm) {
            buffer.putShort(sample);
        }

        try (BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(file))) {
            output.write(buffer.array());
        }
    }
}
