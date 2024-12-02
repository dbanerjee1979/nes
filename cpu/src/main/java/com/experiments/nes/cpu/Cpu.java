package com.experiments.nes.cpu;

import static com.experiments.nes.cpu.Cpu.OperationType.*;

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
        FETCH_OPCODE, FETCH_OPCODE_IN_SAME_CYCLE, FETCH_VALUE, FETCH_ADDRESS, READ_EFFECTIVE_ADDRESS, REREAD_EFFECTIVE_ADDRESS,
        READ_EFFECTIVE_ADDRESS_ADD_INDEX, FETCH_EFFECTIVE_ADDRESS_HIGH, FETCH_EFFECTIVE_ADDRESS_HIGH_ADD_INDEX,
        READ_EFFECTIVE_ADDRESS_FIX_HIGH, FETCH_POINTER, READ_POINTER_ADD_INDEX, FETCH_BOGUS_INSTRUCTION, STORE_RESULT, DATA_AVAILABLE
    }

    enum OperationType {
        Read, Write, ReadWrite
    }

    private final Memory memory;
    private byte a = 0;
    private byte x = 0;
    private byte y = 0;
    private short pc = RESET_VECTOR;
    private byte s = -3;
    private byte p = (byte) (Flag.InterruptDisabled.mask() | 0x20);
    private short address;
    private short pointer;
    private byte data;
    private State state = State.FETCH_OPCODE;
    private Operation operation;

    private final Operation[] operations = new Operation[255];
    {
        ImmediateMode immediateMode = new ImmediateMode();
        ZeroPageMode zeroPageMode = new ZeroPageMode();
        ZeroPageIndexedMode zeroPageXMode = new ZeroPageIndexedMode(this::x);
        ZeroPageIndexedMode zeroPageYMode = new ZeroPageIndexedMode(this::y);
        AbsoluteMode absoluteMode = new AbsoluteMode();
        AbsoluteIndexedMode absoluteXMode = new AbsoluteIndexedMode(this::x);
        AbsoluteIndexedMode absoluteYMode = new AbsoluteIndexedMode(this::y);
        IndexedIndirectMode indexedIndirectMode = new IndexedIndirectMode();
        IndirectIndexedMode indirectIndexedMode = new IndirectIndexedMode();
        AccumulatorMode accumulatorMode = new AccumulatorMode();

        operation(0x00, new BreakOperation());
        // AND
        operation(0x29, new StandardOperation(immediateMode, Read, this::and));
        operation(0x25, new StandardOperation(zeroPageMode, Read, this::and));
        operation(0x35, new StandardOperation(zeroPageXMode, Read, this::and));
        operation(0x2D, new StandardOperation(absoluteMode, Read, this::and));
        operation(0x3D, new StandardOperation(absoluteXMode, Read, this::and));
        operation(0x39, new StandardOperation(absoluteYMode, Read, this::and));
        operation(0x21, new StandardOperation(indexedIndirectMode, Read, this::and));
        operation(0x31, new StandardOperation(indirectIndexedMode, Read, this::and));
        // ASL
        operation(0x0A, new StandardOperation(accumulatorMode, Read, this::leftShift));
        operation(0x06, new StandardOperation(zeroPageMode, ReadWrite, this::leftShift));
        operation(0x16, new StandardOperation(zeroPageXMode, ReadWrite, this::leftShift));
        operation(0x0E, new StandardOperation(absoluteMode, ReadWrite, this::leftShift));
        operation(0x1E, new StandardOperation(absoluteXMode, ReadWrite, this::leftShift));
        // DEC
        operation(0xC6, new StandardOperation(zeroPageMode, ReadWrite, this::decrement));
        operation(0xD6, new StandardOperation(zeroPageXMode, ReadWrite, this::decrement));
        operation(0xCE, new StandardOperation(absoluteMode, ReadWrite, this::decrement));
        operation(0xDE, new StandardOperation(absoluteXMode, ReadWrite, this::decrement));
        // EOR
        operation(0x49, new StandardOperation(immediateMode, Read, this::xor));
        operation(0x45, new StandardOperation(zeroPageMode, Read, this::xor));
        operation(0x55, new StandardOperation(zeroPageXMode, Read, this::xor));
        operation(0x4D, new StandardOperation(absoluteMode, Read, this::xor));
        operation(0x5D, new StandardOperation(absoluteXMode, Read, this::xor));
        operation(0x59, new StandardOperation(absoluteYMode, Read, this::xor));
        operation(0x41, new StandardOperation(indexedIndirectMode, Read, this::xor));
        operation(0x51, new StandardOperation(indirectIndexedMode, Read, this::xor));
        // INC
        operation(0xE6, new StandardOperation(zeroPageMode, ReadWrite, this::increment));
        operation(0xF6, new StandardOperation(zeroPageXMode, ReadWrite, this::increment));
        operation(0xEE, new StandardOperation(absoluteMode, ReadWrite, this::increment));
        operation(0xFE, new StandardOperation(absoluteXMode, ReadWrite, this::increment));
        // LDA
        operation(0xA9, new StandardOperation(immediateMode, Read, this::loadA));
        operation(0xA5, new StandardOperation(zeroPageMode, Read, this::loadA));
        operation(0xB5, new StandardOperation(zeroPageXMode, Read, this::loadA));
        operation(0xAD, new StandardOperation(absoluteMode, Read, this::loadA));
        operation(0xBD, new StandardOperation(absoluteXMode, Read, this::loadA));
        operation(0xB9, new StandardOperation(absoluteYMode, Read, this::loadA));
        operation(0xA1, new StandardOperation(indexedIndirectMode, Read, this::loadA));
        operation(0xB1, new StandardOperation(indirectIndexedMode, Read, this::loadA));
        // LDX
        operation(0xA2, new StandardOperation(immediateMode, Read, this::loadX));
        operation(0xA6, new StandardOperation(zeroPageMode, Read, this::loadX));
        operation(0xB6, new StandardOperation(zeroPageYMode, Read, this::loadX));
        operation(0xAE, new StandardOperation(absoluteMode, Read, this::loadX));
        operation(0xBE, new StandardOperation(absoluteYMode, Read, this::loadX));
        // LDY
        operation(0xA0, new StandardOperation(immediateMode, Read, this::loadY));
        operation(0xA4, new StandardOperation(zeroPageMode, Read, this::loadY));
        operation(0xB4, new StandardOperation(zeroPageXMode, Read, this::loadY));
        operation(0xAC, new StandardOperation(absoluteMode, Read, this::loadY));
        operation(0xBC, new StandardOperation(absoluteXMode, Read, this::loadY));
        // LSR
        operation(0x4A, new StandardOperation(accumulatorMode, Read, this::rightShift));
        operation(0x46, new StandardOperation(zeroPageMode, ReadWrite, this::rightShift));
        operation(0x56, new StandardOperation(zeroPageXMode, ReadWrite, this::rightShift));
        operation(0x4E, new StandardOperation(absoluteMode, ReadWrite, this::rightShift));
        operation(0x5E, new StandardOperation(absoluteXMode, ReadWrite, this::rightShift));
        // OR
        operation(0x09, new StandardOperation(immediateMode, Read, this::or));
        operation(0x05, new StandardOperation(zeroPageMode, Read, this::or));
        operation(0x15, new StandardOperation(zeroPageXMode, Read, this::or));
        operation(0x0D, new StandardOperation(absoluteMode, Read, this::or));
        operation(0x1D, new StandardOperation(absoluteXMode, Read, this::or));
        operation(0x19, new StandardOperation(absoluteYMode, Read, this::or));
        operation(0x01, new StandardOperation(indexedIndirectMode, Read, this::or));
        operation(0x11, new StandardOperation(indirectIndexedMode, Read, this::or));
        // ROL
        operation(0x2A, new StandardOperation(accumulatorMode, Read, this::rotateLeft));
        operation(0x26, new StandardOperation(zeroPageMode, ReadWrite, this::rotateLeft));
        operation(0x36, new StandardOperation(zeroPageXMode, ReadWrite, this::rotateLeft));
        operation(0x2E, new StandardOperation(absoluteMode, ReadWrite, this::rotateLeft));
        operation(0x3E, new StandardOperation(absoluteXMode, ReadWrite, this::rotateLeft));
        // ROR
        operation(0x6A, new StandardOperation(accumulatorMode, Read, this::rotateRight));
        operation(0x66, new StandardOperation(zeroPageMode, ReadWrite, this::rotateRight));
        operation(0x76, new StandardOperation(zeroPageXMode, ReadWrite, this::rotateRight));
        operation(0x6E, new StandardOperation(absoluteMode, ReadWrite, this::rotateRight));
        operation(0x7E, new StandardOperation(absoluteXMode, ReadWrite, this::rotateRight));
        // STA
        operation(0x85, new StandardOperation(zeroPageMode, Write, this::storeA));
        operation(0x95, new StandardOperation(zeroPageXMode, Write, this::storeA));
        operation(0x8D, new StandardOperation(absoluteMode, Write, this::storeA));
        operation(0x9D, new StandardOperation(absoluteXMode, Write, this::storeA));
        operation(0x99, new StandardOperation(absoluteYMode, Write, this::storeA));
        operation(0x81, new StandardOperation(indexedIndirectMode, Write, this::storeA));
        operation(0x91, new StandardOperation(indirectIndexedMode, Write, this::storeA));
        // STX
        operation(0x86, new StandardOperation(zeroPageMode, Write, this::storeX));
        operation(0x96, new StandardOperation(zeroPageYMode, Write, this::storeX));
        operation(0x8E, new StandardOperation(absoluteMode, Write, this::storeX));
        // STY
        operation(0x84, new StandardOperation(zeroPageMode, Write, this::storeY));
        operation(0x94, new StandardOperation(zeroPageXMode, Write, this::storeY));
        operation(0x8C, new StandardOperation(absoluteMode, Write, this::storeY));
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

    public byte p() {
        return this.p;
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

    private short nextPC() {
        return this.pc++;
    }

    private void fetchOpcode() {
        int opcode = this.memory.load(this.pc++) & 0x00FF;
        this.operation = this.operations[opcode];
    }

    private State fetchImmediate() {
        this.data = this.memory.load(this.pc++);
        return State.DATA_AVAILABLE;
    }

    private State fetchPointer(State nextState) {
        this.pointer = (short) (this.memory.load(this.pc++) & 0x00FF);
        return nextState;
    }

    private short nextPointer() {
        short currentPointer = this.pointer;
        this.pointer = (short) ((this.pointer & 0xFF00) | ((this.pointer + 1) & 0x00FF));
        return currentPointer;
    }

    private State readPointerAddIndex() {
        this.memory.load(this.pointer);
        this.pointer = (short) ((this.pointer & 0xFF00) | ((this.pointer + this.x) & 0x00FF));
        return State.FETCH_ADDRESS;
    }

    private State fetchAddress(State nextState, ShortSupplier pointer) {
        this.address = (short) (this.memory.load(pointer.get()) & 0x00FF);
        return nextState;
    }

    private State fetchAddressHigh(State nextState, ShortSupplier pointer) {
        this.address = (short) (((this.memory.load(pointer.get()) & 0x00FF) << 8) | (this.address & 0x00FF));
        return nextState;
    }

    private State fetchAddressHighAddIndex(ShortSupplier nextPointer, byte index) {
        int addressLow = this.address + index;
        this.address = (short) (((this.memory.load(nextPointer.get()) & 0x00FF) << 8) | (addressLow & 0x00FF));
        return (addressLow & 0xFF00) == 0 ? State.READ_EFFECTIVE_ADDRESS : State.READ_EFFECTIVE_ADDRESS_FIX_HIGH;
    }

    private State fetchBogusInstruction() {
        this.memory.load(this.pc);
        this.data = this.a;
        return State.DATA_AVAILABLE;
    }

    private State readEffectiveAddressFixHigh(State nextState) {
        this.data = this.memory.load(this.address);
        this.address += 0x0100;
        return nextState;
    }

    private State readEffectiveAddress(State nextState) {
        this.data = this.memory.load(this.address);
        return nextState;
    }

    private State readEffectiveAddressAddIndex(State nextState, byte index) {
        this.data = this.memory.load(this.address);
        this.address = (short) ((this.address & 0xFF00) | ((this.address + index) & 0x00FF));
        return nextState;
    }

    private State executeOperation(Runnable operation, OperationType operationType) {
        if (operationType == ReadWrite) {
            this.memory.store(this.address, this.data);
        }
        operation.run();
        return switch(operationType) {
            case Read -> State.FETCH_OPCODE_IN_SAME_CYCLE;
            case Write -> State.FETCH_OPCODE;
            case ReadWrite -> State.STORE_RESULT;
        };
    }

    private State executeOperationStoreAccumulator(Runnable operation, OperationType operationType) {
        State nextState = executeOperation(operation, operationType);
        this.a = this.data;
        return nextState;
    }

    private State storeResult() {
        this.memory.store(this.address, this.data);
        return State.FETCH_OPCODE;
    }

    private void loadA() {
        load(this::a);
    }

    private void loadX() {
        load(this::x);
    }

    private void loadY() {
        load(this::y);
    }

    private void load(ByteConsumer register) {
        register.accept(this.data);
        this.p = (byte) (this.p & ~(Flag.Zero.mask() | Flag.Negative.mask()));
        if (this.data == 0) {
            this.p = Flag.Zero.set(this.p);
        }
        if ((this.data & 0x80) != 0) {
            this.p = Flag.Negative.set(this.p);
        }
    }

    private void storeA() {
        this.memory.store(this.address, this.a);
    }

    private void storeX() {
        this.memory.store(this.address, this.x);
    }

    private void storeY() {
        this.memory.store(this.address, this.y);
    }

    private void leftShift() {
        int result = this.data << 1;
        this.p = (byte) (this.p & ~(Flag.Carry.mask() | Flag.Zero.mask() | Flag.Negative.mask()));
        if ((result & 0xFF00) != 0) {
            this.p = Flag.Carry.set(this.p);
        }
        if ((result & 0x0080) != 0) {
            this.p = Flag.Negative.set(this.p);
        }
        if ((result & 0x00FF) == 0) {
            this.p = Flag.Zero.set(this.p);
        }
        this.data = (byte) result;
    }

    private void rightShift() {
        int bit0 = this.data & 0x01;
        // Undo sign extend by clearing bit 7 (sign bit)
        int result = (this.data >> 1) & 0x007F;
        this.p = (byte) (this.p & ~(Flag.Carry.mask() | Flag.Zero.mask()));
        if (bit0 != 0) {
            this.p = Flag.Carry.set(this.p);
        }
        if ((result & 0x00FF) == 0) {
            this.p = Flag.Zero.set(this.p);
        }
        this.data = (byte) result;
    }

    private void rotateLeft() {
        int bit7 = this.data & 0x80;
        int result = (this.data << 1) | (this.p & Flag.Carry.mask());
        this.p = (byte) (this.p & ~(Flag.Carry.mask() | Flag.Negative.mask() | Flag.Zero.mask()));
        if (bit7 != 0) {
            this.p = Flag.Carry.set(this.p);
        }
        if ((result & 0x0080) != 0) {
            this.p = Flag.Negative.set(this.p);
        }
        if ((result & 0x00FF) == 0) {
            this.p = Flag.Zero.set(this.p);
        }
        this.data = (byte) result;
    }

    private void rotateRight() {
        int bit0 = this.data & 0x01;
        int result = (this.data >> 1) | ((this.p & Flag.Carry.mask()) << 7);
        this.p = (byte) (this.p & ~(Flag.Carry.mask() | Flag.Negative.mask() | Flag.Zero.mask()));
        if (bit0 != 0) {
            this.p = Flag.Carry.set(this.p);
        }
        if ((result & 0x0080) != 0) {
            this.p = Flag.Negative.set(this.p);
        }
        if ((result & 0x00FF) == 0) {
            this.p = Flag.Zero.set(this.p);
        }
        this.data = (byte) result;
    }

    private void decrement() {
        this.data--;
        this.p = (byte) (this.p & ~(Flag.Negative.mask() | Flag.Zero.mask()));
        if ((this.data & 0x00FF) == 0) {
            this.p = Flag.Zero.set(this.p);
        }
        if ((this.data & 0x0080) != 0) {
            this.p = Flag.Negative.set(this.p);
        }
    }

    private void increment() {
        this.data++;
        this.p = (byte) (this.p & ~(Flag.Negative.mask() | Flag.Zero.mask()));
        if ((this.data & 0x00FF) == 0) {
            this.p = Flag.Zero.set(this.p);
        }
        if ((this.data & 0x0080) != 0) {
            this.p = Flag.Negative.set(this.p);
        }
    }

    private void xor() {
        this.a = (byte) (this.a ^ this.data);
        this.p = (byte) (this.p & ~(Flag.Negative.mask() | Flag.Zero.mask()));
        if ((this.a & 0x00FF) == 0) {
            this.p = Flag.Zero.set(this.p);
        }
        if ((this.a & 0x0080) != 0) {
            this.p = Flag.Negative.set(this.p);
        }
    }

    private void and() {
        this.a = (byte) (this.a & this.data);
        this.p = (byte) (this.p & ~(Flag.Negative.mask() | Flag.Zero.mask()));
        if ((this.a & 0x00FF) == 0) {
            this.p = Flag.Zero.set(this.p);
        }
        if ((this.a & 0x0080) != 0) {
            this.p = Flag.Negative.set(this.p);
        }
    }

    private void or() {
        this.a = (byte) (this.a | this.data);
        this.p = (byte) (this.p & ~(Flag.Negative.mask() | Flag.Zero.mask()));
        if ((this.a & 0x00FF) == 0) {
            this.p = Flag.Zero.set(this.p);
        }
        if ((this.a & 0x0080) != 0) {
            this.p = Flag.Negative.set(this.p);
        }
    }

    @FunctionalInterface
    private interface ByteSupplier {
        byte get();
    }

    @FunctionalInterface
    private interface ByteConsumer {
        void accept(byte value);
    }

    @FunctionalInterface
    private interface ShortSupplier {
        short get();
    }

    private interface Operation {
        State clock();
    }

    private static class StandardOperation implements Operation {
        private final AddressingMode addressingMode;
        private final OperationType operationType;
        private final Runnable operation;

        public StandardOperation(AddressingMode addressingMode, OperationType operationType, Runnable operation) {
            this.addressingMode = addressingMode;
            this.operationType = operationType;
            this.operation = operation;
        }

        @Override
        public State clock() {
            return this.addressingMode.clock(operation, operationType);
        }
    }

    private static class BreakOperation implements Operation {
        @Override
        public State clock() {
            return State.FETCH_VALUE;
        }
    }

    private interface AddressingMode {
        State clock(Runnable operation, OperationType operationType);
    }

    private class ImmediateMode implements AddressingMode {
        @Override
        public State clock(Runnable operation, OperationType operationType) {
            return switch (state) {
                case FETCH_OPCODE -> State.FETCH_VALUE;
                case FETCH_VALUE -> fetchImmediate();
                case DATA_AVAILABLE -> executeOperation(operation, operationType);
                default -> throw new IllegalStateException();
            };
        }
    }

    private class ZeroPageMode implements AddressingMode {
        @Override
        public State clock(Runnable operation, OperationType operationType) {
            return switch (state) {
                case FETCH_OPCODE -> State.FETCH_ADDRESS;
                case FETCH_ADDRESS -> fetchAddress(operationType == Write ?
                                // For write instructions, the data is already available in a register
                                State.DATA_AVAILABLE :
                                // For read instructions, the data becomes available in the next cycle after fetching from memory
                                State.READ_EFFECTIVE_ADDRESS,
                        Cpu.this::nextPC);
                case READ_EFFECTIVE_ADDRESS -> readEffectiveAddress(State.DATA_AVAILABLE);
                case DATA_AVAILABLE -> executeOperation(operation, operationType);
                case STORE_RESULT -> storeResult();
                default -> throw new IllegalStateException();
            };
        }
    }

    private class ZeroPageIndexedMode implements AddressingMode {
        private final ByteSupplier index;

        private ZeroPageIndexedMode(ByteSupplier index) {
            this.index = index;
        }

        @Override
        public State clock(Runnable operation, OperationType operationType) {
            return switch (state) {
                case FETCH_OPCODE -> State.FETCH_ADDRESS;
                case FETCH_ADDRESS -> fetchAddress(State.READ_EFFECTIVE_ADDRESS_ADD_INDEX, Cpu.this::nextPC);
                case READ_EFFECTIVE_ADDRESS_ADD_INDEX -> readEffectiveAddressAddIndex(operationType == Write ?
                        // For write instructions, the data is already available in a register
                        State.DATA_AVAILABLE :
                        // For read instructions, the data becomes available in the next cycle after fetching from memory
                        State.READ_EFFECTIVE_ADDRESS,
                        index.get());
                case READ_EFFECTIVE_ADDRESS -> readEffectiveAddress(State.DATA_AVAILABLE);
                case DATA_AVAILABLE -> executeOperation(operation, operationType);
                case STORE_RESULT -> storeResult();
                default -> throw new IllegalStateException();
            };
        }
    }

    private class AbsoluteMode implements AddressingMode {
        @Override
        public State clock(Runnable operation, OperationType operationType) {
            return switch (state) {
                case FETCH_OPCODE -> State.FETCH_ADDRESS;
                case FETCH_ADDRESS -> fetchAddress(State.FETCH_EFFECTIVE_ADDRESS_HIGH, Cpu.this::nextPC);
                case FETCH_EFFECTIVE_ADDRESS_HIGH -> fetchAddressHigh(operationType == Write ?
                        // For write instructions, the data is already available in a register
                        State.DATA_AVAILABLE :
                        // For read instructions, the data becomes available in the next cycle after fetching from memory
                        State.READ_EFFECTIVE_ADDRESS,
                        Cpu.this::nextPC);
                case READ_EFFECTIVE_ADDRESS -> readEffectiveAddress(State.DATA_AVAILABLE);
                case DATA_AVAILABLE -> executeOperation(operation, operationType);
                case STORE_RESULT -> storeResult();
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
        public State clock(Runnable operation, OperationType operationType) {
            return switch (state) {
                case FETCH_OPCODE -> State.FETCH_ADDRESS;
                case FETCH_ADDRESS -> fetchAddress(State.FETCH_EFFECTIVE_ADDRESS_HIGH_ADD_INDEX, Cpu.this::nextPC);
                case FETCH_EFFECTIVE_ADDRESS_HIGH_ADD_INDEX -> fetchAddressHighAddIndex(Cpu.this::nextPC, this.index.get());
                case READ_EFFECTIVE_ADDRESS -> readEffectiveAddress(operationType == ReadWrite ?
                        // For R/W instructions, the CPU loads the effective address twice, since the first time
                        // might have crossed the page boundary
                        State.REREAD_EFFECTIVE_ADDRESS :
                        // Otherwise the data is available
                        State.DATA_AVAILABLE);
                case REREAD_EFFECTIVE_ADDRESS -> readEffectiveAddress(State.DATA_AVAILABLE);
                case READ_EFFECTIVE_ADDRESS_FIX_HIGH -> readEffectiveAddressFixHigh(switch (operationType) {
                        // For write instructions, the data is already available in a register
                        case Write -> State.DATA_AVAILABLE;
                        // For read instructions, the data becomes available in the next cycle after fetching from memory
                        case Read -> State.READ_EFFECTIVE_ADDRESS;
                        // For R/W instructions, the effective address has to be loaded again with the fixed address
                        case ReadWrite -> State.REREAD_EFFECTIVE_ADDRESS;
                    });
                case DATA_AVAILABLE -> executeOperation(operation, operationType);
                case STORE_RESULT -> storeResult();
                default -> throw new IllegalStateException();
            };
        }
    }

    private class IndexedIndirectMode implements AddressingMode {
        @Override
        public State clock(Runnable operation, OperationType operationType) {
            return switch (state) {
                case FETCH_OPCODE -> State.FETCH_POINTER;
                case FETCH_POINTER -> fetchPointer(State.READ_POINTER_ADD_INDEX);
                case READ_POINTER_ADD_INDEX -> readPointerAddIndex();
                case FETCH_ADDRESS -> fetchAddress(State.FETCH_EFFECTIVE_ADDRESS_HIGH, Cpu.this::nextPointer);
                case FETCH_EFFECTIVE_ADDRESS_HIGH -> fetchAddressHigh(operationType == Read ?
                                // For read instructions, the data becomes available in the next cycle after fetching from memory
                                State.READ_EFFECTIVE_ADDRESS :
                                // For write instructions, the data is already available in a register
                                State.DATA_AVAILABLE,
                        Cpu.this::nextPointer);
                case READ_EFFECTIVE_ADDRESS -> readEffectiveAddress(State.DATA_AVAILABLE);
                case DATA_AVAILABLE -> executeOperation(operation, operationType);
                default -> throw new IllegalStateException();
            };
        }
    }

    private class IndirectIndexedMode implements AddressingMode {
        @Override
        public State clock(Runnable operation, OperationType operationType) {
            return switch (state) {
                case FETCH_OPCODE -> State.FETCH_POINTER;
                case FETCH_POINTER -> fetchPointer(State.FETCH_ADDRESS);
                case FETCH_ADDRESS -> fetchAddress(State.FETCH_EFFECTIVE_ADDRESS_HIGH_ADD_INDEX, Cpu.this::nextPointer);
                case FETCH_EFFECTIVE_ADDRESS_HIGH_ADD_INDEX -> fetchAddressHighAddIndex(Cpu.this::nextPointer, y);
                case READ_EFFECTIVE_ADDRESS -> readEffectiveAddress(State.DATA_AVAILABLE);
                case READ_EFFECTIVE_ADDRESS_FIX_HIGH -> readEffectiveAddressFixHigh(operationType == Read ?
                        // For read instructions, the data becomes available in the next cycle after fetching from memory
                        State.READ_EFFECTIVE_ADDRESS :
                        // For write instructions, the data is already available in a register
                        State.DATA_AVAILABLE);
                case DATA_AVAILABLE -> executeOperation(operation, operationType);
                default -> throw new IllegalStateException();
            };
        }
    }

    private class AccumulatorMode implements AddressingMode {
        @Override
        public State clock(Runnable operation, OperationType operationType) {
            return switch (state) {
                case FETCH_OPCODE -> State.FETCH_BOGUS_INSTRUCTION;
                // CPU requires minimum 2 cycles per operation, so it does a bogus fetch of PC
                case FETCH_BOGUS_INSTRUCTION -> fetchBogusInstruction();
                case DATA_AVAILABLE -> executeOperationStoreAccumulator(operation, operationType);
                default -> throw new IllegalStateException();
            };
        }
    }
}
