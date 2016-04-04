ByteOrder = {
    "BIG_ENDIAN": "BIG_ENDIAN",
    "LITTLE_ENDIAN": "LITTLE_ENDIAN"
};

Bits = {};
function int3(x) {
    return x >> 24;
}
function int2(x) {
    return (x >> 16) & 0xff;
}
function int1(x) {
    return (x >> 8) & 0xff;
}
function int0(x) {
    return x & 0xff;
}

function short1(x) {
    return (x >> 8) & 0xff;
}
function short0(x) {
    return x & 0xff;
}

Bits.makeInt = function(b3, b2, b1, b0) {
    return ((((b3 & 0xff) << 24) |
    ((b2 & 0xff) << 16) |
    ((b1 & 0xff) <<  8) |
    (b0 & 0xff)));
};

Bits.getIntB = function(bb, bi) {
    return Bits.makeInt(
        bb._get(bi + 0),
        bb._get(bi + 1),
        bb._get(bi + 2),
        bb._get(bi + 3));
};

Bits.getIntL = function(bb, bi) {
    return Bits.makeInt(
        bb._get(bi + 3),
        bb._get(bi + 2),
        bb._get(bi + 1),
        bb._get(bi + 0));
};

Bits.getInt = function(bb, bi, bigEndian) {
    return (bigEndian ? Bits.getIntB(bb, bi) : Bits.getIntL(bb, bi));
};

Bits.putIntB = function(bb, bi, x) {
    bb._put(bi + 0, int3(x));
    bb._put(bi + 1, int2(x));
    bb._put(bi + 2, int1(x));
    bb._put(bi + 3, int0(x));
};

Bits.putInt = function(bb, bi, x, bigEndian) {
    if (bigEndian)
        Bits.putIntB(bb, bi, x);
    else
        Bits.putIntL(bb, bi, x);
};

Bits.makeShort = function(b1, b0) {
    return ((b1 << 8) | (b0 & 0xff));
};

Bits.getShortL = function(bb, bi) {
    return Bits.makeShort(bb._get(bi + 1), bb._get(bi + 0));
};

Bits.getShortB = function(bb, bi) {
    return Bits.makeShort(bb._get(bi + 0), bb._get(bi + 1));
};

Bits.getShort = function(bb, bi, bigEndian) {
    return (bigEndian ? Bits.getShortB(bb, bi) : Bits.getShortL(bb, bi));
};

Bits.putShortL = function(bb, bi, x) {
    bb._put(bi + 0, short0(x));
    bb._put(bi + 1, short1(x));
};

Bits.putShortB = function(bb, bi, x) {
    bb._put(bi + 0, short1(x));
    bb._put(bi + 1, short0(x));
};

Bits.putShort = function(bb, bi, x, bigEndian) {
    if (bigEndian)
        Bits.putShortB(bb, bi, x);
    else
        Bits.putShortL(bb, bi, x);
};

// warning: works only for 53 bits or less!
Bits.makeLong = function(b7, b6, b5, b4, b3, b2, b1, b0) {
    return (((b7 & 0xff) << 24) |
        ((b6 & 0xff) << 16) |
        ((b5 & 0xff) << 8) |
        (b4 & 0xff)) * 0x100000000 +
        ((b3 & 0xff) * 0x1000000) +
        (((b2 & 0xff) << 16) |
        ((b1 & 0xff) <<  8) |
        (b0 & 0xff));
};

Bits.getLongL = function(bb, bi) {
    return Bits.makeLong(bb._get(bi + 7),
        bb._get(bi + 6),
        bb._get(bi + 5),
        bb._get(bi + 4),
        bb._get(bi + 3),
        bb._get(bi + 2),
        bb._get(bi + 1),
        bb._get(bi + 0));
};

Bits.getLongB = function(bb, bi) {
    return Bits.makeLong(bb._get(bi + 0),
        bb._get(bi + 1),
        bb._get(bi + 2),
        bb._get(bi + 3),
        bb._get(bi + 4),
        bb._get(bi + 5),
        bb._get(bi + 6),
        bb._get(bi + 7));
};

Bits.getLong = function(bb, bi, bigEndian) {
    return (bigEndian ? Bits.getLongB(bb, bi) : Bits.getLongL(bb, bi));
};

// warning: works only for 53 bits or less!
function long7(x) { return (x / 0x100000000 >> 24) & 0xff; }
function long6(x) { return (x / 0x100000000 >> 16) & 0xff; }
function long5(x) { return (x / 0x100000000 >> 8) & 0xff; }
function long4(x) { return (x / 0x100000000 >> 0) & 0xff; }
function long3(x) { return (x >> 24) & 0xff; }
function long2(x) { return (x >> 16) & 0xff; }
function long1(x) { return (x >>  8) & 0xff; }
function long0(x) { return (x >>  0) & 0xff; }

Bits.putLongL = function(bb, bi, x) {
    bb._put(bi + 7, long7(x));
    bb._put(bi + 6, long6(x));
    bb._put(bi + 5, long5(x));
    bb._put(bi + 4, long4(x));
    bb._put(bi + 3, long3(x));
    bb._put(bi + 2, long2(x));
    bb._put(bi + 1, long1(x));
    bb._put(bi + 0, long0(x));
};

Bits.putLongB = function(bb, bi, x) {
    bb._put(bi + 0, long7(x));
    bb._put(bi + 1, long6(x));
    bb._put(bi + 2, long5(x));
    bb._put(bi + 3, long4(x));
    bb._put(bi + 4, long3(x));
    bb._put(bi + 5, long2(x));
    bb._put(bi + 6, long1(x));
    bb._put(bi + 7, long0(x));
};

Bits.putLong = function(bb, bi, x, bigEndian) {
    if (bigEndian)
        Bits.putLongB(bb, bi, x);
    else
        Bits.putLongL(bb, bi, x);
};

// 53 bit longs should work ok
// maximum integer is 2^53-1 = 0x001FFFFFFFFFFFFF
Bits.testLong = function() {
    Assert.assertEquals(0x001FFFFFFFFFFFFF, Bits.makeLong(0x00, 0x1f, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff));
    Assert.assertEquals(0x00123456789abcde, Bits.makeLong(0x00, 0x12, 0x34, 0x56, 0x78, 0x9a, 0xbc, 0xde));

    Assert.assertEquals(0x00, long7(0x001FFFFFFFFFFFFF));
    Assert.assertEquals(0x1f, long6(0x001FFFFFFFFFFFFF));
    Assert.assertEquals(0xff, long5(0x001FFFFFFFFFFFFF));
    Assert.assertEquals(0xff, long4(0x001FFFFFFFFFFFFF));
    Assert.assertEquals(0xff, long3(0x001FFFFFFFFFFFFF));
    Assert.assertEquals(0xff, long2(0x001FFFFFFFFFFFFF));
    Assert.assertEquals(0xff, long1(0x001FFFFFFFFFFFFF));
    Assert.assertEquals(0xff, long0(0x001FFFFFFFFFFFFF));

    Assert.assertEquals(0x00, long7(0x00123456789abcde));
    Assert.assertEquals(0x12, long6(0x00123456789abcde));
    Assert.assertEquals(0x34, long5(0x00123456789abcde));
    Assert.assertEquals(0x56, long4(0x00123456789abcde));
    Assert.assertEquals(0x78, long3(0x00123456789abcde));
    Assert.assertEquals(0x9a, long2(0x00123456789abcde));
    Assert.assertEquals(0xbc, long1(0x00123456789abcde));
    Assert.assertEquals(0xde, long0(0x00123456789abcde));
};


function ByteBuffer(hb, mark, position, limit, capacity, offset) {
    // final byte[] hb;
    this.hb = arguments.length >= 1 ? hb : null;
    // private int mark = -1;
    this._mark = arguments.length >= 2 ? mark : -1;
    // private int position = 0;
    this._position = arguments.length >= 3 ? position : 0;
    // private int limit;
    this._limit = arguments.length >= 4 ? limit : 0;
    // private int capacity;
    this._capacity = arguments.length >= 5 ? capacity : 0;
    // final int offset;
    this.offset = arguments.length >= 6 ? offset : 0;
}

ByteBuffer.prototype.bigEndian = true;

ByteBuffer.prototype.duplicate = function() {
    return new ByteBuffer(this.hb, this.markValue(), this.position(), this.limit(), this.capacity(), this.offset);
};

ByteBuffer.prototype.markValue = function() {
    return this._mark;
};
ByteBuffer.prototype.position = function(position) {
    if (arguments.length > 0) {
        if ((position > this._limit) || (position < 0))
            throw "IllegalArgumentException";
        this._position = position;
        if (this._mark > position) this._mark = -1;
        return this;
    }
    return this._position;
};
ByteBuffer.prototype.limit = function(limit) {
    if (arguments.length > 0) {
        this._limit = limit;
        return this;
    }
    return this._limit;
};
ByteBuffer.prototype.capacity = function() {
    return this._capacity;
};
ByteBuffer.prototype.ix = function(i) {
    return i + this.offset;
};

ByteBuffer.prototype.nextPutIndex1 = function() { // package-private
    if (this._position >= this._limit)
        throw 'BufferOverflowException';
    return this._position++;
};

ByteBuffer.prototype.nextPutIndex = function(nb) { // package-private
    if (this._limit - this._position < nb)
        throw 'BufferOverflowException';
    var p = this._position;
    this._position += nb;
    return p;
};

ByteBuffer.prototype.nextGetIndex1 = function() { // package-private
    if (this._position >= this._limit)
        throw 'BufferUnderflowException';
    return this._position++;
};

ByteBuffer.prototype.nextGetIndex = function(nb) { // package-private
    if (this._limit - this._position < nb)
        throw 'BufferUnderflowException';
    var p = this._position;
    this._position += nb;
    return p;
};

ByteBuffer.prototype.getInt = function() {
    return Bits.getInt(this, this.ix(this.nextGetIndex(4)), this.bigEndian);
};

ByteBuffer.prototype.putInt = function(x) {
    Bits.putInt(this, this.ix(this.nextPutIndex(4)), x, this.bigEndian);
    return this;
};

ByteBuffer.prototype._get = function(i) {
    return this.hb[i];
};

ByteBuffer.prototype._put = function(i, b) {
    this.hb[i] = b;
};

ByteBuffer.prototype.get = function(i) {
    if ("undefined" == typeof i) {
        return this.hb[this.ix(this.nextGetIndex1())];
    } else {
        return this.hb[this.ix(i)];
    }
};

ByteBuffer.prototype.getShort = function(i) {
    if ("undefined" == typeof i)
        return Bits.getShort(this, this.ix(this.nextGetIndex(2)), this.bigEndian);
    else
        return Bits.getShort(this, this.ix(this.checkIndex(i, 2)), this.bigEndian);
};

ByteBuffer.prototype.put = function(x) {
    var idx = this.ix(this.nextPutIndex1());
    this.hb[idx] = x;
    return this;
};

ByteBuffer.prototype.putShort = function(i, x) {
    if ("undefined" == typeof x) { x = i; i = null; }
    if (i == null)
        Bits.putShort(this, this.ix(this.nextPutIndex(2)), x, this.bigEndian);
    else
        Bits.putShort(this, this.ix(this.checkIndex(i, 2)), x, this.bigEndian);
    return this;
};

ByteBuffer.prototype.getLong = function(i) {
    if ("undefined" == typeof i)
        return Bits.getLong(this, this.ix(this.nextGetIndex(8)), this.bigEndian);
    else
        return Bits.getLong(this, this.ix(this.checkIndex(i, 8)), this.bigEndian);
};

ByteBuffer.prototype.putLong = function(i, x) {
    if ("undefined" == typeof x) { x = i; i = null; }
    if (i == null)
        Bits.putLong(this, this.ix(this.nextPutIndex(8)), x, this.bigEndian);
    else
        Bits.putLong(this, this.ix(this.checkIndex(i, 8)), x, this.bigEndian);
    return this;
};

ByteBuffer.prototype.getToArray = function(array, offset, length) {
    offset = arguments.length <= 1 ? 0 : offset;
    length = arguments.length <= 2 ? array.length : length;

    ByteBuffer.checkBounds(offset, length, array.length);
    if (this.remaining() < array.length)
        throw 'BufferUnderflowException';

    System.arraycopy(this.hb, this.ix(this.position()), array, offset, array.length);
    this.position(this.position() + array.length);
    return this;
};

ByteBuffer.prototype.putArray = function(src, offset, length) {
    if (!offset) offset = 0;
    if ("undefined" == typeof length) length = src.length;

    ByteBuffer.checkBounds(offset, length, src.length);
    if (length > this.remaining())
        throw 'BufferOverflowException';

    var end = offset + length;
    for (var i = offset; i < end; i++)
        this.put(src[i]);
    return this;
};

ByteBuffer.prototype.putByteBuffer = function(src) {
    if (src == this)
        throw 'IllegalArgumentException';
    var n = src.remaining();
    if (n > this.remaining())
        throw 'BufferOverflowException';

    System.arraycopy(src.hb, src.ix(src.position()), this.hb, this.ix(this.position()), n);

    src.position(src.position() + n);
    this.position(this.position() + n);
    return this;
};

ByteBuffer.prototype.flip = function() {
    this._limit = this._position;
    this._position = 0;
    this._mark = -1;
    return this;
};

ByteBuffer.prototype.array = function() {
    return this.hb;
};

ByteBuffer.prototype.arrayOffset = function() {
    return this.offset;
};

ByteBuffer.prototype.hasRemaining = function() {
    return this._position < this._limit;
};

ByteBuffer.prototype.remaining = function() {
    return this._limit - this._position;
};

ByteBuffer.prototype.clear = function() {
    this._position = 0;
    this._limit = this._capacity;
    this._mark = -1;
    return this;
};

ByteBuffer.prototype.slice = function() {
    return new ByteBuffer(this.hb,
        -1,
        0,
        this.remaining(),
        this.remaining(),
        this.position() + this.offset);
};

ByteBuffer.prototype.toString = function() {
    return "[pos=" + this._position + " lim=" + this._limit + " cap=" + this._capacity + "]";
};

ByteBuffer.prototype.checkIndex = function(i, nb) {
    if ((i < 0) || (nb > this._limit - i))
        throw "IndexOutOfBoundsException";
    return i;
};

ByteBuffer.prototype.order = function(order) {
    if (order) {
        this.bigEndian = order == ByteOrder.BIG_ENDIAN;
        return this;
    }
    return this.bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
};

ByteBuffer.allocate = function(size) {
    var bb = new ByteBuffer();
    bb.hb = new Int8Array(size);
    bb._limit = size;
    bb._capacity = size;
    return bb;
};

ByteBuffer.checkBounds = function(off, len, size) { // package-private
    if ((off | len | (off + len) | (size - (off + len))) < 0)
        throw 'IndexOutOfBoundsException';
};

ByteBuffer.wrap = function(array) {
    return new ByteBuffer(array, -1, 0, array.length, array.length);
};
