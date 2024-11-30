package com.experiments.nes.cpu;

public interface Memory {
    byte load(short address);
    void store(short address, byte value);
}
