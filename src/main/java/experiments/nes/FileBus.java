package experiments.nes;

import java.io.IOException;
import java.io.InputStream;

public class FileBus implements Bus {
    private byte[] memory;

    public FileBus(String path, int size) {
        this.memory = new byte[size];

        try (InputStream is = FileBus.class.getResourceAsStream(path)) {
            if (is.read(this.memory) != memory.length) {
                throw new IllegalStateException("Unexpected amount of data loaded");
            }
        }
        catch (IOException ex) {
            throw new IllegalStateException("Unable to load test resources", ex);
        }
    }

    @Override
    public byte read(short address) {
        return memory[address];
    }
}
