#!/usr/bin/env python3
"""Contrôle d'un .dex : présence exacte de chaînes et définition d'une classe.

Sert à éviter le piège du grep sur un binaire : on lit la table des chaînes et les
class_defs, au lieu de compter des « lignes ».

Usage : python dexcheck.py <fichier.dex> <motif1> [motif2 ...]
"""
import struct
import sys


def uleb128(data: bytes, off: int) -> tuple[int, int]:
    result = 0
    shift = 0
    while True:
        byte = data[off]
        off += 1
        result |= (byte & 0x7F) << shift
        if not byte & 0x80:
            return result, off
        shift += 7


def read_strings(data: bytes) -> list[str]:
    string_ids_size, string_ids_off = struct.unpack_from("<II", data, 56)
    out = []
    for i in range(string_ids_size):
        (off,) = struct.unpack_from("<I", data, string_ids_off + 4 * i)
        _size, p = uleb128(data, off)
        end = data.index(b"\x00", p)
        out.append(data[p:end].decode("utf-8", "replace"))
    return out


def read_classes(data: bytes, strings: list[str]) -> list[str]:
    type_ids_size, type_ids_off = struct.unpack_from("<II", data, 64)
    class_defs_size, class_defs_off = struct.unpack_from("<II", data, 96)
    names = []
    for i in range(class_defs_size):
        (class_idx,) = struct.unpack_from("<I", data, class_defs_off + 32 * i)
        if class_idx >= type_ids_size:
            continue
        (descriptor_idx,) = struct.unpack_from("<I", data, type_ids_off + 4 * class_idx)
        if descriptor_idx < len(strings):
            names.append(strings[descriptor_idx])
    return names


def main() -> int:
    path = sys.argv[1]
    patterns = sys.argv[2:]
    data = open(path, "rb").read()
    strings = read_strings(data)
    print(f"{path} : {len(strings)} chaînes")

    for p in patterns:
        # La table des chaînes contient les noms de méthodes/champs référencés ;
        # « défini » se voit dans les class_defs.
        hits = [s for s in strings if s == p]
        print(f"  chaîne exacte {p!r} : {'oui' if hits else 'non'}"
              f" (sous-chaînes : {sum(1 for s in strings if p in s)})")

    for p in patterns:
        if p.startswith("L") or "." in p:
            classes = read_classes(data, strings)
            found = [c for c in classes if p in c]
            if found:
                print(f"  classe contenant {p!r} : {found[:5]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
