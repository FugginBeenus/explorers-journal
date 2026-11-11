"""A tiny NBT reader and writer, for setting up test worlds. Each value is (tag type, value)."""
import gzip, struct
def _r(f, t):
    if t == 1: return struct.unpack('>b', f.read(1))[0]
    if t == 2: return struct.unpack('>h', f.read(2))[0]
    if t == 3: return struct.unpack('>i', f.read(4))[0]
    if t == 4: return struct.unpack('>q', f.read(8))[0]
    if t == 5: return struct.unpack('>f', f.read(4))[0]
    if t == 6: return struct.unpack('>d', f.read(8))[0]
    if t == 7: n = _r(f, 3); return f.read(n)
    if t == 8: n = struct.unpack('>H', f.read(2))[0]; return f.read(n).decode('utf-8')
    if t == 9:
        et = _r(f, 1); n = _r(f, 3); return (et, [_r(f, et) for _ in range(n)])
    if t == 10:
        d = {}
        while True:
            tt = _r(f, 1)
            if tt == 0: return d
            n = struct.unpack('>H', f.read(2))[0]; name = f.read(n).decode('utf-8')
            d[name] = (tt, _r(f, tt))
    if t == 11: n = _r(f, 3); return [_r(f, 3) for _ in range(n)]
    if t == 12: n = _r(f, 3); return [_r(f, 4) for _ in range(n)]
    raise ValueError(t)
def _w(f, t, v):
    if t == 1: f.write(struct.pack('>b', v))
    elif t == 2: f.write(struct.pack('>h', v))
    elif t == 3: f.write(struct.pack('>i', v))
    elif t == 4: f.write(struct.pack('>q', v))
    elif t == 5: f.write(struct.pack('>f', v))
    elif t == 6: f.write(struct.pack('>d', v))
    elif t == 7: _w(f, 3, len(v)); f.write(v)
    elif t == 8: b = v.encode('utf-8'); f.write(struct.pack('>H', len(b))); f.write(b)
    elif t == 9:
        et, items = v; _w(f, 1, et if items else (et or 0)); _w(f, 3, len(items))
        for i in items: _w(f, et, i)
    elif t == 10:
        for name, (tt, vv) in v.items():
            _w(f, 1, tt); b = name.encode('utf-8'); f.write(struct.pack('>H', len(b))); f.write(b); _w(f, tt, vv)
        _w(f, 1, 0)
    elif t == 11: _w(f, 3, len(v)); [ _w(f, 3, i) for i in v ]
    elif t == 12: _w(f, 3, len(v)); [ _w(f, 4, i) for i in v ]
def load(path):
    with gzip.open(path) as f:
        t = _r(f, 1); n = struct.unpack('>H', f.read(2))[0]; name = f.read(n).decode('utf-8')
        return (name, _r(f, t))
def save(path, root):
    name, value = root
    with gzip.open(path, 'wb') as f:
        _w(f, 1, 10); b = name.encode('utf-8'); f.write(struct.pack('>H', len(b))); f.write(b); _w(f, 10, value)


def patch(path):
    """Sets a world up like the 1.21.1 test world: creative, commands on, night, and spawn at the village."""
    root = load(path)
    d = root[1]['Data'][1]
    d['allowCommands'] = (1, 1)
    d['GameType'] = (3, 1)
    d['Difficulty'] = (1, 2)
    d['SpawnX'] = (3, -976)
    d['SpawnY'] = (3, 67)
    d['SpawnZ'] = (3, 150)
    d['DayTime'] = (4, 17435)
    d['LevelName'] = (8, 'TestWorld')
    rules = d['GameRules'][1]
    rules['spawnRadius'] = (8, '0')
    rules['mobGriefing'] = (8, 'false')
    save(path, root)


if __name__ == '__main__':
    import sys
    patch(sys.argv[1])
    print('set up', sys.argv[1])
