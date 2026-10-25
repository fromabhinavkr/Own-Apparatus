package com.abhinav.ownapp;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;

public class AudioManager {
    private Music cityAmbience;
    private Sound engineSound;
    private Sound stepSound;
    private long engineLoopId = -1;
    private float stepTimer = 0f;

    public void init() {
        if (Gdx.files.internal("city_bgm.mp3").exists()) {
            cityAmbience = Gdx.audio.newMusic(Gdx.files.internal("city_bgm.mp3"));
            cityAmbience.setLooping(true);
            cityAmbience.setVolume(0.3f);
            cityAmbience.play();
        }
        if (Gdx.files.internal("engine.ogg").exists()) {
            engineSound = Gdx.audio.newSound(Gdx.files.internal("engine.ogg"));
        }
        if (Gdx.files.internal("step.ogg").exists()) {
            stepSound = Gdx.audio.newSound(Gdx.files.internal("step.ogg"));
        }
    }

    public void updateFootsteps(float delta, boolean isWalking) {
        if (!isWalking || stepSound == null) return;
        stepTimer -= delta;
        if (stepTimer <= 0f) {
            stepSound.play(0.4f);
            stepTimer = 0.42f;
        }
    }

    public void updateEngineSound(boolean inVehicle, float currentSpeed) {
        if (engineSound == null) return;

        if (inVehicle) {
            if (engineLoopId == -1) {
                engineLoopId = engineSound.loop(0.5f);
            }
            float pitch = 0.8f + (Math.abs(currentSpeed) / 42f) * 1.0f;
            engineSound.setPitch(engineLoopId, pitch);
        } else if (engineLoopId != -1) {
            engineSound.stop(engineLoopId);
            engineLoopId = -1;
        }
    }

    public void dispose() {
        if (cityAmbience != null) cityAmbience.dispose();
        if (engineSound != null) engineSound.dispose();
        if (stepSound != null) stepSound.dispose();
    }
}
