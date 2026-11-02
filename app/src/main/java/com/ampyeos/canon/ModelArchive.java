package com.ampyeos.canon;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

/**
 * Streaming tar.gz reader shared by the Android downloader and CI regression test.
 *
 * A TensorFlow training bundle may include checkpoints much larger than the
 * small .tflite file we need. Skip those entries without allocating their size
 * in RAM or writing them to disk. Limit the entire expanded stream as a guard
 * against malformed and decompression-bomb archives. The caller MUST verify
 * the extracted file's pinned SHA-256 before using it.
 */
public final class ModelArchive {
    public static final long MAX_EXPANDED_BYTES = 4L * 1024 * 1024 * 1024;
    private static final int BLOCK = 512;

    private ModelArchive() {}

    /** Extract exactly one regular .tflite entry. Returns bytes consumed through the entry. */
    public static long extract(File tgz, File output, String modelName,
                               long maxModelBytes) throws IOException {
        if (tgz == null || output == null || modelName == null ||
            maxModelBytes <= 0 || modelName.isEmpty())
            throw new IOException("Parametri di estrazione non validi");
        long scanned = 0;
        int entries = 0;
        try (InputStream in = new GZIPInputStream(
                new BufferedInputStream(new FileInputStream(tgz), 64 * 1024), 64 * 1024)) {
            byte[] header = new byte[BLOCK];
            while (true) {
                int read = readBlock(in, header);
                if (read == 0) break;
                if (read != BLOCK) throw new IOException("Intestazione TAR troncata");
                scanned = safeAdvance(scanned, BLOCK);
                boolean zeros = true;
                for (byte value : header) {
                    if (value != 0) {zeros = false; break;}
                }
                if (zeros) break;
                if (++entries > 100_000)
                    throw new IOException("Troppe voci TAR");

                long size = size(header);
                long padding = (BLOCK - size % BLOCK) % BLOCK;
                // Size of *other* files is unrelated to size of the model
                // and must not be capped at maxModelBytes.
                long end = safeAdvance(safeAdvance(scanned, size), padding);
                if (end > MAX_EXPANDED_BYTES)
                    throw new IOException("Archivio espanso oltre limite di sicurezza (4 GiB)");

                String path = name(header);
                int type = header[156] & 0xff;
                boolean regular = type == 0 || type == '0';
                boolean wanted = regular &&
                    (modelName.equals(path) || path.endsWith("/" + modelName));
                if (wanted) {
                    if (size < 2_000_000 || size > maxModelBytes)
                        throw new IOException("Dimensione del solo modello non valida: " + size);
                    try (OutputStream out = new BufferedOutputStream(
                            new FileOutputStream(output), 64 * 1024)) {
                        copyExact(in, out, size);
                    }
                    // Don't need to unpack unrelated checkpoints after this file.
                    return end;
                }
                // Streaming skip: constant memory, no separate files created.
                discardExact(in, size + padding);
                scanned = end;
            }
        }
        throw new IOException("Modello TFLite richiesto non presente nell'archivio");
    }

    private static long safeAdvance(long a, long b) throws IOException {
        if (a < 0 || b < 0 || a > MAX_EXPANDED_BYTES - b)
            throw new IOException("Archivio espanso oltre limite di sicurezza (4 GiB)");
        return a + b;
    }
    private static int readBlock(InputStream in, byte[] buf) throws IOException {
        int offset = 0;
        while (offset < buf.length) {
            int n = in.read(buf, offset, buf.length - offset);
            if (n < 0) return offset;
            if (n == 0) continue;
            offset += n;
        }
        return offset;
    }
    private static long size(byte[] header) throws IOException {
        // POSIX ustar uses a 12-byte octal field; GNU tar may set the high bit
        // of byte zero for base-256 sizes.
        int first = header[124] & 0xff;
        if ((first & 0x80) != 0) {
            if ((first & 0x40) != 0)
                throw new IOException("Dimensione TAR negativa non valida");
            long result = first & 0x3f;
            for (int i = 125; i < 136; i++) {
                if (result > (Long.MAX_VALUE - (header[i] & 0xff)) / 256)
                    throw new IOException("Dimensione TAR troppo grande");
                result = (result << 8) | (header[i] & 0xff);
            }
            return result;
        }
        long value = 0;
        boolean started = false;
        for (int i = 124; i < 136; i++) {
            int c = header[i] & 0xff;
            if (c == 0) break;
            if (c == ' ' && !started) continue;
            if (c == ' ' && started) break;
            if (c < '0' || c > '7')
                throw new IOException("Dimensione TAR non ottale");
            started = true;
            if (value > (Long.MAX_VALUE - (c - '0')) / 8)
                throw new IOException("Dimensione TAR fuori intervallo");
            value = (value << 3) | (c - '0');
        }
        return value;
    }
    private static String name(byte[] header) {
        String base = field(header, 0, 100);
        String prefix = field(header, 345, 155);
        return prefix.isEmpty() ? base : prefix + "/" + base;
    }
    private static String field(byte[] h, int offset, int length) {
        int n = 0;
        while (n < length && h[offset + n] != 0) n++;
        return new String(h, offset, n, StandardCharsets.UTF_8);
    }
    private static void copyExact(InputStream in, OutputStream out, long bytes)
            throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long remaining = bytes;
        while (remaining > 0) {
            int n = in.read(buffer, 0, (int)Math.min(buffer.length, remaining));
            if (n < 0) throw new IOException("File modello troncato dentro TAR");
            if (n == 0) continue;
            out.write(buffer, 0, n);
            remaining -= n;
        }
    }
    private static void discardExact(InputStream in, long bytes) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long remaining = bytes;
        while (remaining > 0) {
            int n = in.read(buffer, 0, (int)Math.min(buffer.length, remaining));
            if (n < 0) throw new IOException("Archivio TAR troncato durante avanzamento");
            if (n == 0) continue;
            remaining -= n;
        }
    }
}
