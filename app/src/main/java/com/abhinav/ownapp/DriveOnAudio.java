package com.abhinav.ownapp;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressWarnings("all")
public class DriveOnAudio {

    private static final int STATE_OFF      = 0;
    private static final int STATE_STARTING = 1;
    private static final int STATE_RUNNING  = 2;
    private static final int STATE_STOPPING = 3;

    private volatile boolean soundEnabled = true;
    private volatile boolean vibrationEnabled = true;
    private volatile boolean streamAlive = false;

    private ExecutorService sfxExecutor;
    private ToneGenerator toneGenerator;
    private final Vibrator vibrator;

    // Zero-Lag Continuous Audio Stream State
    private volatile int engineState = STATE_RUNNING;
    private volatile int stateSampleCounter = 0;
    private volatile int currentVehicleModel = DriveOnCar.MODEL_SPORTS_GT;
    private volatile float targetRpm = 850f;
    private volatile float currentThrottle = 0f;
    private Thread engineThread;

    private long lastScreechTime = 0;
    private long lastBrakeTime = 0;
    private long lastNitroTime = 0;
    private long lastCrashTime = 0;

    public DriveOnAudio(Context context) {
        this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        try {
            this.sfxExecutor = Executors.newFixedThreadPool(3);
            this.toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 55);
        } catch (Exception e) {
            this.sfxExecutor = null;
            this.toneGenerator = null;
        }
        ensureHotAudioStream();
    }

    private synchronized void ensureHotAudioStream() {
        if (streamAlive && engineThread != null && engineThread.isAlive()) return;
        streamAlive = true;
        engineThread = new Thread(this::runContinuousEngineLoop, "DriveOnEngineAudio");
        engineThread.setPriority(Thread.MAX_PRIORITY);
        engineThread.start();
    }

    public void setSoundEnabled(boolean enabled) {
        this.soundEnabled = enabled;
    }

    public void setVibrationEnabled(boolean enabled) {
        this.vibrationEnabled = enabled;
    }

    public synchronized void release() {
        streamAlive = false;
        engineState = STATE_OFF;
        if (engineThread != null) {
            engineThread.interrupt();
            engineThread = null;
        }
        if (sfxExecutor != null) { sfxExecutor.shutdownNow(); sfxExecutor = null; }
        if (toneGenerator != null) { toneGenerator.release(); toneGenerator = null; }
    }

    /**
     * Triggers an immediate (<16ms lag) vehicle-specific Engine Ignition sequence.
     */
    public void startEngineIgnition(int vehicleModel) {
        ensureHotAudioStream();
        this.currentVehicleModel = vehicleModel;
        this.stateSampleCounter = 0;
        this.engineState = STATE_STARTING;
        vibrateOneShot(65, 210);
    }

    /**
     * Triggers an immediate (<16ms lag) vehicle-specific Engine Shutdown sequence.
     */
    public void stopEngineLoop() {
        ensureHotAudioStream();
        if (this.engineState != STATE_OFF) {
            this.stateSampleCounter = 0;
            this.engineState = STATE_STOPPING;
            vibrateOneShot(45, 150);
        }
    }

    /**
     * Instantly switches the active vehicle sound profile (with a subtle signature rev flare).
     */
    public void switchVehicleImmediate(int newVehicleModel) {
        ensureHotAudioStream();
        this.currentVehicleModel = newVehicleModel;
        if (this.engineState == STATE_RUNNING) {
            // Brief rev/spool flare so the user hears the new vehicle immediately
            this.stateSampleCounter = 5500;
            this.engineState = STATE_STARTING;
        }
    }

    public void updateEngineTelemetry(int vehicleModel, float speedKmh, int gear, boolean throttle, boolean boosting) {
        this.currentVehicleModel = vehicleModel;
        if (this.engineState == STATE_OFF || this.engineState == STATE_STOPPING) return;

        float gearMaxSpd = Math.max(32f, gear * 44f);
        float gearRatio = Math.min(1.25f, Math.max(0f, speedKmh / gearMaxSpd));
        float idleRpm = (vehicleModel == DriveOnCar.MODEL_HEAVY_TRUCK) ? 620f
                : (vehicleModel == DriveOnCar.MODEL_SUPERBIKE ? 1300f
                   : (vehicleModel == DriveOnCar.MODEL_HELICOPTER ? 1500f : 820f));
        float maxRpm = (vehicleModel == DriveOnCar.MODEL_HEAVY_TRUCK) ? 2800f
                : (vehicleModel == DriveOnCar.MODEL_SUPERBIKE ? 9500f
                   : (vehicleModel == DriveOnCar.MODEL_HELICOPTER ? 4500f : 6200f));

        float calcRpm = idleRpm + gearRatio * (maxRpm - idleRpm);
        if (throttle) calcRpm += (maxRpm - idleRpm) * 0.14f;
        if (boosting) calcRpm *= 1.18f;

        this.targetRpm = calcRpm;
        this.currentThrottle = (throttle || boosting) ? 1.0f : 0.25f;
    }

    private void runContinuousEngineLoop() {
        final int sampleRate = 22050;
        final int chunkSamples = 368; // ~16.6ms ultra-low latency buffer (1 frame at 60fps)
        int minBuf = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int bufferBytes = Math.max(minBuf, chunkSamples * 4);

        AudioTrack track = null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                track = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_GAME)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build())
                        .setBufferSizeInBytes(bufferBytes)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build();
            } else {
                track = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                        bufferBytes, AudioTrack.MODE_STREAM);
            }
            track.play();

            short[] buffer = new short[chunkSamples];
            double phase = 0.0, subPhase = 0.0, auxPhase = 0.0, crankPhase = 0.0;
            float smoothRpm = 850f, smoothLoad = 0.3f;
            double lpState1 = 0.0, lpState2 = 0.0, heliWindLp = 0.0;
            long rng = 0x12345678L;

            while (streamAlive && !Thread.currentThread().isInterrupted()) {
                if (!soundEnabled) {
                    Thread.sleep(40);
                    continue;
                }

                int model = currentVehicleModel;
                for (int i = 0; i < chunkSamples; i++) {
                    rng ^= (rng << 13); rng ^= (rng >>> 17); rng ^= (rng << 5);
                    double whiteNoise = ((rng & 0xFFFF) / 32768.0) - 1.0;

                    int state = engineState;
                    double sample = 0.0;

                    if (state == STATE_OFF) {
                        buffer[i] = 0;
                        continue;
                    } else if (state == STATE_STARTING) {
                        int step = stateSampleCounter++;
                        int totalStartSamples = (model == DriveOnCar.MODEL_HELICOPTER) ? 16500
                                : (model == DriveOnCar.MODEL_HEAVY_TRUCK ? 15000 : 11500);

                        if (step >= totalStartSamples) {
                            engineState = STATE_RUNNING;
                        } else {
                            double p = (double) step / totalStartSamples;
                            if (model == DriveOnCar.MODEL_HELICOPTER) {
                                // HELICOPTER START: Igniter ticks + rising turbine spool + accelerating rotor blades
                                double tickGate = (p < 0.35 && ((step / 420) % 2 == 0)) ? Math.exp(-((step % 420) / 80.0)) : 0.0;
                                double igniterTick = whiteNoise * tickGate * 0.55;

                                double spoolHz = 280.0 + Math.pow(p, 1.4) * 920.0;
                                subPhase += (2.0 * Math.PI * spoolHz) / sampleRate;
                                double turbineWhine = Math.sin(subPhase) * (0.18 + 0.25 * p);

                                double rotorHz = 2.5 + p * p * 11.5;
                                phase += (2.0 * Math.PI * rotorHz) / sampleRate;
                                double bladePulse = Math.pow(0.5 + 0.5 * Math.sin(phase), 5.0);
                                auxPhase += (2.0 * Math.PI * 52.0) / sampleRate;
                                heliWindLp += 0.12 * (whiteNoise - heliWindLp);
                                double rotorSlap = (Math.sin(auxPhase) * 0.9 + heliWindLp * 1.4) * bladePulse * p;

                                sample = (igniterTick + turbineWhine + rotorSlap) * 0.55;
                            } else if (model == DriveOnCar.MODEL_SUPERBIKE) {
                                // SUPERBIKE START: Fast high-pitched starter whine -> screaming inline-4 rev flare
                                if (p < 0.32) {
                                    crankPhase += (2.0 * Math.PI * 11.0) / sampleRate;
                                    double crankMod = 0.4 + 0.6 * Math.max(0.0, Math.sin(crankPhase));
                                    phase += (2.0 * Math.PI * 135.0) / sampleRate;
                                    sample = (Math.sin(phase) + Math.sin(phase * 2.0) * 0.5 + whiteNoise * 0.2) * crankMod * 0.38;
                                } else {
                                    double revP = (p - 0.32) / 0.68;
                                    double flare = Math.sin(revP * Math.PI);
                                    double hz = 75.0 + flare * 130.0;
                                    phase += (2.0 * Math.PI * hz) / sampleRate;
                                    double scream = Math.sin(phase) + 0.7 * Math.sin(phase * 2.0) + 0.45 * Math.sin(phase * 3.0);
                                    sample = Math.tanh((scream + whiteNoise * 0.3) * (1.8 + flare)) * 0.45;
                                }
                            } else if (model == DriveOnCar.MODEL_HEAVY_TRUCK) {
                                // HEAVY TRUCK START: Slow heavy 24V diesel compression crank -> deep turbo-diesel roar
                                if (p < 0.48) {
                                    crankPhase += (2.0 * Math.PI * 4.2) / sampleRate;
                                    double comp = 0.3 + 0.7 * Math.pow(Math.max(0.0, Math.sin(crankPhase)), 2.0);
                                    phase += (2.0 * Math.PI * (42.0 + 12.0 * Math.sin(crankPhase))) / sampleRate;
                                    sample = Math.tanh((Math.sin(phase) * 2.2 + whiteNoise * 0.35) * comp) * 0.48;
                                } else {
                                    double revP = (p - 0.48) / 0.52;
                                    double flare = Math.sin(revP * Math.PI);
                                    double hz = 24.0 + flare * 28.0;
                                    phase += (2.0 * Math.PI * hz) / sampleRate;
                                    subPhase += (2.0 * Math.PI * (650.0 + flare * 550.0)) / sampleRate;
                                    double diesel = Math.sin(phase) + 0.8 * Math.sin(phase * 0.5) + Math.sin(subPhase) * 0.15;
                                    sample = Math.tanh((diesel + whiteNoise * 0.45) * 2.1) * 0.46;
                                }
                            } else {
                                // SPORTS GT / BUGGY START: Crisp starter crank -> aggressive V8 / Boxer bark
                                if (p < 0.38) {
                                    crankPhase += (2.0 * Math.PI * 7.5) / sampleRate;
                                    double crankMod = 0.35 + 0.65 * Math.max(0.0, Math.sin(crankPhase));
                                    phase += (2.0 * Math.PI * 64.0) / sampleRate;
                                    sample = (Math.tanh(Math.sin(phase) * 2.4) + whiteNoise * 0.25) * crankMod * 0.42;
                                } else {
                                    double revP = (p - 0.38) / 0.62;
                                    double flare = Math.sin(revP * Math.PI);
                                    double hz = 42.0 + flare * 68.0;
                                    phase += (2.0 * Math.PI * hz) / sampleRate;
                                    double v8 = Math.sin(phase) + 0.65 * Math.sin(phase * 2.0) + 0.55 * Math.sin(phase * 0.5);
                                    sample = Math.tanh((v8 + whiteNoise * 0.45) * (1.9 + flare)) * 0.45;
                                }
                            }
                        }
                    } else if (state == STATE_STOPPING) {
                        int step = stateSampleCounter++;
                        int totalStopSamples = (model == DriveOnCar.MODEL_HELICOPTER) ? 14000
                                : (model == DriveOnCar.MODEL_HEAVY_TRUCK ? 12000 : 8500);

                        if (step >= totalStopSamples) {
                            engineState = STATE_OFF;
                            buffer[i] = 0;
                            continue;
                        } else {
                            double p = (double) step / totalStopSamples;
                            double decay = 1.0 - p;

                            if (model == DriveOnCar.MODEL_HELICOPTER) {
                                // HELICOPTER SHUTDOWN: Descending turbine whine + slowing heavy rotor blade whooshes
                                double spoolHz = 1100.0 - Math.pow(p, 0.75) * 860.0;
                                subPhase += (2.0 * Math.PI * spoolHz) / sampleRate;
                                double whine = Math.sin(subPhase) * 0.28 * decay;

                                double rotorHz = 14.0 * Math.pow(decay, 1.3) + 1.5;
                                phase += (2.0 * Math.PI * rotorHz) / sampleRate;
                                double bladePulse = Math.pow(0.5 + 0.5 * Math.sin(phase), 5.0);
                                auxPhase += (2.0 * Math.PI * 48.0) / sampleRate;
                                heliWindLp += 0.10 * (whiteNoise - heliWindLp);
                                double slap = (Math.sin(auxPhase) * 0.8 + heliWindLp * 1.3) * bladePulse * decay;

                                sample = (whine + slap) * 0.55;
                            } else if (model == DriveOnCar.MODEL_HEAVY_TRUCK) {
                                // HEAVY TRUCK SHUTDOWN: Heavy diesel shudder + pneumatic air-brake hiss ("pssshhhh")
                                if (p < 0.45) {
                                    double dp = p / 0.45;
                                    double hz = 26.0 * (1.0 - dp * 0.75);
                                    phase += (2.0 * Math.PI * hz) / sampleRate;
                                    double shudder = Math.sin(phase) * (1.0 - dp);
                                    sample = Math.tanh((shudder + whiteNoise * 0.3) * 2.2) * 0.45;
                                } else {
                                    double hp = (p - 0.45) / 0.55;
                                    double hissEnv = Math.sin(hp * Math.PI) * (1.0 - hp * 0.5);
                                    lpState1 += 0.45 * (whiteNoise - lpState1);
                                    sample = lpState1 * hissEnv * 0.48;
                                }
                            } else if (model == DriveOnCar.MODEL_SUPERBIKE) {
                                // SUPERBIKE SHUTDOWN: Rapid inline-4 spin down + mechanical relay click
                                double hz = 85.0 * Math.pow(decay, 1.8) + 12.0;
                                phase += (2.0 * Math.PI * hz) / sampleRate;
                                double click = (p > 0.82 && p < 0.86) ? whiteNoise * 0.7 : 0.0;
                                sample = (Math.sin(phase) * decay * 0.4 + click) * 0.45;
                            } else {
                                // SPORTS GT / BUGGY SHUTDOWN: Exhaust burble, backfire pop & V8 spin-down
                                double hz = 48.0 * Math.pow(decay, 1.5) + 10.0;
                                phase += (2.0 * Math.PI * hz) / sampleRate;
                                double pop = ((p > 0.25 && p < 0.30) || (p > 0.52 && p < 0.56)) ? whiteNoise * 0.85 : 0.0;
                                double burble = Math.sin(phase) + 0.6 * Math.sin(phase * 0.5) + pop;
                                sample = Math.tanh(burble * 1.9) * decay * 0.44;
                            }
                        }
                    } else {
                        // STATE_RUNNING: Continuous Real-Time Synthesis
                        smoothRpm += (targetRpm - smoothRpm) * 0.0045f;
                        smoothLoad += (currentThrottle - smoothLoad) * 0.006f;

                        if (model == DriveOnCar.MODEL_HELICOPTER) {
                            // REALISTIC HELICOPTER: Main Rotor Blade-Vortex Slap + Rotor Wash + Tail Rotor Buzz + Turbine Whine
                            double normRpm = Math.max(0.0, Math.min(1.5, (smoothRpm - 1400.0) / 3100.0));
                            double mainRotorHz = 12.5 + normRpm * 6.5; // ~12.5 to 19 blade passes/sec
                            double tailRotorHz = mainRotorHz * 5.25;
                            double turbineHz   = 980.0 + normRpm * 680.0;

                            phase += (2.0 * Math.PI * mainRotorHz) / sampleRate;
                            if (phase > Math.PI * 200.0) phase -= Math.PI * 200.0;

                            subPhase += (2.0 * Math.PI * tailRotorHz) / sampleRate;
                            if (subPhase > Math.PI * 200.0) subPhase -= Math.PI * 200.0;

                            auxPhase += (2.0 * Math.PI * turbineHz) / sampleRate;
                            if (auxPhase > Math.PI * 200.0) auxPhase -= Math.PI * 200.0;

                            // Asymmetric sharp blade-slap envelope
                            double bladeEnv = Math.pow(0.5 + 0.5 * Math.sin(phase), 6.0);
                            double subThump = Math.sin(phase * 3.0) * 0.95 + Math.sin(phase * 4.0) * 0.55;

                            // Low-pass filtered air displacement whoosh
                            heliWindLp += 0.14 * (whiteNoise - heliWindLp);
                            double vortexSlap = (subThump + heliWindLp * (1.3 + smoothLoad * 0.6)) * (0.22 + 0.78 * bladeEnv);

                            // Tail rotor harmonic buzz + turboshaft whistle
                            double tailBuzz = Math.tanh(Math.sin(subPhase) * 1.6) * 0.18;
                            double jetWhine = Math.sin(auxPhase) * 0.09;

                            sample = Math.tanh((vortexSlap * 1.35 + tailBuzz + jetWhine) * (0.75 + smoothLoad * 0.35)) * 0.58;
                        } else {
                            // GROUND VEHICLES: Multi-Cylinder Combustion + 2-Pole Muffler Resonance
                            double baseHz = 24.0 + (smoothRpm / 6200.0) * 86.0;
                            double lpfAlpha = 0.11 + smoothLoad * 0.09;
                            if (model == DriveOnCar.MODEL_HEAVY_TRUCK) {
                                baseHz = 18.0 + (smoothRpm / 2800.0) * 44.0;
                                lpfAlpha = 0.08 + smoothLoad * 0.06;
                            } else if (model == DriveOnCar.MODEL_SUPERBIKE) {
                                baseHz = 36.0 + (smoothRpm / 9500.0) * 145.0;
                                lpfAlpha = 0.16 + smoothLoad * 0.12;
                            } else if (model == DriveOnCar.MODEL_BUGGY_RAID) {
                                baseHz = 26.0 + (smoothRpm / 6200.0) * 82.0;
                                lpfAlpha = 0.12 + smoothLoad * 0.08;
                            }

                            phase += (2.0 * Math.PI * baseHz) / sampleRate;
                            subPhase += (2.0 * Math.PI * (baseHz * 0.5)) / sampleRate;
                            if (phase > Math.PI * 200.0) phase -= Math.PI * 200.0;
                            if (subPhase > Math.PI * 200.0) subPhase -= Math.PI * 200.0;

                            double cyl1 = Math.sin(phase);
                            double subBurble = Math.sin(subPhase) * (model == DriveOnCar.MODEL_SUPERBIKE ? 0.25 : 0.75);
                            double firingGate = 0.55 + 0.45 * Math.max(0.0, cyl1);

                            double rawExhaust = (cyl1 + Math.sin(phase * 2.0 + 0.4) * 0.65 + subBurble) * (0.7 + smoothLoad * 0.5)
                                    + (whiteNoise * firingGate * (0.55 + smoothLoad * 0.65));
                            lpState1 += lpfAlpha * (Math.tanh(rawExhaust * 1.6) - lpState1);
                            lpState2 += lpfAlpha * (lpState1 - lpState2);
                            sample = lpState2 * (0.18 + smoothLoad * 0.16);
                        }
                    }

                    if (sample > 0.95) sample = 0.95;
                    if (sample < -0.95) sample = -0.95;
                    buffer[i] = (short) (sample * 22000);
                }
                track.write(buffer, 0, chunkSamples);
            }
        } catch (Exception ignored) {
        } finally {
            if (track != null) {
                try { track.stop(); track.release(); } catch (Exception ignored) {}
            }
        }
    }

    public void vibrateTap() { vibrateOneShot(16, 120); }
    public void vibrateDrift() { vibrateOneShot(14, 65); }
    public void vibrateCrash(float intensity) {
        if (!vibrationEnabled || vibrator == null || !vibrator.hasVibrator()) return;
        vibrateOneShot(Math.min(150, Math.max(30, (int) (intensity * 100f))), 245);
    }

    private void vibrateOneShot(int durationMs, int amplitude) {
        if (!vibrationEnabled || vibrator == null || !vibrator.hasVibrator()) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, Math.min(255, Math.max(1, amplitude))));
            } else vibrator.vibrate(durationMs);
        } catch (Exception ignored) {}
    }

    public void playClickBeep() {
        if (soundEnabled && toneGenerator != null && sfxExecutor != null) {
            sfxExecutor.execute(() -> { try { toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 35); } catch (Exception ignored) {} });
        }
    }

    public void playGearShift(final int newGear) {
        if (!soundEnabled || sfxExecutor == null) return;
        vibrateOneShot(22, 165);
        playPcmSound(sfxExecutor, 85, (i, numSamples, sampleRate) -> {
            double t = (double) i / sampleRate, p = (double) i / numSamples;
            return Math.tanh((Math.sin(2.0 * Math.PI * 120.0 * t) + (Math.random() * 2.0 - 1.0) * 0.5) * 1.8) * Math.exp(-p * 5.0) * 0.42;
        });
    }

    public void playBrakeSound(final float speedKmh) {
        long now = System.currentTimeMillis();
        if (!soundEnabled || sfxExecutor == null || speedKmh < 12f || now - lastBrakeTime < 140) return;
        lastBrakeTime = now;
        final double gain = Math.min(0.38, 0.14 + (speedKmh / 240.0) * 0.24);
        playPcmSound(sfxExecutor, 115, (i, numSamples, sampleRate) -> {
            double p = (double) i / numSamples;
            return (Math.sin(2.0 * Math.PI * (2050.0 - 220.0 * p) * ((double) i / sampleRate)) * 0.35 + (Math.random() * 2.0 - 1.0) * 0.65) * Math.sin(p * Math.PI) * gain;
        });
    }

    public void playCarHorn(final int carModel) {
        if (!soundEnabled || sfxExecutor == null) return;
        vibrateOneShot(45, 160);
        playPcmSound(sfxExecutor, 220, (i, numSamples, sampleRate) -> {
            double t = (double) i / sampleRate, p = (double) i / numSamples;
            double env = (p < 0.08) ? (p / 0.08) : ((p > 0.85) ? ((1.0 - p) / 0.15) : 1.0);
            double f1 = (carModel == DriveOnCar.MODEL_HEAVY_TRUCK) ? 185.0 : (carModel == DriveOnCar.MODEL_SUPERBIKE ? 560.0 : 410.0);
            double f2 = (carModel == DriveOnCar.MODEL_HEAVY_TRUCK) ? 233.0 : (carModel == DriveOnCar.MODEL_SUPERBIKE ? 680.0 : 512.0);
            return (Math.tanh(Math.sin(2.0 * Math.PI * f1 * t) * 2.2) * 0.5 + Math.tanh(Math.sin(2.0 * Math.PI * f2 * t) * 2.2) * 0.5) * env * 0.72;
        });
    }

    public void playDriftScreech() {
        long now = System.currentTimeMillis();
        if (!soundEnabled || sfxExecutor == null || now - lastScreechTime < 140) return;
        lastScreechTime = now;
        playPcmSound(sfxExecutor, 120, (i, numSamples, sampleRate) -> {
            double t = (double) i / sampleRate, p = (double) i / numSamples;
            return (Math.sin(2.0 * Math.PI * (1420.0 + 160.0 * Math.sin(t * 55.0)) * t) * 0.45 + (Math.random() * 2.0 - 1.0) * 0.45) * Math.sin(p * Math.PI) * 0.36;
        });
    }

    public void playNitroBoost() {
        long now = System.currentTimeMillis();
        if (!soundEnabled || sfxExecutor == null || now - lastNitroTime < 150) return;
        lastNitroTime = now;
        playPcmSound(sfxExecutor, 140, (i, numSamples, sampleRate) -> {
            double t = (double) i / sampleRate, p = (double) i / numSamples;
            return Math.tanh((Math.sin(2.0 * Math.PI * (90.0 + 50.0 * p) * t) * 0.7 + (Math.random() * 2.0 - 1.0) * 0.55 * 0.5) * 1.6) * Math.sin(p * Math.PI) * 0.55;
        });
    }

    public void playBuildingCrash(final float intensity) {
        long now = System.currentTimeMillis();
        if (!soundEnabled || sfxExecutor == null || now - lastCrashTime < 120) return;
        lastCrashTime = now;
        final double gain = Math.min(1.0, Math.max(0.38, intensity));
        playPcmSound(sfxExecutor, 210, (i, numSamples, sampleRate) -> {
            double t = (double) i / sampleRate, p = (double) i / numSamples;
            double concreteBoom = Math.sin(2.0 * Math.PI * (95.0 * Math.exp(-p * 4.2) + 28.0) * t);
            double metalRing = Math.sin(2.0 * Math.PI * 520.0 * t) * Math.exp(-p * 6.0) * 0.35;
            return Math.tanh((concreteBoom * 0.85 + metalRing + (Math.random() * 2.0 - 1.0) * 0.75 * 0.65) * 2.3) * Math.pow(1.0 - p, 1.4) * gain;
        });
    }

    public void playTreeCrash(final float intensity) {
        long now = System.currentTimeMillis();
        if (!soundEnabled || sfxExecutor == null || now - lastCrashTime < 120) return;
        lastCrashTime = now;
        final double gain = Math.min(0.95, Math.max(0.35, intensity));
        playPcmSound(sfxExecutor, 175, (i, numSamples, sampleRate) -> {
            double t = (double) i / sampleRate, p = (double) i / numSamples;
            return Math.tanh((Math.sin(2.0 * Math.PI * (210.0 * Math.exp(-p * 5.0) + 65.0) * t) * 0.75 + (Math.random() * 2.0 - 1.0) * Math.sin(p * Math.PI) * 0.85 * 0.70) * 2.0) * Math.pow(1.0 - p, 1.6) * gain;
        });
    }

    public void playPickupChime() {
        if (!soundEnabled || sfxExecutor == null) return;
        vibrateTap();
        playPcmSound(sfxExecutor, 150, (i, numSamples, sampleRate) -> {
            double p = (double) i / numSamples;
            return Math.sin(2.0 * Math.PI * ((p < 0.5) ? 587.33 : 880.0) * ((double) i / sampleRate)) * (1.0 - p * 0.7) * 0.55;
        });
    }

    private interface WaveGenerator { double sampleAt(int index, int totalSamples, int sampleRate); }

    private void playPcmSound(ExecutorService executor, final int durationMs, final WaveGenerator generator) {
        if (executor == null) return;
        executor.execute(() -> {
            AudioTrack track = null;
            try {
                final int sampleRate = 16000;
                final int numSamples = (sampleRate * durationMs) / 1000;
                short[] pcm = new short[numSamples];
                for (int i = 0; i < numSamples; i++) {
                    double s = generator.sampleAt(i, numSamples, sampleRate);
                    pcm[i] = (short) (Math.max(-1.0, Math.min(1.0, s)) * 24000);
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    track = new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()).setBufferSizeInBytes(numSamples * 2).setTransferMode(AudioTrack.MODE_STATIC).build();
                } else {
                    track = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, numSamples * 2, AudioTrack.MODE_STATIC);
                }
                track.write(pcm, 0, numSamples); track.play(); Thread.sleep(durationMs + 18L);
            } catch (Exception ignored) {
            } finally {
                if (track != null) { try { track.stop(); track.release(); } catch (Exception ignored) {} }
            }
        });
    }
}