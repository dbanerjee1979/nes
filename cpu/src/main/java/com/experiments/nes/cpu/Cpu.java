package com.experiments.nes.cpu;

public class Cpu {
    private static final short RESET_VECTOR = (short) 0xFFFC;

    public enum Flag {
        Carry(0x01), Zero(0x02), InterruptDisabled(0x04), Decimal(0x08),
        Overflow(0x40), Negative(0x80);

        private final byte mask;

        Flag(int mask) {
            this.mask = (byte) mask;
        }

        public boolean isSet(byte p) {
            return (p & mask) != 0;
        }

        public byte mask() {
            return mask;
        }

        public byte set(byte p) {
            return (byte) (p | mask);
        }
    }

    enum State {
        FETCH_OPCODE, FETCH_OPCODE_IN_SAME_CYCLE, FETCH_VALUE, FETCH_ADDRESS, READ_EFFECTIVE_ADDRESS,
        READ_EFFECTIVE_ADDRESS_ADD_INDEX, FETCH_EFFECTIVE_ADDRESS_HIGH, FETCH_EFFECTIVE_ADDRESS_HIGH_ADD_INDEX,
        READ_EFFECTIVE_ADDRESS_FIX_HIGH, DATA_AVAILABLE
    }

    private final Memory memory;
    private byte a = 0;
    private byte x = 0;
    private byte y = 0;
    private short pc = RESET_VECTOR;
    private byte s = -3;
    private byte p = Flag.InterruptDisabled.mask();
    private short address;
    private byte data;
    private State state = State.FETCH_OPCODE;
    private Operation operation;

    private final Operation[] operations = new Operation[255];
    {
        ImmediateMode immediateMode = new ImmediateMode();
        ZeroPageMode zeroPageMode = new ZeroPageMode();
        ZeroPageIndexedMode zeroPageXMode = new ZeroPageIndexedMode();
        AbsoluteMode absoluteMode = new AbsoluteMode();
        AbsoluteIndexedMode absoluteXMode = new AbsoluteIndexedMode(this::x);
        AbsoluteIndexedMode absoluteYMode = new AbsoluteIndexedMode(this::y);

        operation(0x00, new BreakOperation());
        // LDA
        operation(0xA9, new StandardOperation(immediateMode, this::loadA));
        operation(0xA5, new StandardOperation(zeroPageMode, this::loadA));
        operation(0xB5, new StandardOperation(zeroPageXMode, this::loadA));
        operation(0xAD, new StandardOperation(absoluteMode, this::loadA));
        operation(0xBD, new StandardOperation(absoluteXMode, this::loadA));
        operation(0xB9, new StandardOperation(absoluteYMode, this::loadA));
        // LDX
        operation(0xA2, new StandardOperation(immediateMode, this::loadX));
        operation(0xA6, new StandardOperation(zeroPageMode, this::loadX));
        // LDY
        operation(0xA0, new StandardOperation(immediateMode, this::loadY));
        operation(0xA4, new StandardOperation(zeroPageMode, this::loadY));
    }

    private void operation(int opcode, Operation operation) {
        this.operations[opcode & 0x00FF] = operation;
    }

    public Cpu(Memory memory) {
        this.memory = memory;
    }

    public byte a() {
        return a;
    }

    public void a(int a) {
        this.a = (byte) a;
    }

    public byte x() {
        return x;
    }

    public void x(int x) {
        this.x = (byte) x;
    }

    public byte y() {
        return y;
    }

    public void y(int y) {
        this.y = (byte) y;
    }

    public short pc() {
        return pc;
    }

    public void pc(int pc) {
        this.pc = (short) pc;
    }

    public byte s() {
        return s;
    }

    public void s(int s) {
        this.s = (byte) s;
    }

    public boolean flag(Flag flag) {
        return flag.isSet(p);
    }

    public void p(int p) {
        this.p = (byte) p;
    }

    public void reset() {
        this.pc = RESET_VECTOR;
        this.s -= 3;
        this.p = Flag.InterruptDisabled.set(p);
    }

    public void clock() {
        if (state == State.FETCH_OPCODE) {
            fetchOpcode();
        }
        state = operation.clock();
        if (state == State.FETCH_OPCODE_IN_SAME_CYCLE) {
            this.state = State.FETCH_OPCODE;
            clock();
        }
    }

    private void fetchOpcode() {
        int opcode = this.memory.load(this.pc++) & 0x00FF;
        this.operation = this.operations[opcode];
    }

    private State fetchImmediate() {
        this.data = this.memory.load(this.pc++);
        return State.DATA_AVAILABLE;
    }

    private State fetchAddress(State nextState) {
        this.address = (short) (this.memory.load(this.pc++) & 0x00FF);
        return nextState;
    }

    private State fetchAddressHigh() {
        this.address = (short) (((this.memory.load(this.pc++) & 0x00FF) << 8) | (this.address & 0x00FF));
        return State.READ_EFFECTIVE_ADDRESS;
    }

    private State fetchAddressHighAddIndex(byte index) {
        int addressLow = this.address + index;
        this.address = (short) (((this.memory.load(this.pc++) & 0x00FF) << 8) | (addressLow & 0x00FF));
        return (addressLow & 0xFF00) == 0 ? State.READ_EFFECTIVE_ADDRESS : State.READ_EFFECTIVE_ADDRESS_FIX_HIGH;
    }

    private State readEffectiveAddressFixHigh() {
        this.data = this.memory.load(this.address);
        this.address += 0x0100;
        return State.READ_EFFECTIVE_ADDRESS;
    }

    private State readEffectiveAddress() {
        this.data = this.memory.load(this.address);
        return State.DATA_AVAILABLE;
    }

    private State readEffectiveAddressAddIndex() {
        this.data = this.memory.load(this.address);
        this.address = (short) ((this.address & 0xFF00) | ((this.address + this.x) & 0x00FF));
        return State.READ_EFFECTIVE_ADDRESS;
    }

    private State executeReadOperation(Runnable operation) {
        operation.run();
        return State.FETCH_OPCODE_IN_SAME_CYCLE;
    }

    private void loadA() {
        this.a = this.data;
    }

    private void loadX() {
        this.x = this.data;
    }

    private void loadY() {
        this.y = this.data;
    }

    @FunctionalInterface
    private interface ByteSupplier {
        byte get();
    }

    private interface Operation {
        State clock();
    }

    private static class StandardOperation implements Operation {
        private final AddressingMode addressingMode;
        private final Runnable operation;

        public StandardOperation(AddressingMode addressingMode, Runnable operation) {
            this.addressingMode = addressingMode;
            this.operation = operation;
        }

        @Override
        public State clock() {
            return this.addressingMode.clock(operation);
        }
    }

    private static class BreakOperation implements Operation {
        @Override
        public State clock() {
            return State.FETCH_VALUE;
        }
    }

    private interface AddressingMode {
        State clock(Runnable operation);
    }

    private class ImmediateMode implements AddressingMode {
        @Override
        public State clock(Runnable operation) {
            return switch (state) {
                case FETCH_OPCODE -> State.FETCH_VALUE;
                case FETCH_VALUE -> fetchImmediate();
                case DATA_AVAILABLE -> executeReadOperation(operation);
                default -> throw new IllegalStateException();
            };
        }
    }

    private class ZeroPageMode implements AddressingMode {
        @Override
        public State clock(Runnable operation) {
            return switch (state) {
                case FETCH_OPCODE -> State.FETCH_ADDRESS;
                case FETCH_ADDRESS -> fetchAddress(State.READ_EFFECTIVE_ADDRESS);
                case READ_EFFECTIVE_ADDRESS -> readEffectiveAddress();
                case DATA_AVAILABLE -> executeReadOperation(operation);
                default -> throw new IllegalStateException();
            };
        }
    }

    private class ZeroPageIndexedMode implements AddressingMode {
        @Override
        public State clock(Runnable operation) {
            return switch (state) {
                case FETCH_OPCODE -> State.FETCH_ADDRESS;
                case FETCH_ADDRESS -> fetchAddress(State.READ_EFFECTIVE_ADDRESS_ADD_INDEX);
                case READ_EFFECTIVE_ADDRESS_ADD_INDEX -> readEffectiveAddressAddIndex();
                case READ_EFFECTIVE_ADDRESS -> readEffectiveAddress();
                case DATA_AVAILABLE -> executeReadOperation(operation);
                default -> throw new IllegalStateException();
            };
        }
    }

    private class AbsoluteMode implements AddressingMode {
        @Override
        public State clock(Runnable operation) {
            return switch (state) {
                case FETCH_OPCODE -> State.FETCH_ADDRESS;
                case FETCH_ADDRESS -> fetchAddress(State.FETCH_EFFECTIVE_ADDRESS_HIGH);
                case FETCH_EFFECTIVE_ADDRESS_HIGH -> fetchAddressHigh();
                case READ_EFFECTIVE_ADDRESS -> readEffectiveAddress();
                case DATA_AVAILABLE -> executeReadOperation(operation);
                default -> throw new IllegalStateException();
            };
        }
    }

    private class AbsoluteIndexedMode implements AddressingMode {
        private final ByteSupplier index;

        private AbsoluteIndexedMode(ByteSupplier index) {
            this.index = index;
        }

        @Override
        public State clock(Runnable operation) {
            return switch (state) {
                case FETCH_OPCODE -> State.FETCH_ADDRESS;
                case FETCH_ADDRESS -> fetchAddress(State.FETCH_EFFECTIVE_ADDRESS_HIGH_ADD_INDEX);
                case FETCH_EFFECTIVE_ADDRESS_HIGH_ADD_INDEX -> fetchAddressHighAddIndex(this.index.get());
                case READ_EFFECTIVE_ADDRESS -> readEffectiveAddress();
                case READ_EFFECTIVE_ADDRESS_FIX_HIGH -> readEffectiveAddressFixHigh();
                case DATA_AVAILABLE -> executeReadOperation(operation);
                default -> throw new IllegalStateException();
            };
        }
    }
}
