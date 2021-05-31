package experiments.nes.ppu;

import experiments.nes.Bus;

import java.awt.image.BufferedImage;

public class PPU {
    private Bus bus;
    private NesPalette palette;
    private BufferedImage frameBuffer;
    private int cycle;
    private int dataCycle;
    private short backgroundPatternTable;
    private VRamAddress v;
    private VRamAddress t;
    private byte nametableEntry;
    private byte[] tiles = new byte[4];
    private byte[] attrs = new byte[2];
    private byte plane1;
    private byte plane2;
    private byte attr;
    private boolean preRender;
    private int x;
    private int y;
    private int scanline;
    private boolean vblank;

    public PPU(Bus bus, NesPalette palette) {
        this.bus = bus;
        this.palette = palette;
        this.frameBuffer = new BufferedImage(256, 240, BufferedImage.TYPE_INT_RGB);
        this.cycle = 0;
        this.dataCycle = 0;
        this.v = new VRamAddress();
        this.t = new VRamAddress();
        this.setBackgroundPatternTable(0);
        this.preRender = true;
        this.x = 0;
        this.y = -1;
        this.scanline = -1;
        this.vblank = false;
    }

    public BufferedImage getFrameBuffer() {
        return frameBuffer;
    }

    public void cycle() {
        try {
            if (scanline > 239) {
                if (scanline == 241 && cycle == 1) {
                    vblank = true;
                }
            } else {
                boolean pixelRender;
                if ((pixelRender = (cycle >= 1 && cycle <= 256)) || cycle >= 321 && cycle <= 336) {
                    switch (dataCycle++) {
                        case 0:
                            plane1 = tiles[0];
                            plane2 = tiles[1];
                            attr = attrs[0];
                            this.nametableEntry = this.bus.read(this.v.getNameTableAddress());
                            break;
                        case 2:
                            attrs[0] = attrs[1];
                            attrs[1] = this.v.getBackgroundPalette(this.bus.read(this.v.getAttrTableAddress()));
                            break;
                        case 4:
                            tiles[0] = tiles[2];
                            short tileAddr1 = this.v.getTileAddressPlane1(backgroundPatternTable, this.nametableEntry);
                            tiles[2] = this.bus.read(tileAddr1);
                            break;
                        case 6:
                            tiles[1] = tiles[3];
                            short tileAddr2 = this.v.getTileAddressPlane2(backgroundPatternTable, this.nametableEntry);
                            tiles[3] = this.bus.read(tileAddr2);
                            break;
                        case 7:
                            this.v.incrementX();
                            dataCycle = 0;
                            break;
                    }
                    if (!preRender && pixelRender) {
                        int colorIndex = ((plane1 & 0x80) == 0 ? 0x00 : 0x01) | ((plane2 & 0x80) == 0 ? 0x00 : 0x02);
                        int paletteAddr = 0x3F00 | ((attr & 0x03) << 2) | colorIndex;
                        byte paletteEntry = bus.read((short) ((paletteAddr & 0x3) == 0 ? 0x3F00 : paletteAddr));
                        frameBuffer.setRGB(x++, y, palette.getRGB(paletteEntry));
                        plane1 <<= 1;
                        plane2 <<= 1;
                    }
                    if (cycle == 256) {
                        y++;
                        this.v.incrementY();
                    }
                } else if (cycle == 257) {
                    x = 0;
                    this.v.setX(this.t);
                } else if (preRender && cycle >= 280 && cycle <= 304) {
                    this.v.setY(this.t);
                } else if (cycle >= 337 && cycle <= 340) {
                    switch (dataCycle++) {
                        case 0:
                            this.bus.read(this.v.getNameTableAddress());
                            break;
                        case 1:
                            dataCycle = 0;
                            break;
                    }
                }
            }
            if (cycle == 340) {
                preRender = false;
                cycle = -1;
                scanline++;
            }
        }
        finally {
            cycle++;
        }
    }

    public void setBackgroundPatternTable(int table) {
        switch (table) {
            case 0:
                backgroundPatternTable = 0x0000;
                break;
            case 1:
                backgroundPatternTable = 0x1000;
                break;
            default:
                throw new IllegalArgumentException("There are only two pattern tables!");
        }
    }

    public boolean isVBlank() {
        return vblank;
    }
}
