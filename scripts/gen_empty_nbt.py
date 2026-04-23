#!/usr/bin/env python3
"""
Generate an empty 3x3x3 structure NBT template for GameTest.

Output: neoforge-1.21.1/src/main/resources/data/fluidphysics/structure/empty.nbt
~108 bytes, gzipped, with DataVersion=3953 (Minecraft 1.21.1).

Run from the repo root:
    python scripts/gen_empty_nbt.py
"""

import gzip
import struct
import sys
from pathlib import Path

# NBT tag IDs
TAG_END        = 0
TAG_BYTE       = 1
TAG_SHORT      = 2
TAG_INT        = 3
TAG_LONG       = 4
TAG_FLOAT      = 5
TAG_DOUBLE     = 6
TAG_BYTE_ARRAY = 7
TAG_STRING     = 8
TAG_LIST       = 9
TAG_COMPOUND   = 10


def _name(name: str) -> bytes:
    b = name.encode('utf-8')
    return struct.pack('>H', len(b)) + b


def named_tag(tag_id: int, name: str, payload: bytes) -> bytes:
    return bytes([tag_id]) + _name(name) + payload


def string_payload(value: str) -> bytes:
    b = value.encode('utf-8')
    return struct.pack('>H', len(b)) + b


def int_payload(value: int) -> bytes:
    return struct.pack('>i', value)


def list_payload(element_tag_id: int, elements_payload: list[bytes]) -> bytes:
    return bytes([element_tag_id]) + struct.pack('>i', len(elements_payload)) + b''.join(elements_payload)


def int_list_payload(values: list[int]) -> bytes:
    return list_payload(TAG_INT, [int_payload(v) for v in values])


def compound_end() -> bytes:
    return bytes([TAG_END])


def compound(payload: bytes) -> bytes:
    return payload + compound_end()


def build_empty_structure() -> bytes:
    # Palette: [{ Name: "minecraft:air" }]
    air_compound = compound(named_tag(TAG_STRING, 'Name', string_payload('minecraft:air')))
    palette_list = list_payload(TAG_COMPOUND, [air_compound])

    root_payload = b''.join([
        named_tag(TAG_LIST, 'size', int_list_payload([3, 3, 3])),
        named_tag(TAG_LIST, 'entities', list_payload(TAG_END, [])),
        named_tag(TAG_LIST, 'blocks', list_payload(TAG_END, [])),
        named_tag(TAG_LIST, 'palette', palette_list),
        named_tag(TAG_INT, 'DataVersion', int_payload(3953)),  # 1.21.1
    ])

    # Outer file structure: one named compound wrapping the payload
    raw = named_tag(TAG_COMPOUND, '', compound(root_payload))
    return raw


def main() -> None:
    repo_root = Path(__file__).resolve().parent.parent
    target = repo_root / 'neoforge-1.21.1' / 'src' / 'main' / 'resources' / 'data' / 'fluidphysics' / 'structure' / 'empty.nbt'
    target.parent.mkdir(parents=True, exist_ok=True)

    raw = build_empty_structure()
    with gzip.open(target, 'wb', compresslevel=9) as f:
        f.write(raw)

    size = target.stat().st_size
    print(f'Wrote {target} ({size} bytes)', file=sys.stderr)


if __name__ == '__main__':
    main()
