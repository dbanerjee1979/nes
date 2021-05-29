package experiments.nes.ppu;

import java.awt.*;

public class NesPalette {
    private Color[] palette = new Color[64];

    public NesPalette() {
        palette[0x00] = new Color(84, 84, 84);
        palette[0x10] = new Color(152, 150, 152);
        palette[0x20] = new Color(236, 238, 236);
        palette[0x30] = new Color(236, 238, 236);

        palette[0x01] = new Color(0, 30, 116);
        palette[0x11] = new Color(8, 76, 196);
        palette[0x21] = new Color(76, 154, 236);
        palette[0x31] = new Color(168, 204, 236);

        palette[0x02] = new Color(8, 16, 144);
        palette[0x12] = new Color(48, 50, 236);
        palette[0x22] = new Color(120, 124, 236);
        palette[0x32] = new Color(188, 188, 236);

        palette[0x03] = new Color(48, 0, 136);
        palette[0x13] = new Color(92, 30, 228);
        palette[0x23] = new Color(176, 98, 236);
        palette[0x33] = new Color(212, 178, 236);

        palette[0x04] = new Color(68, 0, 100);
        palette[0x14] = new Color(136, 20, 176);
        palette[0x24] = new Color(228, 84, 236);
        palette[0x34] = new Color(236, 174, 236);

        palette[0x05] = new Color(92, 0, 48);
        palette[0x15] = new Color(160, 20, 100);
        palette[0x25] = new Color(236, 88, 180);
        palette[0x35] = new Color(236, 174, 212);

        palette[0x06] = new Color(84, 4, 0);
        palette[0x16] = new Color(152, 34, 32);
        palette[0x26] = new Color(236, 106, 100);
        palette[0x36] = new Color(236, 180, 176);

        palette[0x07] = new Color(60, 24, 0);
        palette[0x17] = new Color(120, 60, 0);
        palette[0x27] = new Color(212, 136, 32);
        palette[0x37] = new Color(228, 196, 144);

        palette[0x08] = new Color(32, 42, 0);
        palette[0x18] = new Color(84, 90, 0);
        palette[0x28] = new Color(160, 170, 0);
        palette[0x38] = new Color(204, 210, 120);

        palette[0x09] = new Color(8, 58, 0);
        palette[0x19] = new Color(40, 114, 0);
        palette[0x29] = new Color(116, 196, 0);
        palette[0x39] = new Color(180, 222, 120);

        palette[0x0A] = new Color(0, 64, 0);
        palette[0x1A] = new Color(8, 124, 0);
        palette[0x2A] = new Color(76, 208, 32);
        palette[0x3A] = new Color(168, 226, 144);

        palette[0x0B] = new Color(0, 60, 0);
        palette[0x1B] = new Color(0, 118, 40);
        palette[0x2B] = new Color(56, 204, 108);
        palette[0x3B] = new Color(152, 226, 180);

        palette[0x0C] = new Color(0, 50, 60);
        palette[0x1C] = new Color(0, 102, 120);
        palette[0x2C] = new Color(56, 180, 204);
        palette[0x3C] = new Color(160, 214, 228);

        palette[0x0D] = new Color(0, 0, 0);
        palette[0x1D] = new Color(0, 0, 0);
        palette[0x2D] = new Color(60, 60, 60);
        palette[0x3D] = new Color(160, 162, 160);

        palette[0x0E] = new Color(0, 0, 0);
        palette[0x1E] = new Color(0, 0, 0);
        palette[0x2E] = new Color(0, 0, 0);
        palette[0x3E] = new Color(0, 0, 0);

        palette[0x0F] = new Color(0, 0, 0);
        palette[0x1F] = new Color(0, 0, 0);
        palette[0x2F] = new Color(0, 0, 0);
        palette[0x3F] = new Color(0, 0, 0);
    }

    public int getRGB(byte entry) {
        return palette[entry].getRGB() & 0xFFFFFF;
    }
}
