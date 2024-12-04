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

        public byte clear(byte p) {
            return (byte) (p & ~mask);
        }

        public byte set(byte p, boolean isSet) {
            p = clear(p);
            return isSet ? set(p) : p;
        }
    }

    enum State {
        FETCH_OPCODE, FETCH_OPCODE_IN_SAME_CYCLE, FETCH_VALUE, FETCH_ADDRESS, READ_EFFECTIVE_ADDRESS, REREAD_EFFECTIVE_ADDRESS,
        READ_EFFECTIVE_ADDRESS_ADD_INDEX, FETCH_EFFECTIVE_ADDRESS_HIGH, FETCH_EFFECTIVE_ADDRESS_HIGH_ADD_INDEX,
        READ_EFFECTIVE_ADDRESS_FIX_HIGH, FETCH_POINTER, FETCH_POINTER_HIGH, READ_POINTER_ADD_INDEX, FETCH_BOGUS_INSTRUCTION,
        STORE_RESULT, UPDATE_PC, DATA_AVAILABLE
    }

    enum OperationType {
        Read, Write, ReadWrite, Jump
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
        ImpliedMode impliedMode = new ImpliedMode();
        AbsoluteIndirectMode absoluteIndirectMode = new AbsoluteIndirectMode();

        operation(0x00, new BreakOperation());
        // ADC
        operation(0x69, new StandardOperation(immediateMode, Read, this::addWithCarry));
        operation(0x65, new StandardOperation(zeroPageMode, Read, this::addWithCarry));
        operation(0x75, new StandardOperation(zeroPageXMode, Read, this::addWithCarry));
        operation(0x6D, new StandardOperation(absoluteMode, Read, this::addWithCarry));
        operation(0x7D, new StandardOperation(absoluteXMode, Read, this::addWithCarry));
        operation(0x79, new StandardOperation(absoluteYMode, Read, this::addWithCarry));
        operation(0x61, new StandardOperation(indexedIndirectMode, Read, this::addWithCarry));
        operation(0x71, new StandardOperation(indirectIndexedMode, Read, this::addWithCarry));
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
        // BIT
        operation(0x24, new StandardOperation(zeroPageMode, Read, this::bit));
        operation(0x2C, new StandardOperation(absoluteXMode, Read, this::bit));
        // CLC
        operation(0x18, new StandardOperation(impliedMode, Read, this::clearCarry));
        // CLD
        operation(0xD8, new StandardOperation(impliedMode, Read, this::clearDecimal));
        // CLI
        operation(0x58, new StandardOperation(impliedMode, Read, this::clearInterrupt));
        // CLV
        operation(0xB8, new StandardOperation(impliedMode, Read, this::clearOverflow));
        // CMP
        operation(0xC9, new StandardOperation(immediateMode, Read, this::compareA));
        operation(0xC5, new StandardOperation(zeroPageMode, Read, this::compareA));
        operation(0xD5, new StandardOperation(zeroPageXMode, Read, this::compareA));
        operation(0xCD, new StandardOperation(absoluteMode, Read, this::compareA));
        operation(0xDD, new StandardOperation(absoluteXMode, Read, this::compareA));
        operation(0xD9, new StandardOperation(absoluteYMode, Read, this::compareA));
        operation(0xC1, new StandardOperation(indexedIndirectMode, Read, this::compareA));
        operation(0xD1, new StandardOperation(indirectIndexedMode, Read, this::compareA));
        // CPX
        operation(0xE0, new StandardOperation(immediateMode, Read, this::compareX));
        operation(0xE4, new StandardOperation(zeroPageMode, Read, this::compareX));
        operation(0xEC, new StandardOperation(absoluteMode, Read, this::compareX));
        // CPY
        operation(0xC0, new StandardOperation(immediateMode, Read, this::compareY));
        operation(0xC4, new StandardOperation(zeroPageMode, Read, this::compareY));
        operation(0xCC, new StandardOperation(absoluteMode, Read, this::compareY));
        // DEC
        operation(0xC6, new StandardOperation(zeroPageMode, ReadWrite, this::decrement));
        operation(0xD6, new StandardOperation(zeroPageXMode, ReadWrite, this::decrement));
        operation(0xCE, new StandardOperation(absoluteMode, ReadWrite, this::decrement));
        operation(0xDE, new StandardOperation(absoluteXMode, ReadWrite, this::decrement));
        // DEX
        operation(0xCA, new StandardOperation(impliedMode, Read, this::decrementX));
        // DEY
        operation(0x88, new StandardOperation(impliedMode, Read, this::decrementY));
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
        // INX
        operation(0xE8, new StandardOperation(impliedMode, Read, this::incrementX));
        // INY
        operation(0xC8, new StandardOperation(impliedMode, Read, this::incrementY));
        // JMP
        operation(0x4C, new StandardOperation(absoluteMode, Jump));
        operation(0x6C, new StandardOperation(absoluteIndirectMode, Jump));
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
        // NOP
        operation(0xEA, new StandardOperation(impliedMode, Read));
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
        // SBC
        operation(0xE9, new StandardOperation(immediateMode, Read, this::subtractWithCarry));
        operation(0xE5, new StandardOperation(zeroPageMode, Read, this::subtractWithCarry));
        operation(0xF5, new StandardOperation(zeroPageXMode, Read, this::subtractWithCarry));
        operation(0xED, new StandardOperation(absoluteMode, Read, this::subtractWithCarry));
        operation(0xFD, new StandardOperation(absoluteXMode, Read, this::subtractWithCarry));
        operation(0xF9, new StandardOperation(absoluteYMode, Read, this::subtractWithCarry));
        operation(0xE1, new StandardOperation(indexedIndirectMode, Read, this::subtractWithCarry));
        operation(0xF1, new StandardOperation(indirectIndexedMode, Read, this::subtractWithCarry));
        // SEC
        operation(0x38, new StandardOperation(impliedMode, Read, this::setCarry));
        // SED
        operation(0xF8, new StandardOperation(impliedMode, Read, this::setDecimal));
        // SEI
        operation(0x78, new StandardOperation(impliedMode, Read, this::setInterrupt));
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
        // TAX
        operation(0xAA, new StandardOperation(impliedMode, Read, this::transferAtoX));
        // TAY
        operation(0xA8, new StandardOperation(impliedMode, Read, this::transferAtoY));
        // TSX
        operation(0xBA, new StandardOperation(impliedMode, Read, this::transferStoX));
        // TXA
        operation(0x8A, new StandardOperation(impliedMode, Read, this::transferXtoA));
        // TXS
        operation(0x9A, new StandardOperation(impliedMode, Read, this::transferXtoS));
        // TYA
        operation(0x98, new StandardOperation(impliedMode, Read, this::transferYtoA));
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

    private State fetchPointerHigh() {
        this.pointer = (short) (((this.memory.load(this.pc++) & 0x00FF) << 8) | (this.pointer & 0x00FF));
        return State.FETCH_ADDRESS;
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
            case Write, Jump -> State.FETCH_OPCODE;
            case ReadWrite -> State.STORE_RESULT;
        };
    }

    private State executeOperationStoreAccumulator(Runnable operation, OperationType operationType) {
        State nextState = executeOperation(operation, operationType);
        this.a = this.data;
        return nextState;
    }

    private State updatePC() {
        this.pc = this.address;
        return State.FETCH_OPCODE_IN_SAME_CYCLE;
    }

    private State storeResult() {
        this.memory.store(this.address, this.data);
        return State.FETCH_OPCODE;
    }

    private void setZeroNegativeFlags(byte value) {
        this.p = (byte) (this.p & ~(Flag.Zero.mask() | Flag.Negative.mask()));
        this.p = Flag.Zero.set(this.p, value == 0);
        this.p = Flag.Negative.set(this.p, (value & 0x80) != 0);
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
        setZeroNegativeFlags(this.data);
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
        this.data = (byte) result;
        setZeroNegativeFlags(this.data);
        this.p = Flag.Carry.set(this.p, (result & 0xFF00) != 0);
    }

    private void rightShift() {
        int bit0 = this.data & 0x01;
        // Undo sign extend by clearing bit 7 (sign bit)
        int result = (this.data >> 1) & 0x007F;
        this.data = (byte) result;
        setZeroNegativeFlags(this.data);
        this.p = Flag.Carry.set(this.p, bit0 != 0);
    }

    private void rotateLeft() {
        int bit7 = this.data & 0x80;
        int result = (this.data << 1) | (this.p & Flag.Carry.mask());
        this.data = (byte) result;
        setZeroNegativeFlags(this.data);
        this.p = Flag.Carry.set(this.p, bit7 != 0);
    }

    private void rotateRight() {
        int bit0 = this.data & 0x01;
        int result = (this.data >> 1) | ((this.p & Flag.Carry.mask()) << 7);
        this.data = (byte) result;
        setZeroNegativeFlags(this.data);
        this.p = Flag.Carry.set(this.p, bit0 != 0);
    }

    private void decrement() {
        this.data--;
        setZeroNegativeFlags(this.data);
    }

    private void decrementX() {
        this.x--;
        setZeroNegativeFlags(this.x);
    }

    private void decrementY() {
        this.y--;
        setZeroNegativeFlags(this.y);
    }

    private void increment() {
        this.data++;
        setZeroNegativeFlags(this.data);
    }

    private void incrementX() {
        this.x++;
        setZeroNegativeFlags(this.x);
    }

    private void incrementY() {
        this.y++;
        setZeroNegativeFlags(this.y);
    }

    private void xor() {
        this.a = (byte) (this.a ^ this.data);
        setZeroNegativeFlags(this.a);
    }

    private void and() {
        this.a = (byte) (this.a & this.data);
        setZeroNegativeFlags(this.a);
    }

    private void or() {
        this.a = (byte) (this.a | this.data);
        setZeroNegativeFlags(this.a);
    }

    private void addWithCarry() {
        int result = (this.a & 0x00FF) + (this.data & 0x00FF) + (this.p & Flag.Carry.mask());
        setZeroNegativeFlags((byte) result);
        this.p = Flag.Carry.set(this.p, (result & 0x0100) != 0);
        this.p = Flag.Overflow.set(this.p, (
                (result ^ this.a) &      // Is result sign bit different from A?
                (result ^ this.data) &   // Is result sign bit different from memory?
                0x80) != 0);             // If both, the result exceeds the signed range [-128, 127]
        this.a = (byte) result;
    }

    private void subtractWithCarry() {
        this.data = (byte) ~this.data;
        addWithCarry();
    }

    private void compare(byte register) {
        int result = register - this.data;
        this.p = Flag.Carry.set(this.p, result >= 0);
        this.p = Flag.Zero.set(this.p, result == 0);
        this.p = Flag.Negative.set(this.p, result < 0);
    }

    private void compareA() {
        compare(this.a);
    }

    private void compareX() {
        compare(this.x);
    }

    private void compareY() {
        compare(this.y);
    }

    private void bit() {
        int result = this.a & this.data;
        this.p = Flag.Zero.set(this.p, result == 0);
        int bitMask = Flag.Negative.mask() | Flag.Overflow.mask();
        this.p = (byte) ((byte) (this.p & ~bitMask) | (this.data & bitMask));
    }

    private void clearCarry() {
        this.p = Flag.Carry.clear(this.p);
    }

    private void setCarry() {
        this.p = Flag.Carry.set(this.p);
    }

    private void clearDecimal() {
        this.p = Flag.Decimal.clear(this.p);
    }

    private void setDecimal() {
        this.p = Flag.Decimal.set(this.p);
    }

    private void clearInterrupt() {
        this.p = Flag.InterruptDisabled.clear(this.p);
    }

    private void setInterrupt() {
        this.p = Flag.InterruptDisabled.set(this.p);
    }

    private void clearOverflow() {
        this.p = Flag.Overflow.clear(this.p);
    }

    private void transferAtoX() {
        this.x = this.a;
        setZeroNegativeFlags(this.x);
    }

    private void transferAtoY() {
        this.y = this.a;
        setZeroNegativeFlags(this.y);
    }

    private void transferStoX() {
        this.x = this.s;
        setZeroNegativeFlags(this.x);
    }

    private void transferXtoA() {
        this.a = this.x;
        setZeroNegativeFlags(this.a);
    }

    private void transferXtoS() {
        this.s = this.x;
        setZeroNegativeFlags(this.s);
    }

    private void transferYtoA() {
        this.a = this.y;
        setZeroNegativeFlags(this.a);
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

        public StandardOperation(AddressingMode addressingMode, OperationType operationType) {
            this(addressingMode, operationType, () -> {});
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
                case FETCH_EFFECTIVE_ADDRESS_HIGH -> fetchAddressHigh(switch (operationType) {
                    // For write instructions, the data is already available in a register
                    case Write -> State.DATA_AVAILABLE;
                    // For jump instructions, update the PC and fetch the next instruction
                    case Jump -> State.UPDATE_PC;
                    // For read instructions, the data becomes available in the next cycle after fetching from memory
                    default -> State.READ_EFFECTIVE_ADDRESS;
                }, Cpu.this::nextPC);
                case READ_EFFECTIVE_ADDRESS -> readEffectiveAddress(State.DATA_AVAILABLE);
                case DATA_AVAILABLE -> executeOperation(operation, operationType);
                case STORE_RESULT -> storeResult();
                case UPDATE_PC -> updatePC();
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
                        default -> throw new IllegalStateException();
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

    private class AbsoluteIndirectMode implements AddressingMode {
        @Override
        public State clock(Runnable operation, OperationType operationType) {
            return switch (state) {
                case FETCH_OPCODE -> State.FETCH_POINTER;
                case FETCH_POINTER -> fetchPointer(State.FETCH_POINTER_HIGH);
                case FETCH_POINTER_HIGH -> fetchPointerHigh();
                case FETCH_ADDRESS -> fetchAddress(State.FETCH_EFFECTIVE_ADDRESS_HIGH, Cpu.this::nextPointer);
                case FETCH_EFFECTIVE_ADDRESS_HIGH -> fetchAddressHigh(State.UPDATE_PC, Cpu.this::nextPointer);
                case UPDATE_PC -> updatePC();
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

    private class ImpliedMode implements AddressingMode {
        @Override
        public State clock(Runnable operation, OperationType operationType) {
            return switch (state) {
                case FETCH_OPCODE -> State.FETCH_BOGUS_INSTRUCTION;
                // CPU requires minimum 2 cycles per operation, so it does a bogus fetch of PC
                case FETCH_BOGUS_INSTRUCTION -> fetchBogusInstruction();
                case DATA_AVAILABLE -> executeOperation(operation, operationType);
                default -> throw new IllegalStateException();
            };
        }
    }
}
