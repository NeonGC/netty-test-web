package ru.neongc.web.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import io.netty.util.ByteProcessor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

public class PacketBuffer extends ByteBuf {

    private final ByteBuf buf;

    public PacketBuffer(ByteBuf wrapped) {
        this.buf = wrapped;
    }

    public static int getVarIntSize(int input) {
        for (int i = 1;i < 5;++i) {
            if ((input & -1 << i * 7) == 0) {
                return i;
            }
        }
        return 5;
    }

    public PacketBuffer writeByteArray(byte[] array) {
        this.writeVarInt(array.length);
        this.writeBytes(array);
        return this;
    }

    public byte[] readByteArray() {
        return this.readByteArray(this.readableBytes());
    }

    public byte[] readByteArray(int maxLength) {
        int i = this.readVarInt();
        if (i > maxLength) {
            throw new DecoderException("ByteArray with size " + i + " is bigger than allowed " + maxLength);
        } else {
            byte[] abyte = new byte[i];
            this.readBytes(abyte);
            return abyte;
        }
    }

    public PacketBuffer writeVarIntArray(int[] array) {
        this.writeVarInt(array.length);
        for (int i : array) {
            this.writeVarInt(i);
        }
        return this;
    }

    public int[] readVarIntArray() {
        return this.readVarIntArray(this.readableBytes());
    }

    public int[] readVarIntArray(int maxLength) {
        int i = this.readVarInt();
        if (i > maxLength) {
            throw new DecoderException("VarIntArray with size " + i + " is bigger than allowed " + maxLength);
        } else {
            int[] aint = new int[i];
            for (int j = 0;j < aint.length;++j) {
                aint[j] = this.readVarInt();
            }
            return aint;
        }
    }

    public PacketBuffer writeLongArray(long[] array) {
        this.writeVarInt(array.length);
        for (long i : array) {
            this.writeLong(i);
        }
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T extends Enum<T>> T readEnumValue(Class<T> enumClass) {
        return (T) ((Enum[]) enumClass.getEnumConstants())[this.readVarInt()];
    }

    public PacketBuffer writeEnumValue(Enum<?> value) {
        return this.writeVarInt(value.ordinal());
    }

    public int readVarInt() {
        int i = 0;
        int j = 0;
        byte b0;
        while (true) {
            b0 = this.readByte();
            i |= (b0 & 127) << j++ * 7;
            if (j > 5) {
                throw new RuntimeException("VarInt too big");
            }
            if ((b0 & 128) != 128) {
                return i;
            }
        }
    }

    public long readVarLong() {
        long i = 0L;
        int j = 0;
        byte b0;
        while (true) {
            b0 = this.readByte();
            i |= (long) (b0 & 127) << j++ * 7;
            if (j > 10) {
                throw new RuntimeException("VarLong too big");
            }
            if ((b0 & 128) != 128) {
                return i;
            }
        }
    }

    public PacketBuffer writeUniqueId(UUID uuid) {
        this.writeLong(uuid.getMostSignificantBits());
        this.writeLong(uuid.getLeastSignificantBits());
        return this;
    }

    public UUID readUniqueId() {
        return new UUID(this.readLong(), this.readLong());
    }

    public PacketBuffer writeVarInt(int input) {
        while ((input & -128) != 0) {
            this.writeByte(input & 127 | 128);
            input >>>= 7;
        }
        this.writeByte(input);
        return this;
    }

    public PacketBuffer writeVarLong(long value) {
        while ((value & -128L) != 0L) {
            this.writeByte((int) (value & 127L) | 128);
            value >>>= 7;
        }
        this.writeByte((int) value);
        return this;
    }

    public String readString(int maxLength) {
        int i = this.readVarInt();
        if (i > maxLength * 4) {
            throw new DecoderException("The received encoded string buffer length is longer than maximum allowed (" + i + " > " + maxLength * 4 + ")");
        } else if (i < 0) {
            throw new DecoderException("The received encoded string buffer length is less than zero! Weird string!");
        } else {
            String s = this.toString(this.readerIndex(), i, StandardCharsets.UTF_8);
            this.readerIndex(this.readerIndex() + i);
            if (s.length() > maxLength) {
                throw new DecoderException("The received string length is longer than maximum allowed (" + i + " > " + maxLength + ")");
            } else {
                return s;
            }
        }
    }

    public PacketBuffer writeString(String string) {
        byte[] abyte = string.getBytes(StandardCharsets.UTF_8);
        if (abyte.length > 32767) {
            throw new EncoderException("String too big (was " + abyte.length + " bytes encoded, max " + 32767 + ")");
        } else {
            this.writeVarInt(abyte.length);
            this.writeBytes(abyte);
            return this;
        }
    }

    public Date readTime() {
        return new Date(this.readLong());
    }

    public PacketBuffer writeTime(Date time) {
        this.writeLong(time.getTime());
        return this;
    }

    @Override
    public int capacity() {
        return this.buf.capacity();
    }

    @Override
    public ByteBuf capacity(int i) {
        return this.buf.capacity(i);
    }

    @Override
    public int maxCapacity() {
        return this.buf.maxCapacity();
    }

    @Override
    public ByteBufAllocator alloc() {
        return this.buf.alloc();
    }

    @Override
    public ByteOrder order() {
        return this.buf.order();
    }

    @Override
    public ByteBuf order(ByteOrder bo) {
        return this.buf.order(bo);
    }

    @Override
    public ByteBuf unwrap() {
        return this.buf.unwrap();
    }

    @Override
    public boolean isDirect() {
        return this.buf.isDirect();
    }

    @Override
    public boolean isReadOnly() {
        return this.buf.isReadOnly();
    }

    @Override
    public ByteBuf asReadOnly() {
        return this.buf.asReadOnly();
    }

    @Override
    public int readerIndex() {
        return this.buf.readerIndex();
    }

    @Override
    public ByteBuf readerIndex(int i) {
        return this.buf.readerIndex(i);
    }

    @Override
    public int writerIndex() {
        return this.buf.writerIndex();
    }

    @Override
    public ByteBuf writerIndex(int i) {
        return this.buf.writerIndex(i);
    }

    @Override
    public ByteBuf setIndex(int i, int i1) {
        return this.buf.setIndex(i, i1);
    }

    @Override
    public int readableBytes() {
        return this.buf.readableBytes();
    }

    @Override
    public int writableBytes() {
        return this.buf.writableBytes();
    }

    @Override
    public int maxWritableBytes() {
        return this.buf.maxWritableBytes();
    }

    @Override
    public boolean isReadable() {
        return this.buf.isReadable();
    }

    @Override
    public boolean isReadable(int i) {
        return this.buf.isReadable(i);
    }

    @Override
    public boolean isWritable() {
        return this.buf.isWritable();
    }

    @Override
    public boolean isWritable(int i) {
        return this.buf.isWritable(i);
    }

    @Override
    public ByteBuf clear() {
        return this.buf.clear();
    }

    @Override
    public ByteBuf markReaderIndex() {
        return this.buf.markReaderIndex();
    }

    @Override
    public ByteBuf resetReaderIndex() {
        return this.buf.resetReaderIndex();
    }

    @Override
    public ByteBuf markWriterIndex() {
        return this.buf.markWriterIndex();
    }

    @Override
    public ByteBuf resetWriterIndex() {
        return this.buf.resetWriterIndex();
    }

    @Override
    public ByteBuf discardReadBytes() {
        return this.buf.discardReadBytes();
    }

    @Override
    public ByteBuf discardSomeReadBytes() {
        return this.buf.discardSomeReadBytes();
    }

    @Override
    public ByteBuf ensureWritable(int i) {
        return this.buf.ensureWritable(i);
    }

    @Override
    public int ensureWritable(int i, boolean bln) {
        return this.buf.ensureWritable(i, bln);
    }

    @Override
    public boolean getBoolean(int i) {
        return this.buf.getBoolean(i);
    }

    @Override
    public byte getByte(int i) {
        return this.buf.getByte(i);
    }

    @Override
    public short getUnsignedByte(int i) {
        return this.buf.getUnsignedByte(i);
    }

    @Override
    public short getShort(int i) {
        return this.buf.getShort(i);
    }

    @Override
    public short getShortLE(int i) {
        return this.buf.getShortLE(i);
    }

    @Override
    public int getUnsignedShort(int i) {
        return this.buf.getUnsignedShort(i);
    }

    @Override
    public int getUnsignedShortLE(int i) {
        return this.buf.getUnsignedShortLE(i);
    }

    @Override
    public int getMedium(int i) {
        return this.buf.getMedium(i);
    }

    @Override
    public int getMediumLE(int i) {
        return this.buf.getMediumLE(i);
    }

    @Override
    public int getUnsignedMedium(int i) {
        return this.buf.getUnsignedMedium(i);
    }

    @Override
    public int getUnsignedMediumLE(int i) {
        return this.buf.getUnsignedMediumLE(i);
    }

    @Override
    public int getInt(int i) {
        return this.buf.getInt(i);
    }

    @Override
    public int getIntLE(int i) {
        return this.buf.getIntLE(i);
    }

    @Override
    public long getUnsignedInt(int i) {
        return this.buf.getUnsignedInt(i);
    }

    @Override
    public long getUnsignedIntLE(int i) {
        return this.buf.getUnsignedIntLE(i);
    }

    @Override
    public long getLong(int i) {
        return this.buf.getLong(i);
    }

    @Override
    public long getLongLE(int i) {
        return this.buf.getLongLE(i);
    }

    @Override
    public char getChar(int i) {
        return this.buf.getChar(i);
    }

    @Override
    public float getFloat(int i) {
        return this.buf.getFloat(i);
    }

    @Override
    public double getDouble(int i) {
        return this.buf.getDouble(i);
    }

    @Override
    public ByteBuf getBytes(int i, ByteBuf bb) {
        return this.buf.getBytes(i, bb);
    }

    @Override
    public ByteBuf getBytes(int i, ByteBuf bb, int i1) {
        return this.buf.getBytes(i, bb, i1);
    }

    @Override
    public ByteBuf getBytes(int i, ByteBuf bb, int i1, int i2) {
        return this.buf.getBytes(i, bb, i1, i2);
    }

    @Override
    public ByteBuf getBytes(int i, byte[] bytes) {
        return this.buf.getBytes(i, bytes);
    }

    @Override
    public ByteBuf getBytes(int i, byte[] bytes, int i1, int i2) {
        return this.buf.getBytes(i, bytes, i1, i2);
    }

    @Override
    public ByteBuf getBytes(int i, ByteBuffer bb) {
        return this.buf.getBytes(i, bb);
    }

    @Override
    public ByteBuf getBytes(int i, OutputStream out, int i1) throws IOException {
        return this.buf.getBytes(i, out, i1);
    }

    @Override
    public int getBytes(int i, GatheringByteChannel gbc, int i1) throws IOException {
        return this.buf.getBytes(i, gbc, i1);
    }

    @Override
    public int getBytes(int i, FileChannel fc, long l, int i1) throws IOException {
        return this.buf.getBytes(i, fc, l, i1);
    }

    @Override
    public CharSequence getCharSequence(int i, int i1, Charset chrst) {
        return this.buf.getCharSequence(i, i1, chrst);
    }

    @Override
    public ByteBuf setBoolean(int i, boolean bln) {
        return this.buf.setBoolean(i, bln);
    }

    @Override
    public ByteBuf setByte(int i, int i1) {
        return this.buf.setByte(i, i1);
    }

    @Override
    public ByteBuf setShort(int i, int i1) {
        return this.buf.setShort(i, i1);
    }

    @Override
    public ByteBuf setShortLE(int i, int i1) {
        return this.buf.setShortLE(i, i1);
    }

    @Override
    public ByteBuf setMedium(int i, int i1) {
        return this.buf.setMedium(i, i1);
    }

    @Override
    public ByteBuf setMediumLE(int i, int i1) {
        return this.buf.setMediumLE(i, i1);
    }

    @Override
    public ByteBuf setInt(int i, int i1) {
        return this.buf.setInt(i, i1);
    }

    @Override
    public ByteBuf setIntLE(int i, int i1) {
        return this.buf.setIntLE(i, i1);
    }

    @Override
    public ByteBuf setLong(int i, long l) {
        return this.buf.setLong(i, l);
    }

    @Override
    public ByteBuf setLongLE(int i, long l) {
        return this.buf.setLongLE(i, l);
    }

    @Override
    public ByteBuf setChar(int i, int i1) {
        return this.buf.setChar(i, i1);
    }

    @Override
    public ByteBuf setFloat(int i, float f) {
        return this.buf.setFloat(i, f);
    }

    @Override
    public ByteBuf setDouble(int i, double d) {
        return this.buf.setDouble(i, d);
    }

    @Override
    public ByteBuf setBytes(int i, ByteBuf bb) {
        return this.buf.setBytes(i, bb);
    }

    @Override
    public ByteBuf setBytes(int i, ByteBuf bb, int i1) {
        return this.buf.setBytes(i, bb, i1);
    }

    @Override
    public ByteBuf setBytes(int i, ByteBuf bb, int i1, int i2) {
        return this.buf.setBytes(i, bb, i1, i2);
    }

    @Override
    public ByteBuf setBytes(int i, byte[] bytes) {
        return this.buf.setBytes(i, bytes);
    }

    @Override
    public ByteBuf setBytes(int i, byte[] bytes, int i1, int i2) {
        return this.buf.setBytes(i, bytes, i1, i2);
    }

    @Override
    public ByteBuf setBytes(int i, ByteBuffer bb) {
        return this.buf.setBytes(i, bb);
    }

    @Override
    public int setBytes(int i, InputStream in, int i1) throws IOException {
        return this.buf.setBytes(i, in, i1);
    }

    @Override
    public int setBytes(int i, ScatteringByteChannel sbc, int i1) throws IOException {
        return this.buf.setBytes(i, sbc, i1);
    }

    @Override
    public int setBytes(int i, FileChannel fc, long l, int i1) throws IOException {
        return this.buf.setBytes(i, fc, l, i1);
    }

    @Override
    public ByteBuf setZero(int i, int i1) {
        return this.buf.setZero(i, i1);
    }

    @Override
    public int setCharSequence(int i, CharSequence cs, Charset chrst) {
        return this.buf.setCharSequence(i, cs, chrst);
    }

    @Override
    public boolean readBoolean() {
        return this.buf.readBoolean();
    }

    @Override
    public byte readByte() {
        return this.buf.readByte();
    }

    @Override
    public short readUnsignedByte() {
        return this.buf.readUnsignedByte();
    }

    @Override
    public short readShort() {
        return this.buf.readShort();
    }

    @Override
    public short readShortLE() {
        return this.buf.readShortLE();
    }

    @Override
    public int readUnsignedShort() {
        return this.buf.readUnsignedShort();
    }

    @Override
    public int readUnsignedShortLE() {
        return this.buf.readUnsignedShortLE();
    }

    @Override
    public int readMedium() {
        return this.buf.readMedium();
    }

    @Override
    public int readMediumLE() {
        return this.buf.readMediumLE();
    }

    @Override
    public int readUnsignedMedium() {
        return this.buf.readUnsignedMedium();
    }

    @Override
    public int readUnsignedMediumLE() {
        return this.buf.readUnsignedMediumLE();
    }

    @Override
    public int readInt() {
        return this.buf.readInt();
    }

    @Override
    public int readIntLE() {
        return this.buf.readIntLE();
    }

    @Override
    public long readUnsignedInt() {
        return this.buf.readUnsignedInt();
    }

    @Override
    public long readUnsignedIntLE() {
        return this.buf.readUnsignedIntLE();
    }

    @Override
    public long readLong() {
        return this.buf.readLong();
    }

    @Override
    public long readLongLE() {
        return this.buf.readLongLE();
    }

    @Override
    public char readChar() {
        return this.buf.readChar();
    }

    @Override
    public float readFloat() {
        return this.buf.readFloat();
    }

    @Override
    public double readDouble() {
        return this.buf.readDouble();
    }

    @Override
    public ByteBuf readBytes(int i) {
        return this.buf.readBytes(i);
    }

    @Override
    public ByteBuf readSlice(int i) {
        return this.buf.readSlice(i);
    }

    @Override
    public ByteBuf readRetainedSlice(int i) {
        return this.buf.readRetainedSlice(i);
    }

    @Override
    public ByteBuf readBytes(ByteBuf bb) {
        return this.buf.readBytes(bb);
    }

    @Override
    public ByteBuf readBytes(ByteBuf bb, int i) {
        return this.buf.readBytes(bb, i);
    }

    @Override
    public ByteBuf readBytes(ByteBuf bb, int i, int i1) {
        return this.buf.readBytes(bb, i, i1);
    }

    @Override
    public ByteBuf readBytes(byte[] bytes) {
        return this.buf.readBytes(bytes);
    }

    @Override
    public ByteBuf readBytes(byte[] bytes, int i, int i1) {
        return this.buf.readBytes(bytes, i, i1);
    }

    @Override
    public ByteBuf readBytes(ByteBuffer bb) {
        return this.buf.readBytes(bb);
    }

    @Override
    public ByteBuf readBytes(OutputStream out, int i) throws IOException {
        return this.buf.readBytes(out, i);
    }

    @Override
    public int readBytes(GatheringByteChannel gbc, int i) throws IOException {
        return this.buf.readBytes(gbc, i);
    }

    @Override
    public CharSequence readCharSequence(int i, Charset chrst) {
        return this.buf.readCharSequence(i, chrst);
    }

    @Override
    public int readBytes(FileChannel fc, long l, int i) throws IOException {
        return this.buf.readBytes(fc, l, i);
    }

    @Override
    public ByteBuf skipBytes(int i) {
        return this.buf.skipBytes(i);
    }

    @Override
    public ByteBuf writeBoolean(boolean bln) {
        return this.buf.writeBoolean(bln);
    }

    @Override
    public ByteBuf writeByte(int i) {
        return this.buf.writeByte(i);
    }

    @Override
    public ByteBuf writeShort(int i) {
        return this.buf.writeShort(i);
    }

    @Override
    public ByteBuf writeShortLE(int i) {
        return this.buf.writeShortLE(i);
    }

    @Override
    public ByteBuf writeMedium(int i) {
        return this.buf.writeMedium(i);
    }

    @Override
    public ByteBuf writeMediumLE(int i) {
        return this.buf.writeMediumLE(i);
    }

    @Override
    public ByteBuf writeInt(int i) {
        return this.buf.writeInt(i);
    }

    @Override
    public ByteBuf writeIntLE(int i) {
        return this.buf.writeIntLE(i);
    }

    @Override
    public ByteBuf writeLong(long l) {
        return this.buf.writeLong(l);
    }

    @Override
    public ByteBuf writeLongLE(long l) {
        return this.buf.writeLongLE(l);
    }

    @Override
    public ByteBuf writeChar(int i) {
        return this.buf.writeChar(i);
    }

    @Override
    public ByteBuf writeFloat(float f) {
        return this.buf.writeFloat(f);
    }

    @Override
    public ByteBuf writeDouble(double d) {
        return this.buf.writeDouble(d);
    }

    @Override
    public ByteBuf writeBytes(ByteBuf bb) {
        return this.buf.writeBytes(bb);
    }

    @Override
    public ByteBuf writeBytes(ByteBuf bb, int i) {
        return this.buf.writeBytes(bb, i);
    }

    @Override
    public ByteBuf writeBytes(ByteBuf bb, int i, int i1) {
        return this.buf.writeBytes(bb, i, i1);
    }

    @Override
    public ByteBuf writeBytes(byte[] bytes) {
        return this.buf.writeBytes(bytes);
    }

    @Override
    public ByteBuf writeBytes(byte[] bytes, int i, int i1) {
        return this.buf.writeBytes(bytes, i, i1);
    }

    @Override
    public ByteBuf writeBytes(ByteBuffer bb) {
        return this.buf.writeBytes(bb);
    }

    @Override
    public int writeBytes(InputStream in, int i) throws IOException {
        return this.buf.writeBytes(in, i);
    }

    @Override
    public int writeBytes(ScatteringByteChannel sbc, int i) throws IOException {
        return this.buf.writeBytes(sbc, i);
    }

    @Override
    public int writeBytes(FileChannel fc, long l, int i) throws IOException {
        return this.buf.writeBytes(fc, l, i);
    }

    @Override
    public ByteBuf writeZero(int i) {
        return this.buf.writeZero(i);
    }

    @Override
    public int writeCharSequence(CharSequence cs, Charset chrst) {
        return this.buf.writeCharSequence(cs, chrst);
    }

    @Override
    public int indexOf(int i, int i1, byte b) {
        return this.buf.indexOf(i, i1, b);
    }

    @Override
    public int bytesBefore(byte b) {
        return this.buf.bytesBefore(b);
    }

    @Override
    public int bytesBefore(int i, byte b) {
        return this.buf.bytesBefore(i, b);
    }

    @Override
    public int bytesBefore(int i, int i1, byte b) {
        return this.buf.bytesBefore(i, i1, b);
    }

    @Override
    public int forEachByte(ByteProcessor bp) {
        return this.buf.forEachByte(bp);
    }

    @Override
    public int forEachByte(int i, int i1, ByteProcessor bp) {
        return this.buf.forEachByte(i, i1, bp);
    }

    @Override
    public int forEachByteDesc(ByteProcessor bp) {
        return this.buf.forEachByteDesc(bp);
    }

    @Override
    public int forEachByteDesc(int i, int i1, ByteProcessor bp) {
        return this.buf.forEachByteDesc(i, i1, bp);
    }

    @Override
    public ByteBuf copy() {
        return this.buf.copy();
    }

    @Override
    public ByteBuf copy(int i, int i1) {
        return this.buf.copy(i, i1);
    }

    @Override
    public ByteBuf slice() {
        return this.buf.slice();
    }

    @Override
    public ByteBuf retainedSlice() {
        return this.buf.retainedSlice();
    }

    @Override
    public ByteBuf slice(int i, int i1) {
        return this.buf.slice(i, i1);
    }

    @Override
    public ByteBuf retainedSlice(int i, int i1) {
        return this.buf.retainedSlice(i, i1);
    }

    @Override
    public ByteBuf duplicate() {
        return this.buf.duplicate();
    }

    @Override
    public ByteBuf retainedDuplicate() {
        return this.buf.retainedDuplicate();
    }

    @Override
    public int nioBufferCount() {
        return this.buf.nioBufferCount();
    }

    @Override
    public ByteBuffer nioBuffer() {
        return this.buf.nioBuffer();
    }

    @Override
    public ByteBuffer nioBuffer(int i, int i1) {
        return this.buf.nioBuffer(i, i1);
    }

    @Override
    public ByteBuffer internalNioBuffer(int i, int i1) {
        return this.buf.internalNioBuffer(i, i1);
    }

    @Override
    public ByteBuffer[] nioBuffers() {
        return this.buf.nioBuffers();
    }

    @Override
    public ByteBuffer[] nioBuffers(int i, int i1) {
        return this.buf.nioBuffers(i, i1);
    }

    @Override
    public boolean hasArray() {
        return this.buf.hasArray();
    }

    @Override
    public byte[] array() {
        return this.buf.array();
    }

    @Override
    public int arrayOffset() {
        return this.buf.arrayOffset();
    }

    @Override
    public boolean hasMemoryAddress() {
        return this.buf.hasMemoryAddress();
    }

    @Override
    public long memoryAddress() {
        return this.buf.memoryAddress();
    }

    @Override
    public String toString(Charset chrst) {
        return this.buf.toString(chrst);
    }

    @Override
    public String toString(int i, int i1, Charset chrst) {
        return this.buf.toString(i, i1, chrst);
    }

    @Override
    public int hashCode() {
        return this.buf.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return this.buf.equals(o);
    }

    @Override
    public int compareTo(ByteBuf bb) {
        return this.buf.compareTo(bb);
    }

    @Override
    public String toString() {
        return this.buf.toString();
    }

    @Override
    public ByteBuf retain(int i) {
        return this.buf.retain(i);
    }

    @Override
    public ByteBuf retain() {
        return this.buf.retain();
    }

    @Override
    public ByteBuf touch() {
        return this.buf.touch();
    }

    @Override
    public ByteBuf touch(Object o) {
        return this.buf.touch(o);
    }

    @Override
    public int refCnt() {
        return this.buf.refCnt();
    }

    @Override
    public boolean release() {
        return this.buf.release();
    }

    @Override
    public boolean release(int i) {
        return this.buf.release(i);
    }
}
