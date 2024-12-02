package com.experiments.nes.cpu;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static com.experiments.nes.HexAssertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CpuTest {
    private Cpu cpu;
    private MemoryStub memory;
    private int cycle;
    private Map<Integer, Map<String, Byte>> registers;

    @BeforeEach
    void setup() {
        this.memory = new MemoryStub();
        this.cpu = new Cpu(memory);
        this.cycle = 0;
        this.registers = new HashMap<>();
    }

    @Test
    void testInitialState() {
        assertEquals(0, this.cpu.a());
        assertEquals(0, this.cpu.x());
        assertEquals(0, this.cpu.y());
        assertEquals(0xFFFC, this.cpu.pc());
        assertEquals(0xFD, this.cpu.s());
        assertFalse(this.cpu.flag(Cpu.Flag.Carry));
        assertFalse(this.cpu.flag(Cpu.Flag.Zero));
        assertTrue(this.cpu.flag(Cpu.Flag.InterruptDisabled));
        assertFalse(this.cpu.flag(Cpu.Flag.Decimal));
        assertFalse(this.cpu.flag(Cpu.Flag.Overflow));
        assertFalse(this.cpu.flag(Cpu.Flag.Negative));
    }

    @Test
    void testReset() {
        this.cpu.a(0xFF);
        this.cpu.x(0xFF);
        this.cpu.y(0xFF);
        this.cpu.pc(0x0000);
        this.cpu.s(0xFD);
        this.cpu.p(~Cpu.Flag.InterruptDisabled.mask());

        this.cpu.reset();

        assertEquals(0xFF, this.cpu.a());
        assertEquals(0xFF, this.cpu.x());
        assertEquals(0xFF, this.cpu.y());
        assertEquals(0xFFFC, this.cpu.pc());
        assertEquals(0xFA, this.cpu.s());
        assertTrue(this.cpu.flag(Cpu.Flag.Carry));
        assertTrue(this.cpu.flag(Cpu.Flag.Zero));
        assertTrue(this.cpu.flag(Cpu.Flag.InterruptDisabled));
        assertTrue(this.cpu.flag(Cpu.Flag.Decimal));
        assertTrue(this.cpu.flag(Cpu.Flag.Overflow));
        assertTrue(this.cpu.flag(Cpu.Flag.Negative));
    }

    void memory(int address, int value) {
        this.memory.store((short) address, (byte) value);
    }

    void clock(int cycles) {
        for (int cycle = 0; cycle < cycles; cycle++) {
            try {
                clock();
            }
            catch (Exception ex) {
                throw new IllegalStateException("Wrong state at cycle " + cycle, ex);
            }
        }
    }

    void clock() {
        cpu.clock();
        memory.clock();
        registers.put(cycle, Map.of(
                "a", cpu.a(),
                "x", cpu.x(),
                "y", cpu.y(),
                "p", cpu.p()));
        cycle++;
    }

    Verification cycle(int n, String description) {
        return new Verification(n, description);
    }

    @SuppressWarnings("UnusedReturnValue")
    class Verification {
        private final int cycle;
        private final String description;

        public Verification(int cycle, String description) {
            this.cycle = cycle;
            this.description = description;
        }

        public Verification read(int address) {
            memory.verifyReadAddress(cycle, address, description);
            return this;
        }


        public Verification write(int address, int value) {
            memory.verifyWriteAddress(cycle, address, value, description);
            return this;
        }

        private Verification register(String register, int value) {
            assertEquals(value, registers.getOrDefault(cycle, Map.of()).getOrDefault(register, (byte) 0x00),
                    description);
            return this;
        }

        public Verification a(int value) {
            return register("a", value);
        }

        public Verification x(int value) {
            return register("x", value);
        }

        public Verification y(int value) {
            return register("y", value);
        }

        public Verification flags(String expectedFlags) {
            byte actual = registers.getOrDefault(cycle, Map.of()).getOrDefault("p", (byte) 0x20);
            String flagLabels = "NV1BDIZC";
            int mask = 0x0080;
            StringBuilder actualFlags = new StringBuilder();
            for (int i = 0; i < 8; i++, mask >>= 1) {
                actualFlags.append((actual & mask) != 0 ? flagLabels.charAt(i) : ".");
            }
            Assertions.assertEquals(expectedFlags, actualFlags.toString(), description);
            return this;
        }
    }

    @Nested
    class ASL {
        @Test
        void testAccumulator() {
            cpu.pc(0x0100);
            memory(0x0100, 0xA9); // LDA #01
            memory(0x0101, 0x01);
            memory(0x0102, 0x0A); // ASL A

            clock(5);

            cycle(0, "Fetch opcode"                      ).read(0x0100).a(0x00);
            cycle(1, "Fetch value"                       ).read(0x0101).a(0x00);
            cycle(2, "Fetch opcode"                      ).read(0x0102).a(0x01);
            cycle(3, "Fetch next instruction, throw away").read(0x0103).a(0x01);
            cycle(4, "Fetch opcode"                      ).read(0x0103).a(0x02);
        }

        @Test
        void testZeroPage() {
            cpu.pc(0x0100);
            memory(0x0100, 0xA9); // LDA #01
            memory(0x0101, 0x01);
            memory(0x0102, 0x85); // STA $01
            memory(0x0103, 0x01);
            memory(0x0104, 0x06); // ASL $01
            memory(0x0105, 0x01);

            clock(10);

            cycle(0, "Fetch opcode"               ).read(0x0100       );
            cycle(1, "Fetch value"                ).read(0x0101       );
            cycle(2, "Fetch opcode"               ).read(0x0102       );
            cycle(3, "Fetch address"              ).read(0x0103       );
            cycle(4, "Write to effective address" ).write(0x0001, 0x01);
            cycle(5, "Fetch opcode"               ).read(0x0104       );
            cycle(6, "Fetch address"              ).read(0x0105       );
            cycle(7, "Read from effective address").read(0x0001       );
            cycle(8, "Write old value to address" ).write(0x0001, 0x01);
            cycle(9, "Write new value to address" ).write(0x0001, 0x02);
        }

        @Test
        void testZeroPageX() {
            cpu.pc(0x0100);
            memory(0x0100, 0xA9); // LDX #01
            memory(0x0101, 0x01);
            memory(0x0102, 0xA2); // LDA #01
            memory(0x0103, 0x01);
            memory(0x0104, 0x95); // STA $01,X
            memory(0x0105, 0x01);
            memory(0x0106, 0x16); // ASL $01,X
            memory(0x0107, 0x01);

            clock(14);

            cycle(0,  "Fetch opcode"                ).read(0x0100       ).a(0x00).x(0x00);
            cycle(1,  "Fetch value"                 ).read(0x0101       ).a(0x00).x(0x00);
            cycle(2,  "Fetch opcode"                ).read(0x0102       ).a(0x01).x(0x00);
            cycle(3,  "Fetch value"                 ).read(0x0103       ).a(0x01).x(0x00);
            cycle(4,  "Fetch opcode"                ).read(0x0104       ).a(0x01).x(0x01);
            cycle(5,  "Fetch address"               ).read(0x0105       ).a(0x01).x(0x01);
            cycle(6,  "Read from address, add index").read(0x0001       ).a(0x01).x(0x01);
            cycle(7,  "Write to address"            ).write(0x0002, 0x01).a(0x01).x(0x01);
            cycle(8,  "Fetch opcode"                ).read(0x0106       ).a(0x01).x(0x01);
            cycle(9,  "Fetch address"               ).read(0x0107       ).a(0x01).x(0x01);
            cycle(10, "Read from address, add index").read(0x0001       ).a(0x01).x(0x01);
            cycle(11, "Read from address"           ).read(0x0002       ).a(0x01).x(0x01);
            cycle(12, "Write old value to address"  ).write(0x0002, 0x01).a(0x01).x(0x01);
            cycle(13, "Write new value to address"  ).write(0x0002, 0x02).a(0x01).x(0x01);
        }

        @Test
        void testAbsolute() {
            cpu.pc(0x0100);
            memory(0x0100, 0xA9); // LDA #01
            memory(0x0101, 0x01);
            memory(0x0102, 0x8D); // STA $1234
            memory(0x0103, 0x34);
            memory(0x0104, 0x12);
            memory(0x0105, 0x0E); // ASL $1234
            memory(0x0106, 0x34);
            memory(0x0107, 0x12);

            clock(12);

            cycle(0,  "Fetch opcode"                ).read(0x0100       ).a(0x00);
            cycle(1,  "Fetch value"                 ).read(0x0101       ).a(0x00);
            cycle(2,  "Fetch opcode"                ).read(0x0102       ).a(0x01);
            cycle(3,  "Fetch address low byte"      ).read(0x0103       ).a(0x01);
            cycle(4,  "Fetch address high byte"     ).read(0x0104       ).a(0x01);
            cycle(5,  "Write to effective address"  ).write(0x1234, 0x01).a(0x01);
            cycle(6,  "Fetch opcode"                ).read(0x0105       ).a(0x01);
            cycle(7,  "Fetch address low byte"      ).read(0x0106       ).a(0x01);
            cycle(8,  "Fetch address high byte"     ).read(0x0107       ).a(0x01);
            cycle(9,  "Read from effective address" ).read(0x1234       ).a(0x01);
            cycle(10, "Write old value to address"  ).write(0x1234, 0x01).a(0x01);
            cycle(11, "Write new value to address"  ).write(0x1234, 0x02).a(0x01);
        }

        @Test
        void testAbsoluteX() {
            cpu.pc(0x0100);
            memory(0x0100, 0xA9); // LDA #01
            memory(0x0101, 0x01);
            memory(0x0102, 0xA2); // LDX #01
            memory(0x0103, 0x01);
            memory(0x0104, 0x9D); // STA $1234,X
            memory(0x0105, 0x34);
            memory(0x0106, 0x12);
            memory(0x0107, 0x1E); // ASL $1234,X
            memory(0x0108, 0x34);
            memory(0x0109, 0x12);

            clock(16);

            cycle(0,  "Fetch opcode"                      ).read(0x0100       ).a(0x00).x(0x00);
            cycle(1,  "Fetch value"                       ).read(0x0101       ).a(0x00).x(0x00);
            cycle(2,  "Fetch opcode"                      ).read(0x0102       ).a(0x01).x(0x00);
            cycle(3,  "Fetch value"                       ).read(0x0103       ).a(0x01).x(0x00);
            cycle(4,  "Fetch opcode"                      ).read(0x0104       ).a(0x01).x(0x01);
            cycle(5,  "Fetch address low byte"            ).read(0x0105       ).a(0x01).x(0x01);
            cycle(6,  "Fetch address high byte, add index").read(0x0106       ).a(0x01).x(0x01);
            cycle(7,  "Read from address"                 ).read(0x1235       ).a(0x01).x(0x01);
            cycle(8,  "Write to address"                  ).write(0x1235, 0x01).a(0x01).x(0x01);
            cycle(9,  "Fetch opcode"                      ).read(0x0107       ).a(0x01).x(0x01);
            cycle(10, "Fetch address low byte"            ).read(0x0108       ).a(0x01).x(0x01);
            cycle(11, "Fetch address high byte, add index").read(0x0109       ).a(0x01).x(0x01);
            cycle(12, "Read from address"                 ).read(0x1235       ).a(0x01).x(0x01);
            cycle(13, "Re-read from address"              ).read(0x1235       ).a(0x01).x(0x01);
            cycle(14, "Write old value to address"        ).write(0x1235, 0x01).a(0x01).x(0x01);
            cycle(15, "Write new value to address"        ).write(0x1235, 0x02).a(0x01).x(0x01);
        }

        @Test
        void testAbsoluteXPageCrossing() {
            cpu.pc(0x0100);
            memory(0x0100, 0xA9); // LDA #01
            memory(0x0101, 0x01);
            memory(0x0102, 0xA2); // LDX #02
            memory(0x0103, 0x02);
            memory(0x0104, 0x9D); // STA $12FF,X
            memory(0x0105, 0xFF);
            memory(0x0106, 0x12);
            memory(0x0107, 0x1E); // ASL $12FF,X
            memory(0x0108, 0xFF);
            memory(0x0109, 0x12);

            clock(16);

            cycle(0,  "Fetch opcode"                      ).read(0x0100       ).a(0x00).x(0x00);
            cycle(1,  "Fetch value"                       ).read(0x0101       ).a(0x00).x(0x00);
            cycle(2,  "Fetch opcode"                      ).read(0x0102       ).a(0x01).x(0x00);
            cycle(3,  "Fetch value"                       ).read(0x0103       ).a(0x01).x(0x00);
            cycle(4,  "Fetch opcode"                      ).read(0x0104       ).a(0x01).x(0x02);
            cycle(5,  "Fetch address low byte"            ).read(0x0105       ).a(0x01).x(0x02);
            cycle(6,  "Fetch address high byte, add index").read(0x0106       ).a(0x01).x(0x02);
            cycle(7,  "Read from address, fix high byte"  ).read(0x1201       ).a(0x01).x(0x02);
            cycle(8,  "Write to address"                  ).write(0x1301, 0x01).a(0x01).x(0x02);
            cycle(9,  "Fetch opcode"                      ).read(0x0107       ).a(0x01).x(0x02);
            cycle(10, "Fetch address low byte"            ).read(0x0108       ).a(0x01).x(0x02);
            cycle(11, "Fetch address high byte, add index").read(0x0109       ).a(0x01).x(0x02);
            cycle(12, "Read from address, fix high byte"  ).read(0x1201       ).a(0x01).x(0x02);
            cycle(13, "Re-read from address"              ).read(0x1301       ).a(0x01).x(0x02);
            cycle(14, "Write old value to address"        ).write(0x1301, 0x01).a(0x01).x(0x02);
            cycle(15, "Write new value to address"        ).write(0x1301, 0x02).a(0x01).x(0x02);
        }

        @Test
        void testCarryFlag() {
            cpu.pc(0x0100);
            memory(0x0100, 0xA9); // LDA #81
            memory(0x0101, 0x81);
            memory(0x0102, 0x0A); // ASL A

            clock(5);

            cycle(0, "Fetch opcode"                      ).read(0x0100).a(0x00).flags("..1..I..");
            cycle(1, "Fetch value"                       ).read(0x0101).a(0x00).flags("..1..I..");
            cycle(2, "Fetch opcode"                      ).read(0x0102).a(0x81).flags("N.1..I..");
            cycle(3, "Fetch next instruction, throw away").read(0x0103).a(0x81).flags("N.1..I..");
            cycle(4, "Fetch opcode"                      ).read(0x0103).a(0x02).flags("..1..I.C");
        }

        @Test
        void testNegativeFlag() {
            cpu.pc(0x0100);
            memory(0x0100, 0xA9); // LDA #40
            memory(0x0101, 0x40);
            memory(0x0102, 0x0A); // ASL A

            clock(5);

            cycle(0, "Fetch opcode"                      ).read(0x0100).a(0x00).flags("..1..I..");
            cycle(1, "Fetch value"                       ).read(0x0101).a(0x00).flags("..1..I..");
            cycle(2, "Fetch opcode"                      ).read(0x0102).a(0x40).flags("..1..I..");
            cycle(3, "Fetch next instruction, throw away").read(0x0103).a(0x40).flags("..1..I..");
            cycle(4, "Fetch opcode"                      ).read(0x0103).a(0x80).flags("N.1..I..");
        }

        @Test
        void testZeroFlag() {
            cpu.pc(0x0100);
            memory(0x0100, 0xA9); // LDA #80
            memory(0x0101, 0x80);
            memory(0x0102, 0x0A); // ASL A

            clock(5);

            cycle(0, "Fetch opcode"                      ).read(0x0100).a(0x00).flags("..1..I..");
            cycle(1, "Fetch value"                       ).read(0x0101).a(0x00).flags("..1..I..");
            cycle(2, "Fetch opcode"                      ).read(0x0102).a(0x80).flags("N.1..I..");
            cycle(3, "Fetch next instruction, throw away").read(0x0103).a(0x80).flags("N.1..I..");
            cycle(4, "Fetch opcode"                      ).read(0x0103).a(0x00).flags("..1..IZC");
        }
    }

    @Nested
    class LDA {
        @Test
        void testImmediate() {
            cpu.pc(0x0100);
            memory(0x0100, 0xA9); // LDA #01
            memory(0x0101, 0x01);

            clock(3);

            cycle(0, "Fetch opcode").read(0x0100).a(0x00);
            cycle(1, "Fetch value" ).read(0x0101).a(0x00);
            cycle(2, "Fetch opcode").read(0x0102).a(0x01);
        }

        @Test
        void testZeroPage() {
            cpu.pc(0x0100);
            memory(0x0001, 0x01);
            memory(0x0100, 0xA5); // LDA $01
            memory(0x0101, 0x01);

            clock(4);

            cycle(0, "Fetch opcode"               ).read(0x0100).a(0x00);
            cycle(1, "Fetch address"              ).read(0x0101).a(0x00);
            cycle(2, "Read from effective address").read(0x0001).a(0x00);
            cycle(3, "Fetch opcode"               ).read(0x0102).a(0x01);
        }

        @Test
        void testZeroPageX() {
            cpu.pc(0x0100);
            memory(0x0002, 0x01);
            memory(0x0100, 0xA2); // LDX #01
            memory(0x0101, 0x01);
            memory(0x0102, 0xB5); // LDA $01,X
            memory(0x0103, 0x01);

            clock(7);

            cycle(0, "Fetch opcode"                ).read(0x0100).a(0x00).x(0x00);
            cycle(1, "Fetch value"                 ).read(0x0101).a(0x00).x(0x00);
            cycle(2, "Fetch opcode"                ).read(0x0102).a(0x00).x(0x01);
            cycle(3, "Fetch address"               ).read(0x0103).a(0x00).x(0x01);
            cycle(4, "Read from address, add index").read(0x0001).a(0x00).x(0x01);
            cycle(5, "Read from address           ").read(0x0002).a(0x00).x(0x01);
            cycle(6, "Fetch opcode"                ).read(0x0104).a(0x01).x(0x01);
        }

        @Test
        void testZeroPageXPageCrossing() {
            cpu.pc(0x0100);
            memory(0x0001, 0x01);
            memory(0x0100, 0xA2); // LDX #02
            memory(0x0101, 0x02);
            memory(0x0102, 0xB5); // LDA $FF,X
            memory(0x0103, 0xFF);

            clock(7);

            cycle(0, "Fetch opcode"                ).read(0x0100).a(0x00).x(0x00);
            cycle(1, "Fetch value"                 ).read(0x0101).a(0x00).x(0x00);
            cycle(2, "Fetch opcode"                ).read(0x0102).a(0x00).x(0x02);
            cycle(3, "Fetch address"               ).read(0x0103).a(0x00).x(0x02);
            cycle(4, "Read from address, add index").read(0x00FF).a(0x00).x(0x02);
            cycle(5, "Read from address           ").read(0x0001).a(0x00).x(0x02);
            cycle(6, "Fetch opcode"                ).read(0x0104).a(0x01).x(0x02);
        }

        @Test
        void testAbsolute() {
            cpu.pc(0x0100);
            memory(0x1234, 0x01);
            memory(0x0100, 0xAD); // LDA $1234
            memory(0x0101, 0x34);
            memory(0x0102, 0x12);

            clock(5);

            cycle(0, "Fetch opcode"               ).read(0x0100).a(0x00);
            cycle(1, "Fetch address low byte"     ).read(0x0101).a(0x00);
            cycle(2, "Fetch address high byte"    ).read(0x0102).a(0x00);
            cycle(3, "Read from effective address").read(0x1234).a(0x00);
            cycle(4, "Fetch opcode"               ).read(0x0103).a(0x01);
        }

        @Test
        void testAbsoluteX() {
            cpu.pc(0x0100);
            memory(0x1235, 0x01);
            memory(0x0100, 0xA2); // LDX #01
            memory(0x0101, 0x01);
            memory(0x0102, 0xBD); // LDA $1234,X
            memory(0x0103, 0x34);
            memory(0x0104, 0x12);

            clock(7);

            cycle(0, "Fetch opcode"                      ).read(0x0100).a(0x00).x(0x00);
            cycle(1, "Fetch value"                       ).read(0x0101).a(0x00).x(0x00);
            cycle(2, "Fetch opcode"                      ).read(0x0102).a(0x00).x(0x01);
            cycle(3, "Fetch address low byte"            ).read(0x0103).a(0x00).x(0x01);
            cycle(4, "Fetch address high byte, add index").read(0x0104).a(0x00).x(0x01);
            cycle(5, "Read from address"                 ).read(0x1235).a(0x00).x(0x01);
            cycle(6, "Fetch opcode"                      ).read(0x0105).a(0x01).x(0x01);
        }

        @Test
        void testAbsoluteXPageCrossing() {
            cpu.pc(0x0100);
            memory(0x1301, 0x01);
            memory(0x0100, 0xA2); // LDX #02
            memory(0x0101, 0x02);
            memory(0x0102, 0xBD); // LDA $12FF,X
            memory(0x0103, 0xFF);
            memory(0x0104, 0x12);

            clock(8);

            cycle(0, "Fetch opcode"                      ).read(0x0100).a(0x00).x(0x00);
            cycle(1, "Fetch value"                       ).read(0x0101).a(0x00).x(0x00);
            cycle(2, "Fetch opcode"                      ).read(0x0102).a(0x00).x(0x02);
            cycle(3, "Fetch address low byte"            ).read(0x0103).a(0x00).x(0x02);
            cycle(4, "Fetch address high byte, add index").read(0x0104).a(0x00).x(0x02);
            cycle(5, "Read from address, fix high byte"  ).read(0x1201).a(0x00).x(0x02);
            cycle(6, "Read from address"                 ).read(0x1301).a(0x00).x(0x02);
            cycle(7, "Fetch opcode"                      ).read(0x0105).a(0x01).x(0x02);
        }

        @Test
        void testAbsoluteXPageCrossingEndOfMemory() {
            cpu.pc(0x0100);
            memory(0x0001, 0x01);
            memory(0x0100, 0xA2); // LDX #02
            memory(0x0101, 0x02);
            memory(0x0102, 0xBD);
            memory(0x0103, 0xFF); // LDA $FFFF,X
            memory(0x0104, 0xFF);

            clock(8);

            cycle(0, "Fetch opcode"                      ).read(0x0100).a(0x00).x(0x00);
            cycle(1, "Fetch value"                       ).read(0x0101).a(0x00).x(0x00);
            cycle(2, "Fetch opcode"                      ).read(0x0102).a(0x00).x(0x02);
            cycle(3, "Fetch address low byte"            ).read(0x0103).a(0x00).x(0x02);
            cycle(4, "Fetch address high byte, add index").read(0x0104).a(0x00).x(0x02);
            cycle(5, "Read from address, fix high byte"  ).read(0xFF01).a(0x00).x(0x02);
            cycle(6, "Read from address"                 ).read(0x0001).a(0x00).x(0x02);
            cycle(7, "Fetch opcode"                      ).read(0x0105).a(0x01).x(0x02);
        }

        @Test
        void testAbsoluteY() {
            cpu.pc(0x0100);
            memory(0x1235, 0x01);
            memory(0x0100, 0xA0); // LDY #01
            memory(0x0101, 0x01);
            memory(0x0102, 0xB9); // LDA $1234,X
            memory(0x0103, 0x34);
            memory(0x0104, 0x12);

            clock(7);

            cycle(0, "Fetch opcode"                      ).read(0x0100).a(0x00).y(0x00);
            cycle(1, "Fetch value"                       ).read(0x0101).a(0x00).y(0x00);
            cycle(2, "Fetch opcode"                      ).read(0x0102).a(0x00).y(0x01);
            cycle(3, "Fetch address low byte"            ).read(0x0103).a(0x00).y(0x01);
            cycle(4, "Fetch address high byte, add index").read(0x0104).a(0x00).y(0x01);
            cycle(5, "Read from address"                 ).read(0x1235).a(0x00).y(0x01);
            cycle(6, "Fetch opcode"                      ).read(0x0105).a(0x01).y(0x01);
        }

        @Test
        void testIndexedIndirect() {
            cpu.pc(0x0100);
            memory(0x0002, 0x34);
            memory(0x0003, 0x12);
            memory(0x1234, 0x01);
            memory(0x0100, 0xA2); // LDX #01
            memory(0x0101, 0x01);
            memory(0x0102, 0xA1); // LDA ($01,X)
            memory(0x0103, 0x01);

            clock(9);

            cycle(0, "Fetch opcode"                      ).read(0x0100).a(0x00).x(0x00);
            cycle(1, "Fetch value"                       ).read(0x0101).a(0x00).x(0x00);
            cycle(2, "Fetch opcode"                      ).read(0x0102).a(0x00).x(0x01);
            cycle(3, "Fetch pointer address"             ).read(0x0103).a(0x00).x(0x01);
            cycle(4, "Read from pointer, add X"          ).read(0x0001).a(0x00).x(0x01);
            cycle(5, "Fetch address low byte"            ).read(0x0002).a(0x00).x(0x01);
            cycle(6, "Fetch address high byte"           ).read(0x0003).a(0x00).x(0x01);
            cycle(7, "Read from address"                 ).read(0x1234).a(0x00).x(0x01);
            cycle(8, "Fetch opcode"                      ).read(0x0104).a(0x01).x(0x01);
        }

        @Test
        void testIndexedIndirectPageCrossing() {
            cpu.pc(0x0100);
            memory(0x0000, 0x34);
            memory(0x0001, 0x12);
            memory(0x1234, 0x01);
            memory(0x0100, 0xA2); // LDX #01
            memory(0x0101, 0x01);
            memory(0x0102, 0xA1); // LDA ($FF,X)
            memory(0x0103, 0xFF);

            clock(9);

            cycle(0, "Fetch opcode"                      ).read(0x0100).a(0x00).x(0x00);
            cycle(1, "Fetch value"                       ).read(0x0101).a(0x00).x(0x00);
            cycle(2, "Fetch opcode"                      ).read(0x0102).a(0x00).x(0x01);
            cycle(3, "Fetch pointer address"             ).read(0x0103).a(0x00).x(0x01);
            cycle(4, "Read from pointer, add X"          ).read(0x00FF).a(0x00).x(0x01);
            cycle(5, "Fetch address low byte"            ).read(0x0000).a(0x00).x(0x01);
            cycle(6, "Fetch address high byte"           ).read(0x0001).a(0x00).x(0x01);
            cycle(7, "Read from address"                 ).read(0x1234).a(0x00).x(0x01);
            cycle(8, "Fetch opcode"                      ).read(0x0104).a(0x01).x(0x01);
        }

        @Test
        void testIndexedIndirectPageCrossingAddressStraddlesBoundary() {
            cpu.pc(0x0100);
            memory(0x00FF, 0x34);
            memory(0x0000, 0x12);
            memory(0x1234, 0x01);
            memory(0x0100, 0xA2); // LDX #01
            memory(0x0101, 0x01);
            memory(0x0102, 0xA1); // LDA ($FE,X)
            memory(0x0103, 0xFE);

            clock(9);

            cycle(0, "Fetch opcode"                      ).read(0x0100).a(0x00).x(0x00);
            cycle(1, "Fetch value"                       ).read(0x0101).a(0x00).x(0x00);
            cycle(2, "Fetch opcode"                      ).read(0x0102).a(0x00).x(0x01);
            cycle(3, "Fetch pointer address"             ).read(0x0103).a(0x00).x(0x01);
            cycle(4, "Read from pointer, add X"          ).read(0x00FE).a(0x00).x(0x01);
            cycle(5, "Fetch address low byte"            ).read(0x00FF).a(0x00).x(0x01);
            cycle(6, "Fetch address high byte"           ).read(0x0000).a(0x00).x(0x01);
            cycle(7, "Read from address"                 ).read(0x1234).a(0x00).x(0x01);
            cycle(8, "Fetch opcode"                      ).read(0x0104).a(0x01).x(0x01);
        }

        @Test
        void testIndirectIndexed() {
            cpu.pc(0x0100);
            memory(0x0001, 0x34);
            memory(0x0002, 0x12);
            memory(0x1235, 0x01);
            memory(0x0100, 0xA0); // LDY #01
            memory(0x0101, 0x01);
            memory(0x0102, 0xB1); // LDA ($01),Y
            memory(0x0103, 0x01);

            clock(8);

            cycle(0, "Fetch opcode"                      ).read(0x0100).a(0x00).y(0x00);
            cycle(1, "Fetch value"                       ).read(0x0101).a(0x00).y(0x00);
            cycle(2, "Fetch opcode"                      ).read(0x0102).a(0x00).y(0x01);
            cycle(3, "Fetch pointer address"             ).read(0x0103).a(0x00).y(0x01);
            cycle(4, "Fetch address low byte"            ).read(0x0001).a(0x00).y(0x01);
            cycle(5, "Fetch address high byte, add Y"    ).read(0x0002).a(0x00).y(0x01);
            cycle(6, "Read from address"                 ).read(0x1235).a(0x00).y(0x01);
            cycle(7, "Fetch opcode"                      ).read(0x0104).a(0x01).y(0x01);
        }

        @Test
        void testIndirectIndexedPointerStraddlesPageBoundary() {
            cpu.pc(0x0100);
            memory(0x00FF, 0x34);
            memory(0x0000, 0x12);
            memory(0x1235, 0x01);
            memory(0x0100, 0xA0); // LDY #01
            memory(0x0101, 0x01);
            memory(0x0102, 0xB1); // LDA ($FF),Y
            memory(0x0103, 0xFF);

            clock(8);

            cycle(0, "Fetch opcode"                      ).read(0x0100).a(0x00).y(0x00);
            cycle(1, "Fetch value"                       ).read(0x0101).a(0x00).y(0x00);
            cycle(2, "Fetch opcode"                      ).read(0x0102).a(0x00).y(0x01);
            cycle(3, "Fetch pointer address"             ).read(0x0103).a(0x00).y(0x01);
            cycle(4, "Fetch address low byte"            ).read(0x00FF).a(0x00).y(0x01);
            cycle(5, "Fetch address high byte, add Y"    ).read(0x0000).a(0x00).y(0x01);
            cycle(6, "Read from address"                 ).read(0x1235).a(0x00).y(0x01);
            cycle(7, "Fetch opcode"                      ).read(0x0104).a(0x01).y(0x01);
        }

        @Test
        void testIndirectIndexedCrossesPageBoundary() {
            cpu.pc(0x0100);
            memory(0x0001, 0xFF);
            memory(0x0002, 0x12);
            memory(0x1301, 0x01);
            memory(0x0100, 0xA0); // LDY #02
            memory(0x0101, 0x02);
            memory(0x0102, 0xB1); // LDA ($01),Y
            memory(0x0103, 0x01);

            clock(9);

            cycle(0, "Fetch opcode"                      ).read(0x0100).a(0x00).y(0x00);
            cycle(1, "Fetch value"                       ).read(0x0101).a(0x00).y(0x00);
            cycle(2, "Fetch opcode"                      ).read(0x0102).a(0x00).y(0x02);
            cycle(3, "Fetch pointer address"             ).read(0x0103).a(0x00).y(0x02);
            cycle(4, "Fetch address low byte"            ).read(0x0001).a(0x00).y(0x02);
            cycle(5, "Fetch address high byte, add Y"    ).read(0x0002).a(0x00).y(0x02);
            cycle(6, "Read from address, fix high byte"  ).read(0x1201).a(0x00).y(0x02);
            cycle(7, "Read from address"                 ).read(0x1301).a(0x00).y(0x02);
            cycle(8, "Fetch opcode"                      ).read(0x0104).a(0x01).y(0x02);
        }

        @Test
        void testZeroFlag() {
            cpu.pc(0x0100);
            memory(0x0100, 0xA9); // LDA #00
            memory(0x0101, 0x00);

            clock(3);

            cycle(0, "Fetch opcode").read(0x0100).a(0x00).flags("..1..I..");
            cycle(1, "Fetch value" ).read(0x0101).a(0x00).flags("..1..I..");
            cycle(2, "Fetch opcode").read(0x0102).a(0x00).flags("..1..IZ.");
        }

        @Test
        void testNegativeFlag() {
            cpu.pc(0x0100);
            memory(0x0100, 0xA9); // LDA #80
            memory(0x0101, 0x80);

            clock(3);

            cycle(0, "Fetch opcode").read(0x0100).a(0x00).flags("..1..I..");
            cycle(1, "Fetch value" ).read(0x0101).a(0x00).flags("..1..I..");
            cycle(2, "Fetch opcode").read(0x0102).a(0x80).flags("N.1..I..");
        }
    }

    @Nested
    class LDX {
        @Test
        void testImmediate() {
            cpu.pc(0x0100);
            memory(0x0100, 0xA2);
            memory(0x0101, 0x01);

            clock(3);

            cycle(0, "Fetch opcode").read(0x0100).x(0x00);
            cycle(1, "Fetch value" ).read(0x0101).x(0x00);
            cycle(2, "Fetch opcode").read(0x0102).x(0x01);
        }

        @Test
        void testZeroPage() {
            cpu.pc(0x0100);
            memory(0x0001, 0x01);
            memory(0x0100, 0xA6);
            memory(0x0101, 0x01);

            clock(4);

            cycle(0, "Fetch opcode"               ).read(0x0100).x(0x00);
            cycle(1, "Fetch address"              ).read(0x0101).x(0x00);
            cycle(2, "Read from effective address").read(0x0001).x(0x00);
            cycle(3, "Fetch opcode"               ).read(0x0102).x(0x01);
        }

        @Test
        void testZeroPageY() {
            cpu.pc(0x0100);
            memory(0x0002, 0x01);
            memory(0x0100, 0xA0);
            memory(0x0101, 0x01);
            memory(0x0102, 0xB6);
            memory(0x0103, 0x01);

            clock(7);

            cycle(0, "Fetch opcode"                ).read(0x0100).x(0x00).y(0x00);
            cycle(1, "Fetch value"                 ).read(0x0101).x(0x00).y(0x00);
            cycle(2, "Fetch opcode"                ).read(0x0102).x(0x00).y(0x01);
            cycle(3, "Fetch address"               ).read(0x0103).x(0x00).y(0x01);
            cycle(4, "Read from address, add index").read(0x0001).x(0x00).y(0x01);
            cycle(5, "Read from address           ").read(0x0002).x(0x00).y(0x01);
            cycle(6, "Fetch opcode"                ).read(0x0104).x(0x01).y(0x01);
        }

        @Test
        void testAbsolute() {
            cpu.pc(0x0100);
            memory(0x1234, 0x01);
            memory(0x0100, 0xAE);
            memory(0x0101, 0x34);
            memory(0x0102, 0x12);

            clock(5);

            cycle(0, "Fetch opcode"               ).read(0x0100).x(0x00);
            cycle(1, "Fetch address low byte"     ).read(0x0101).x(0x00);
            cycle(2, "Fetch address high byte"    ).read(0x0102).x(0x00);
            cycle(3, "Read from effective address").read(0x1234).x(0x00);
            cycle(4, "Fetch opcode"               ).read(0x0103).x(0x01);
        }

        @Test
        void testAbsoluteY() {
            cpu.pc(0x0100);
            memory(0x1235, 0x01);
            memory(0x0100, 0xA0);
            memory(0x0101, 0x01);
            memory(0x0102, 0xBE);
            memory(0x0103, 0x34);
            memory(0x0104, 0x12);

            clock(7);

            cycle(0, "Fetch opcode"                      ).read(0x0100).x(0x00).y(0x00);
            cycle(1, "Fetch value"                       ).read(0x0101).x(0x00).y(0x00);
            cycle(2, "Fetch opcode"                      ).read(0x0102).x(0x00).y(0x01);
            cycle(3, "Fetch address low byte"            ).read(0x0103).x(0x00).y(0x01);
            cycle(4, "Fetch address high byte, add index").read(0x0104).x(0x00).y(0x01);
            cycle(5, "Read from address"                 ).read(0x1235).x(0x00).y(0x01);
            cycle(6, "Fetch opcode"                      ).read(0x0105).x(0x01).y(0x01);
        }

        @Test
        void testZeroFlag() {
            cpu.pc(0x0100);
            memory(0x0100, 0xA2);
            memory(0x0101, 0x00);

            clock(3);

            cycle(0, "Fetch opcode").read(0x0100).x(0x00).flags("..1..I..");
            cycle(1, "Fetch value" ).read(0x0101).x(0x00).flags("..1..I..");
            cycle(2, "Fetch opcode").read(0x0102).x(0x00).flags("..1..IZ.");
        }

        @Test
        void testNegativeFlag() {
            cpu.pc(0x0100);
            memory(0x0100, 0xA2);
            memory(0x0101, 0x80);

            clock(3);

            cycle(0, "Fetch opcode").read(0x0100).x(0x00).flags("..1..I..");
            cycle(1, "Fetch value" ).read(0x0101).x(0x00).flags("..1..I..");
            cycle(2, "Fetch opcode").read(0x0102).x(0x80).flags("N.1..I..");
        }
    }

    @Nested
    class LDY {
        @Test
        void testImmediate() {
            cpu.pc(0x0100);
            memory(0x0100, 0xA0);
            memory(0x0101, 0x01);

            clock(3);

            cycle(0, "Fetch opcode").read(0x0100).y(0x00);
            cycle(1, "Fetch value" ).read(0x0101).y(0x00);
            cycle(2, "Fetch opcode").read(0x0102).y(0x01);
        }

        @Test
        void testZeroPage() {
            cpu.pc(0x0100);
            memory(0x0001, 0x01);
            memory(0x0100, 0xA4);
            memory(0x0101, 0x01);

            clock(4);

            cycle(0, "Fetch opcode"               ).read(0x0100).y(0x00);
            cycle(1, "Fetch address"              ).read(0x0101).y(0x00);
            cycle(2, "Read from effective address").read(0x0001).y(0x00);
            cycle(3, "Fetch opcode"               ).read(0x0102).y(0x01);
        }

        @Test
        void testZeroPageY() {
            cpu.pc(0x0100);
            memory(0x0002, 0x01);
            memory(0x0100, 0xA2);
            memory(0x0101, 0x01);
            memory(0x0102, 0xB4);
            memory(0x0103, 0x01);

            clock(7);

            cycle(0, "Fetch opcode"                ).read(0x0100).y(0x00).x(0x00);
            cycle(1, "Fetch value"                 ).read(0x0101).y(0x00).x(0x00);
            cycle(2, "Fetch opcode"                ).read(0x0102).y(0x00).x(0x01);
            cycle(3, "Fetch address"               ).read(0x0103).y(0x00).x(0x01);
            cycle(4, "Read from address, add index").read(0x0001).y(0x00).x(0x01);
            cycle(5, "Read from address           ").read(0x0002).y(0x00).x(0x01);
            cycle(6, "Fetch opcode"                ).read(0x0104).y(0x01).x(0x01);
        }

        @Test
        void testAbsolute() {
            cpu.pc(0x0100);
            memory(0x1234, 0x01);
            memory(0x0100, 0xAC);
            memory(0x0101, 0x34);
            memory(0x0102, 0x12);

            clock(5);

            cycle(0, "Fetch opcode"               ).read(0x0100).y(0x00);
            cycle(1, "Fetch address low byte"     ).read(0x0101).y(0x00);
            cycle(2, "Fetch address high byte"    ).read(0x0102).y(0x00);
            cycle(3, "Read from effective address").read(0x1234).y(0x00);
            cycle(4, "Fetch opcode"               ).read(0x0103).y(0x01);
        }

        @Test
        void testAbsoluteX() {
            cpu.pc(0x0100);
            memory(0x1235, 0x01);
            memory(0x0100, 0xA2);
            memory(0x0101, 0x01);
            memory(0x0102, 0xBC);
            memory(0x0103, 0x34);
            memory(0x0104, 0x12);

            clock(7);

            cycle(0, "Fetch opcode"                      ).read(0x0100).y(0x00).x(0x00);
            cycle(1, "Fetch value"                       ).read(0x0101).y(0x00).x(0x00);
            cycle(2, "Fetch opcode"                      ).read(0x0102).y(0x00).x(0x01);
            cycle(3, "Fetch address low byte"            ).read(0x0103).y(0x00).x(0x01);
            cycle(4, "Fetch address high byte, add index").read(0x0104).y(0x00).x(0x01);
            cycle(5, "Read from address"                 ).read(0x1235).y(0x00).x(0x01);
            cycle(6, "Fetch opcode"                      ).read(0x0105).y(0x01).x(0x01);
        }

        @Test
        void testZeroFlag() {
            cpu.pc(0x0100);
            memory(0x0100, 0xA0);
            memory(0x0101, 0x00);

            clock(3);

            cycle(0, "Fetch opcode").read(0x0100).y(0x00).flags("..1..I..");
            cycle(1, "Fetch value" ).read(0x0101).y(0x00).flags("..1..I..");
            cycle(2, "Fetch opcode").read(0x0102).y(0x00).flags("..1..IZ.");
        }

        @Test
        void testNegativeFlag() {
            cpu.pc(0x0100);
            memory(0x0100, 0xA0);
            memory(0x0101, 0x80);

            clock(3);

            cycle(0, "Fetch opcode").read(0x0100).y(0x00).flags("..1..I..");
            cycle(1, "Fetch value" ).read(0x0101).y(0x00).flags("..1..I..");
            cycle(2, "Fetch opcode").read(0x0102).y(0x80).flags("N.1..I..");
        }
    }

    @Nested
    class STA {
        @Test
        void testZeroPage() {
            cpu.pc(0x0100);
            memory(0x0100, 0xA9); // LDA #01
            memory(0x0101, 0x01);
            memory(0x0102, 0x85); // STA $01
            memory(0x0103, 0x01);

            clock(6);

            cycle(0, "Fetch opcode"              ).read(0x0100       ).a(0x00);
            cycle(1, "Fetch value"               ).read(0x0101       ).a(0x00);
            cycle(2, "Fetch opcode"              ).read(0x0102       ).a(0x01);
            cycle(3, "Fetch address"             ).read(0x0103       ).a(0x01);
            cycle(4, "Write to effective address").write(0x0001, 0x01).a(0x01);
            cycle(5, "Fetch opcode"              ).read(0x0104       ).a(0x01);
        }

        @Test
        void testZeroPageX() {
            cpu.pc(0x0100);
            memory(0x0100, 0xA9); // LDX #01
            memory(0x0101, 0x01);
            memory(0x0102, 0xA2); // LDA #01
            memory(0x0103, 0x01);
            memory(0x0104, 0x95); // STA $01,X
            memory(0x0105, 0x01);

            clock(9);

            cycle(0, "Fetch opcode"                ).read(0x0100       ).a(0x00).x(0x00);
            cycle(1, "Fetch value"                 ).read(0x0101       ).a(0x00).x(0x00);
            cycle(2, "Fetch opcode"                ).read(0x0102       ).a(0x01).x(0x00);
            cycle(3, "Fetch value"                 ).read(0x0103       ).a(0x01).x(0x00);
            cycle(4, "Fetch opcode"                ).read(0x0104       ).a(0x01).x(0x01);
            cycle(5, "Fetch address"               ).read(0x0105       ).a(0x01).x(0x01);
            cycle(6, "Read from address, add index").read(0x0001       ).a(0x01).x(0x01);
            cycle(7, "Write to address"            ).write(0x0002, 0x01).a(0x01).x(0x01);
            cycle(8, "Fetch opcode"                ).read(0x0106       ).a(0x01).x(0x01);
        }

        @Test
        void testAbsolute() {
            cpu.pc(0x0100);
            memory(0x0100, 0xA9); // LDA #01
            memory(0x0101, 0x01);
            memory(0x0102, 0x8D); // STA $1234
            memory(0x0103, 0x34);
            memory(0x0104, 0x12);

            clock(7);

            cycle(0, "Fetch opcode"              ).read(0x0100       ).a(0x00);
            cycle(1, "Fetch value"               ).read(0x0101       ).a(0x00);
            cycle(2, "Fetch opcode"              ).read(0x0102       ).a(0x01);
            cycle(3, "Fetch address low byte"    ).read(0x0103       ).a(0x01);
            cycle(4, "Fetch address high byte"   ).read(0x0104       ).a(0x01);
            cycle(5, "Write to effective address").write(0x1234, 0x01).a(0x01);
            cycle(6, "Fetch opcode"              ).read(0x0105       ).a(0x01);
        }

        @Test
        void testAbsoluteX() {
            cpu.pc(0x0100);
            memory(0x0100, 0xA9); // LDA #01
            memory(0x0101, 0x01);
            memory(0x0102, 0xA2); // LDX #01
            memory(0x0103, 0x01);
            memory(0x0104, 0x9D); // STA $1234,X
            memory(0x0105, 0x34);
            memory(0x0106, 0x12);

            clock(10);

            cycle(0, "Fetch opcode"                      ).read(0x0100       ).a(0x00).x(0x00);
            cycle(1, "Fetch value"                       ).read(0x0101       ).a(0x00).x(0x00);
            cycle(2, "Fetch opcode"                      ).read(0x0102       ).a(0x01).x(0x00);
            cycle(3, "Fetch value"                       ).read(0x0103       ).a(0x01).x(0x00);
            cycle(4, "Fetch opcode"                      ).read(0x0104       ).a(0x01).x(0x01);
            cycle(5, "Fetch address low byte"            ).read(0x0105       ).a(0x01).x(0x01);
            cycle(6, "Fetch address high byte, add index").read(0x0106       ).a(0x01).x(0x01);
            cycle(7, "Read from address"                 ).read(0x1235       ).a(0x01).x(0x01);
            cycle(8, "Write to address"                  ).write(0x1235, 0x01).a(0x01).x(0x01);
            cycle(9, "Fetch opcode"                      ).read(0x0107       ).a(0x01).x(0x01);
        }

        @Test
        void testAbsoluteXPageCrossing() {
            cpu.pc(0x0100);
            memory(0x0100, 0xA9); // LDA #01
            memory(0x0101, 0x01);
            memory(0x0102, 0xA2); // LDX #02
            memory(0x0103, 0x02);
            memory(0x0104, 0x9D); // STA $12FF,X
            memory(0x0105, 0xFF);
            memory(0x0106, 0x12);

            clock(10);

            cycle(0, "Fetch opcode"                      ).read(0x0100       ).a(0x00).x(0x00);
            cycle(1, "Fetch value"                       ).read(0x0101       ).a(0x00).x(0x00);
            cycle(2, "Fetch opcode"                      ).read(0x0102       ).a(0x01).x(0x00);
            cycle(3, "Fetch value"                       ).read(0x0103       ).a(0x01).x(0x00);
            cycle(4, "Fetch opcode"                      ).read(0x0104       ).a(0x01).x(0x02);
            cycle(5, "Fetch address low byte"            ).read(0x0105       ).a(0x01).x(0x02);
            cycle(6, "Fetch address high byte, add index").read(0x0106       ).a(0x01).x(0x02);
            cycle(7, "Read from address, fix high byte"  ).read(0x1201       ).a(0x01).x(0x02);
            cycle(8, "Write to address"                  ).write(0x1301, 0x01).a(0x01).x(0x02);
            cycle(9, "Fetch opcode"                      ).read(0x0107       ).a(0x01).x(0x02);
        }

        @Test
        void testAbsoluteY() {
            cpu.pc(0x0100);
            memory(0x0100, 0xA9);
            memory(0x0101, 0x01);
            memory(0x0102, 0xA0);
            memory(0x0103, 0x01);
            memory(0x0104, 0x99);
            memory(0x0105, 0x34);
            memory(0x0106, 0x12);

            clock(10);

            cycle(0, "Fetch opcode"                      ).read(0x0100       ).a(0x00).y(0x00);
            cycle(1, "Fetch value"                       ).read(0x0101       ).a(0x00).y(0x00);
            cycle(2, "Fetch opcode"                      ).read(0x0102       ).a(0x01).y(0x00);
            cycle(3, "Fetch value"                       ).read(0x0103       ).a(0x01).y(0x00);
            cycle(4, "Fetch opcode"                      ).read(0x0104       ).a(0x01).y(0x01);
            cycle(5, "Fetch address low byte"            ).read(0x0105       ).a(0x01).y(0x01);
            cycle(6, "Fetch address high byte, add index").read(0x0106       ).a(0x01).y(0x01);
            cycle(7, "Read from address"                 ).read(0x1235       ).a(0x01).y(0x01);
            cycle(8, "Write to address"                  ).write(0x1235, 0x01).a(0x01).y(0x01);
            cycle(9, "Fetch opcode"                      ).read(0x0107       ).a(0x01).y(0x01);
        }

        @Test
        void testIndexedIndirect() {
            cpu.pc(0x0100);
            memory(0x0002, 0x34);
            memory(0x0003, 0x12);
            memory(0x0100, 0xA9);
            memory(0x0101, 0x01);
            memory(0x0102, 0xA2);
            memory(0x0103, 0x01);
            memory(0x0104, 0x81);
            memory(0x0105, 0x01);

            clock(11);

            cycle(0,  "Fetch opcode"            ).read(0x0100       ).a(0x00).x(0x00);
            cycle(1,  "Fetch value"             ).read(0x0101       ).a(0x00).x(0x00);
            cycle(2,  "Fetch opcode"            ).read(0x0102       ).a(0x01).x(0x00);
            cycle(3,  "Fetch value"             ).read(0x0103       ).a(0x01).x(0x00);
            cycle(4,  "Fetch opcode"            ).read(0x0104       ).a(0x01).x(0x01);
            cycle(5,  "Fetch pointer address"   ).read(0x0105       ).a(0x01).x(0x01);
            cycle(6,  "Read from pointer, add X").read(0x0001       ).a(0x01).x(0x01);
            cycle(7,  "Fetch address low byte"  ).read(0x0002       ).a(0x01).x(0x01);
            cycle(8,  "Fetch address high byte" ).read(0x0003       ).a(0x01).x(0x01);
            cycle(9,  "Write to address"        ).write(0x1234, 0x01).a(0x01).x(0x01);
            cycle(10, "Fetch opcode"            ).read(0x0106       ).a(0x01).x(0x01);
        }

        @Test
        void testIndirectIndexed() {
            cpu.pc(0x0100);
            memory(0x0001, 0x34);
            memory(0x0002, 0x12);
            memory(0x0100, 0xA9);
            memory(0x0101, 0x01);
            memory(0x0102, 0xA0);
            memory(0x0103, 0x01);
            memory(0x0104, 0x91);
            memory(0x0105, 0x01);

            clock(11);

            cycle(0,  "Fetch opcode"                    ).read(0x0100       ).a(0x00).y(0x00);
            cycle(1,  "Fetch value"                     ).read(0x0101       ).a(0x00).y(0x00);
            cycle(2,  "Fetch opcode"                    ).read(0x0102       ).a(0x01).y(0x00);
            cycle(3,  "Fetch value"                     ).read(0x0103       ).a(0x01).y(0x00);
            cycle(4,  "Fetch opcode"                    ).read(0x0104       ).a(0x01).y(0x01);
            cycle(5,  "Fetch pointer address"           ).read(0x0105       ).a(0x01).y(0x01);
            cycle(6,  "Fetch address low byte"          ).read(0x0001       ).a(0x01).y(0x01);
            cycle(7,  "Fetch address high byte, add Y"  ).read(0x0002       ).a(0x01).y(0x01);
            cycle(8,  "Read from address, fix high byte").read(0x1235       ).a(0x01).y(0x01);
            cycle(9,  "Write to address "               ).write(0x1235, 0x01).a(0x01).y(0x01);
            cycle(10, "Fetch opcode"                    ).read(0x0106       ).a(0x01).y(0x01);
        }

        @Test
        void testIndirectIndexedCrossesPageBoundary() {
            cpu.pc(0x0100);
            memory(0x0001, 0xFF);
            memory(0x0002, 0x12);
            memory(0x0100, 0xA9);
            memory(0x0101, 0x01);
            memory(0x0102, 0xA0);
            memory(0x0103, 0x02);
            memory(0x0104, 0x91);
            memory(0x0105, 0x01);

            clock(11);

            cycle(0,  "Fetch opcode"                    ).read(0x0100       ).a(0x00).y(0x00);
            cycle(1,  "Fetch value"                     ).read(0x0101       ).a(0x00).y(0x00);
            cycle(2,  "Fetch opcode"                    ).read(0x0102       ).a(0x01).y(0x00);
            cycle(3,  "Fetch value"                     ).read(0x0103       ).a(0x01).y(0x00);
            cycle(4,  "Fetch opcode"                    ).read(0x0104       ).a(0x01).y(0x02);
            cycle(5,  "Fetch pointer address"           ).read(0x0105       ).a(0x01).y(0x02);
            cycle(6,  "Fetch address low byte"          ).read(0x0001       ).a(0x01).y(0x02);
            cycle(7,  "Fetch address high byte, add Y"  ).read(0x0002       ).a(0x01).y(0x02);
            cycle(8,  "Read from address, fix high byte").read(0x1201       ).a(0x01).y(0x02);
            cycle(9,  "Write to address "               ).write(0x1301, 0x01).a(0x01).y(0x02);
            cycle(10, "Fetch opcode"                    ).read(0x0106       ).a(0x01).y(0x02);
        }
    }

    @Nested
    class STX {
        @Test
        void testZeroPage() {
            cpu.pc(0x0100);
            memory(0x0100, 0xA2);
            memory(0x0101, 0x01);
            memory(0x0102, 0x86);
            memory(0x0103, 0x01);

            clock(6);

            cycle(0, "Fetch opcode"              ).read(0x0100       ).x(0x00);
            cycle(1, "Fetch value"               ).read(0x0101       ).x(0x00);
            cycle(2, "Fetch opcode"              ).read(0x0102       ).x(0x01);
            cycle(3, "Fetch address"             ).read(0x0103       ).x(0x01);
            cycle(4, "Write to effective address").write(0x0001, 0x01).x(0x01);
            cycle(5, "Fetch opcode"              ).read(0x0104       ).x(0x01);
        }

        @Test
        void testZeroPageY() {
            cpu.pc(0x0100);
            memory(0x0100, 0xA2);
            memory(0x0101, 0x01);
            memory(0x0102, 0xA0);
            memory(0x0103, 0x01);
            memory(0x0104, 0x96);
            memory(0x0105, 0x01);

            clock(9);

            cycle(0, "Fetch opcode"                ).read(0x0100       ).x(0x00).y(0x00);
            cycle(1, "Fetch value"                 ).read(0x0101       ).x(0x00).y(0x00);
            cycle(2, "Fetch opcode"                ).read(0x0102       ).x(0x01).y(0x00);
            cycle(3, "Fetch value"                 ).read(0x0103       ).x(0x01).y(0x00);
            cycle(4, "Fetch opcode"                ).read(0x0104       ).x(0x01).y(0x01);
            cycle(5, "Fetch address"               ).read(0x0105       ).x(0x01).y(0x01);
            cycle(6, "Read from address, add index").read(0x0001       ).x(0x01).y(0x01);
            cycle(7, "Write to address"            ).write(0x0002, 0x01).x(0x01).y(0x01);
            cycle(8, "Fetch opcode"                ).read(0x0106       ).x(0x01).y(0x01);
        }

        @Test
        void testAbsolute() {
            cpu.pc(0x0100);
            memory(0x0100, 0xA2);
            memory(0x0101, 0x01);
            memory(0x0102, 0x8E);
            memory(0x0103, 0x34);
            memory(0x0104, 0x12);

            clock(7);

            cycle(0, "Fetch opcode"              ).read(0x0100       ).x(0x00);
            cycle(1, "Fetch value"               ).read(0x0101       ).x(0x00);
            cycle(2, "Fetch opcode"              ).read(0x0102       ).x(0x01);
            cycle(3, "Fetch address low byte"    ).read(0x0103       ).x(0x01);
            cycle(4, "Fetch address high byte"   ).read(0x0104       ).x(0x01);
            cycle(5, "Write to effective address").write(0x1234, 0x01).x(0x01);
            cycle(6, "Fetch opcode"              ).read(0x0105       ).x(0x01);
        }
    }

    @Nested
    class STY {
        @Test
        void testZeroPage() {
            cpu.pc(0x0100);
            memory(0x0100, 0xA0);
            memory(0x0101, 0x01);
            memory(0x0102, 0x84);
            memory(0x0103, 0x01);

            clock(6);

            cycle(0, "Fetch opcode"              ).read(0x0100       ).y(0x00);
            cycle(1, "Fetch value"               ).read(0x0101       ).y(0x00);
            cycle(2, "Fetch opcode"              ).read(0x0102       ).y(0x01);
            cycle(3, "Fetch address"             ).read(0x0103       ).y(0x01);
            cycle(4, "Write to effective address").write(0x0001, 0x01).y(0x01);
            cycle(5, "Fetch opcode"              ).read(0x0104       ).y(0x01);
        }

        @Test
        void testZeroPageX() {
            cpu.pc(0x0100);
            memory(0x0100, 0xA0);
            memory(0x0101, 0x01);
            memory(0x0102, 0xA2);
            memory(0x0103, 0x01);
            memory(0x0104, 0x94);
            memory(0x0105, 0x01);

            clock(9);

            cycle(0, "Fetch opcode"                ).read(0x0100       ).y(0x00).x(0x00);
            cycle(1, "Fetch value"                 ).read(0x0101       ).y(0x00).x(0x00);
            cycle(2, "Fetch opcode"                ).read(0x0102       ).y(0x01).x(0x00);
            cycle(3, "Fetch value"                 ).read(0x0103       ).y(0x01).x(0x00);
            cycle(4, "Fetch opcode"                ).read(0x0104       ).y(0x01).x(0x01);
            cycle(5, "Fetch address"               ).read(0x0105       ).y(0x01).x(0x01);
            cycle(6, "Read from address, add index").read(0x0001       ).y(0x01).x(0x01);
            cycle(7, "Write to address"            ).write(0x0002, 0x01).y(0x01).x(0x01);
            cycle(8, "Fetch opcode"                ).read(0x0106       ).y(0x01).x(0x01);
        }

        @Test
        void testAbsolute() {
            cpu.pc(0x0100);
            memory(0x0100, 0xA0);
            memory(0x0101, 0x01);
            memory(0x0102, 0x8C);
            memory(0x0103, 0x34);
            memory(0x0104, 0x12);

            clock(7);

            cycle(0, "Fetch opcode"              ).read(0x0100       ).y(0x00);
            cycle(1, "Fetch value"               ).read(0x0101       ).y(0x00);
            cycle(2, "Fetch opcode"              ).read(0x0102       ).y(0x01);
            cycle(3, "Fetch address low byte"    ).read(0x0103       ).y(0x01);
            cycle(4, "Fetch address high byte"   ).read(0x0104       ).y(0x01);
            cycle(5, "Write to effective address").write(0x1234, 0x01).y(0x01);
            cycle(6, "Fetch opcode"              ).read(0x0105       ).y(0x01);
        }
    }
}
