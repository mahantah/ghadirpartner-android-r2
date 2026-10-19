"""Check ELF LOAD alignment in 64-bit native libraries; no APK mutation."""
import struct
import sys
import zipfile

with zipfile.ZipFile(sys.argv[1]) as archive:
    checked = 0
    for item in archive.namelist():
        if not item.endswith('.so') or not item.startswith(('lib/arm64-v8a/', 'lib/x86_64/')):
            continue
        data = archive.read(item)
        if data[:4] != b'\x7fELF' or data[4] != 2:
            raise SystemExit('Invalid ELF64: ' + item)
        endian = '<' if data[5] == 1 else '>'
        offset = struct.unpack_from(endian + 'Q', data, 32)[0]
        size, count = struct.unpack_from(endian + 'HH', data, 54)
        loads = [struct.unpack_from(endian + 'IIQQQQQQ', data, offset + i * size)
                 for i in range(count)]
        if any(header[0] == 1 and header[7] < 16384 for header in loads):
            raise SystemExit('ELF LOAD is not 16 KB aligned: ' + item)
        checked += 1
        print('ELF 16 KB aligned:', item)
    print('64-bit native libraries checked:', checked)
