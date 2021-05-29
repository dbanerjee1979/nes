package experiments.nes.ppu;

import experiments.nes.Bus;

import java.io.IOException;
import java.io.InputStream;

public class PpuFileBus implements Bus {
    private byte[] ppuMemory;

    public PpuFileBus(String path) {
        this.ppuMemory = new byte[0x4000];

        try (InputStream is = PpuFileBus.class.getResourceAsStream(path)) {
            if (is.read(this.ppuMemory) != ppuMemory.length) {
                throw new IllegalStateException("Unexpected amount of data loaded");
            }
        }
        catch (IOException ex) {
            throw new IllegalStateException("Unable to load test resources", ex);
        }
    }

    @Override
    public byte read(short address) {
        return ppuMemory[address];
    }
}
