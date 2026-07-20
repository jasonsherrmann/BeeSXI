package com.jaysin.beesxi.apiary;

public enum ApiaryMachinePartType {
    CASING(0.0F),
    ACCELERATOR(0.10F),
    HYPER_ACCELERATOR(0.25F);

    private final float speedBonus;

    ApiaryMachinePartType(float speedBonus) {
        this.speedBonus = speedBonus;
    }

    public float speedBonus() {
        return speedBonus;
    }
}
