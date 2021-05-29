package experiments.nes.ppu;

public class VRamAddress {
    public static final int NUM_TILES_ROW = 32;
    public static final int NAME_TABLE_START = 0x2000;
    public static final int ATTR_TABLE_START = 0x23C0;

    private static final int FINE_Y_MASK = 0xF000;
    private static final int NAMETABLE_MASK = 0x0C00;
    private static final int NAMETABLE_H_MASK = 0x0400;
    private static final int NAMETABLE_V_MASK = 0x0800;
    private static final int TILE_Y_MASK = 0x03E0;
    private static final int TILE_X_MASK = 0x001F;
    private static final int TILE_COORDINATE_MASK = 0x001F;
    private static final int FINE_COORDINATE_MASK = 0x0007;
    private static final int NAMETABLE_BITS = 2;
    private static final int TILE_COORDINATE_BITS = 5;
    private static final int ATTRIBUTE_COORDINATE_BITS = 3;
    private static final int TILE_FINE_COORDINATE_BITS = 4;
    private static final int ATTRIBUTE_Y_MASK = 0x38;
    private static final int NAMETABLE_ENTRY_MASK = 0x0FF0;
    private static final int TILE_PLANE_2_MASK = 0x08;

    // yyy NN YYYYY XXXXX
    // y - fine Y-scroll (NAMETABLE_FINE_Y_MASK)
    // N - name table select (NAMETABLE_MASK)
    // Y - coarse Y-scroll (NAMETABLE_TILE_Y_MASK)
    // X - coarse X-scroll (NAMETABLE_TILE_X_MASK)
    private short value;

    public VRamAddress() {
    }

    public int getTileX() {
        return value & TILE_X_MASK;
    }

    public int getTileY() {
        return ((value & TILE_Y_MASK) >> TILE_COORDINATE_BITS) & TILE_COORDINATE_MASK;
    }

    public int getFineY() {
        return ((value & FINE_Y_MASK) >> (NAMETABLE_BITS + TILE_COORDINATE_BITS * 2)) & FINE_COORDINATE_MASK;
    }

    public void setY(VRamAddress that) {
        this.value &= ~(NAMETABLE_V_MASK | FINE_Y_MASK | TILE_Y_MASK);
        this.value |= that.value & (NAMETABLE_V_MASK | FINE_Y_MASK | TILE_Y_MASK);
    }

    public void setX(VRamAddress that) {
        this.value &= ~(NAMETABLE_H_MASK | TILE_X_MASK);
        this.value |= that.value & (NAMETABLE_H_MASK | TILE_X_MASK);
    }

    public short getNameTableAddress() {
        return (short) (NAME_TABLE_START |
            (value & (NAMETABLE_MASK | TILE_Y_MASK | TILE_X_MASK))
        );
    }

    public void incrementX() {
        int x = getTileX();
        if (x == NUM_TILES_ROW - 1) {
            zeroX();
            switchHorizontalNameTable();
        } else {
            value++;
        }
    }

    public void incrementY() {
        int y = getFineY();
        if (y != 7) {
            y++;
            value &= ~FINE_Y_MASK;
            value |= (y << (NAMETABLE_BITS + TILE_COORDINATE_BITS * 2)) & FINE_Y_MASK;
        } else {
            value &= ~FINE_Y_MASK;
            y = getTileY();
            if (y == 29) {
                value &= ~TILE_Y_MASK;
                switchVerticalNameTable();
            } else if (y == 31) {
                value &= ~TILE_Y_MASK;
            } else {
                y++;
                value &= ~TILE_Y_MASK;
                value |= (y << TILE_COORDINATE_BITS) & TILE_Y_MASK;
            }
        }
    }

    public short getAttrTableAddress() {
        int y = (getTileY() >> 2) & 0x07;
        int x = (getTileX() >> 2) & 0x07;
        return (short) (ATTR_TABLE_START |
            (value & NAMETABLE_MASK) | (y << ATTRIBUTE_COORDINATE_BITS) & ATTRIBUTE_Y_MASK | x
        );
    }

    public byte getBackgroundPalette(byte attributeValue) {
        // each quadrant consists of two tiles:
        // 7 6  - bottom right - y = 1, x = 1   shift = 6
        // 5 4  - bottom left  - y = 1, x = 0   shift = 4
        // 3 2  - top right    - y = 0, x = 1   shift = 2
        // 1 0  - top left     - y = 0, x = 0   shift = 0
        // quadrant selection is determined by second bit
        int y = (getTileY() >> 1) & 0x01;
        int x = (getTileX() >> 1) & 0x01;
        int attrShift = y << 2 | x << 1;
        return (byte) ((attributeValue >> attrShift) & 0x03);
    }

    public short getTileAddressPlane1(int tableStart, byte nameTableEntry) {
        return (short) (getTileAddress(tableStart, nameTableEntry) | getFineY());
    }

    public short getTileAddressPlane2(int tableStart, byte nameTableEntry) {
        return (short) (getTileAddress(tableStart, nameTableEntry) | TILE_PLANE_2_MASK | getFineY());
    }

    private void zeroX() {
        value &= ~TILE_X_MASK;
    }

    private void switchHorizontalNameTable() {
        value ^= NAMETABLE_H_MASK;
    }

    private void switchVerticalNameTable() {
        value ^= NAMETABLE_V_MASK;
    }

    private short getTileAddress(int tableStart, byte nameTableEntry) {
        return (short) (tableStart | (nameTableEntry << TILE_FINE_COORDINATE_BITS) & NAMETABLE_ENTRY_MASK);
    }
}
