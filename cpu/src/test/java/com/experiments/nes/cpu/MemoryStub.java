package com.experiments.nes.cpu;

import java.util.HashMap;
import java.util.Map;

import static com.experiments.nes.HexAssertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class MemoryStub implements Memory {
    private final byte[] memory = new byte[0x10000];
    private final Map<Integer, Short> readAddresses = new HashMap<>();
    private int cycle;

    @Override
    public byte load(short address) {
        readAddresses.put(cycle, address);
        return this.memory[address & 0xFFFF];
    }

    @Override
    public void store(short address, byte value) {
        this.memory[address] = value;
    }

    public void verifyReadAddress(int cycle, int expectedReadAddress, String description) {
        Short readAddress = readAddresses.get(cycle);
        assertNotNull(readAddress, String.format("%s: Expecting read at address %04x on cycle %d",
                description, expectedReadAddress, cycle));
        assertEquals(expectedReadAddress, readAddress);
    }

    public void clock() {
        this.cycle++;
    }
}
