package com.experiments.nes.cpu;

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

    void clock() {
        cpu.clock();
        memory.clock();
        registers.put(cycle, Map.of(
                "a", cpu.a(),
                "x", cpu.x(),
                "y", cpu.y()));
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
    }

    @Nested
    class LDA {
        @Test
        void testImmediate() {
            cpu.pc(0x0100);
            memory(0x0100, 0xA9);
            memory(0x0101, 0x01);

            clock();
            clock();
            clock();

            cycle(0, "Fetch opcode").read(0x0100).a(0x00);
            cycle(1, "Fetch value" ).read(0x0101).a(0x00);
            cycle(2, "Fetch opcode").read(0x0102).a(0x01);
        }

        @Test
        void testZeroPage() {
            cpu.pc(0x0100);
            memory(0x0001, 0x01);
            memory(0x0100, 0xA5);
            memory(0x0101, 0x01);

            clock();
            clock();
            clock();
            clock();

            cycle(0, "Fetch opcode"               ).read(0x0100).a(0x00);
            cycle(1, "Fetch address"              ).read(0x0101).a(0x00);
            cycle(2, "Read from effective address").read(0x0001).a(0x00);
            cycle(3, "Fetch opcode"               ).read(0x0102).a(0x01);
        }

        @Test
        void testZeroPageX() {
            cpu.pc(0x0100);
            memory(0x0002, 0x01);
            memory(0x0100, 0xA2);
            memory(0x0101, 0x01);
            memory(0x0102, 0xB5);
            memory(0x0103, 0x01);

            clock();
            clock();
            clock();
            clock();
            clock();
            clock();
            clock();

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
            memory(0x0100, 0xA2);
            memory(0x0101, 0x02);
            memory(0x0102, 0xB5);
            memory(0x0103, 0xFF);

            clock();
            clock();
            clock();
            clock();
            clock();
            clock();
            clock();

            cycle(0, "Fetch opcode"                ).read(0x0100).a(0x00).x(0x00);
            cycle(1, "Fetch value"                 ).read(0x0101).a(0x00).x(0x00);
            cycle(2, "Fetch opcode"                ).read(0x0102).a(0x00).x(0x02);
            cycle(3, "Fetch address"               ).read(0x0103).a(0x00).x(0x02);
            cycle(4, "Read from address, add index").read(0x00FF).a(0x00).x(0x02);
            cycle(5, "Read from address           ").read(0x0001).a(0x00).x(0x02);
            cycle(6, "Fetch opcode"                ).read(0x0104).a(0x01).x(0x02);
        }
    }

    @Nested
    class LDX {
        @Test
        void testImmediate() {
            cpu.pc(0x0100);
            memory(0x0100, 0xA2);
            memory(0x0101, 0x01);

            clock();
            clock();
            clock();

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

            clock();
            clock();
            clock();
            clock();

            cycle(0, "Fetch opcode"               ).read(0x0100).x(0x00);
            cycle(1, "Fetch address"              ).read(0x0101).x(0x00);
            cycle(2, "Read from effective address").read(0x0001).x(0x00);
            cycle(3, "Fetch opcode"               ).read(0x0102).x(0x01);
        }
    }

    @Nested
    class LDY {
        @Test
        void testImmediate() {
            cpu.pc(0x0100);
            memory(0x0100, 0xA0);
            memory(0x0101, 0x01);

            clock();
            clock();
            clock();

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

            clock();
            clock();
            clock();
            clock();

            cycle(0, "Fetch opcode"               ).read(0x0100).y(0x00);
            cycle(1, "Fetch address"              ).read(0x0101).y(0x00);
            cycle(2, "Read from effective address").read(0x0001).y(0x00);
            cycle(3, "Fetch opcode"               ).read(0x0102).y(0x01);
        }
    }
}
