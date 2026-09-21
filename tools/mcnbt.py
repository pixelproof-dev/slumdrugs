"""A small NBT reader and writer, enough for structure files. No dependencies.

Tags are plain Python values with a thin wrapper where the type matters:
  Compound -> dict, List -> list (of one type), String -> str, Byte/Short/Int/Long -> Int(kind),
  Float/Double -> Flt(kind), ByteArray/IntArray/LongArray -> Arr(kind, list).
Reading gives the same shapes back, so a file can be read, changed and written again.
"""
import gzip
import struct

TAG_END, TAG_BYTE, TAG_SHORT, TAG_INT, TAG_LONG, TAG_FLOAT, TAG_DOUBLE = range(7)
TAG_BYTE_ARRAY, TAG_STRING, TAG_LIST, TAG_COMPOUND, TAG_INT_ARRAY, TAG_LONG_ARRAY = range(7, 13)


class Int(int):
    """An integer with a declared width: 'b', 's', 'i' or 'l'."""
    def __new__(cls, value, kind='i'):
        o = int.__new__(cls, value)
        o.kind = kind
        return o


class Flt(float):
    def __new__(cls, value, kind='d'):
        o = float.__new__(cls, value)
        o.kind = kind
        return o


class Arr:
    def __init__(self, kind, values):
        self.kind = kind  # 'b', 'i' or 'l'
        self.values = list(values)

    def __repr__(self): return f'Arr({self.kind!r}, {self.values})'


def tag_type(v):
    if isinstance(v, bool): return TAG_BYTE
    if isinstance(v, Int): return {'b': TAG_BYTE, 's': TAG_SHORT, 'i': TAG_INT, 'l': TAG_LONG}[v.kind]
    if isinstance(v, int): return TAG_INT
    if isinstance(v, Flt): return TAG_FLOAT if v.kind == 'f' else TAG_DOUBLE
    if isinstance(v, float): return TAG_DOUBLE
    if isinstance(v, str): return TAG_STRING
    if isinstance(v, dict): return TAG_COMPOUND
    if isinstance(v, list): return TAG_LIST
    if isinstance(v, Arr): return {'b': TAG_BYTE_ARRAY, 'i': TAG_INT_ARRAY, 'l': TAG_LONG_ARRAY}[v.kind]
    raise TypeError(f'not an NBT value: {v!r}')


def _write_payload(out, t, v):
    if t == TAG_BYTE: out += struct.pack('>b', int(v))
    elif t == TAG_SHORT: out += struct.pack('>h', int(v))
    elif t == TAG_INT: out += struct.pack('>i', int(v))
    elif t == TAG_LONG: out += struct.pack('>q', int(v))
    elif t == TAG_FLOAT: out += struct.pack('>f', float(v))
    elif t == TAG_DOUBLE: out += struct.pack('>d', float(v))
    elif t == TAG_STRING:
        b = v.encode('utf-8'); out += struct.pack('>H', len(b)) + b
    elif t == TAG_LIST:
        et = tag_type(v[0]) if v else TAG_END
        out += struct.pack('>bi', et, len(v))
        for item in v: _write_payload(out, et, item)
    elif t == TAG_COMPOUND:
        for k, item in v.items():
            it = tag_type(item)
            kb = k.encode('utf-8')
            out += struct.pack('>bH', it, len(kb)) + kb
            _write_payload(out, it, item)
        out += b'\x00'
    elif t in (TAG_BYTE_ARRAY, TAG_INT_ARRAY, TAG_LONG_ARRAY):
        fmt = {TAG_BYTE_ARRAY: 'b', TAG_INT_ARRAY: 'i', TAG_LONG_ARRAY: 'q'}[t]
        out += struct.pack('>i', len(v.values)) + struct.pack('>' + fmt * len(v.values), *v.values)


def dumps(root, name=''):
    out = bytearray()
    nb = name.encode('utf-8')
    out += struct.pack('>bH', TAG_COMPOUND, len(nb)) + nb
    _write_payload(out, TAG_COMPOUND, root)
    return bytes(out)


def write(path, root, name=''):
    path.write_bytes(gzip.compress(dumps(root, name), 9, mtime=0))


class _Reader:
    def __init__(self, data): self.d, self.i = data, 0

    def take(self, fmt):
        v = struct.unpack_from('>' + fmt, self.d, self.i)
        self.i += struct.calcsize('>' + fmt)
        return v if len(v) > 1 else v[0]

    def string(self):
        n = self.take('H'); s = self.d[self.i:self.i + n].decode('utf-8'); self.i += n; return s

    def payload(self, t):
        if t == TAG_END: return None
        if t == TAG_BYTE: return Int(self.take('b'), 'b')
        if t == TAG_SHORT: return Int(self.take('h'), 's')
        if t == TAG_INT: return Int(self.take('i'), 'i')
        if t == TAG_LONG: return Int(self.take('q'), 'l')
        if t == TAG_FLOAT: return Flt(self.take('f'), 'f')
        if t == TAG_DOUBLE: return Flt(self.take('d'), 'd')
        if t == TAG_STRING: return self.string()
        if t == TAG_LIST:
            et, n = self.take('bi')
            if et == TAG_END: return []
            return [self.payload(et) for _ in range(n)]
        if t == TAG_COMPOUND:
            out = {}
            while True:
                it = self.take('b')
                if it == TAG_END: return out
                key = self.string()  # read the name before the value: Python evaluates the right side of an assignment first
                out[key] = self.payload(it)
        if t in (TAG_BYTE_ARRAY, TAG_INT_ARRAY, TAG_LONG_ARRAY):
            fmt = {TAG_BYTE_ARRAY: 'b', TAG_INT_ARRAY: 'i', TAG_LONG_ARRAY: 'q'}[t]
            n = self.take('i')
            vals = list(struct.unpack_from('>' + fmt * n, self.d, self.i)); self.i += struct.calcsize(fmt) * n
            return Arr({TAG_BYTE_ARRAY: 'b', TAG_INT_ARRAY: 'i', TAG_LONG_ARRAY: 'l'}[t], vals)
        raise ValueError(f'unknown tag {t}')


def loads(data):
    if data[:2] == b'\x1f\x8b': data = gzip.decompress(data)
    r = _Reader(data)
    t = r.take('b'); name = r.string()
    return name, r.payload(t)


def read(path):
    return loads(path.read_bytes())[1]
