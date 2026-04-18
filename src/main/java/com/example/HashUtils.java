package com.example;

/**
 * HashUtils.java
 *
 * Provides two independent, high-quality hash functions for arbitrary byte arrays.
 *
 * The double hashing trick allows generating k independent hash functions cheaply:
 *
 *   h_i(x) = ( h1(x) + i * h2(x) ) mod m      i = 0, 1, …, k-1
 *
 * This avoids storing k separate functions and has been shown empirically to produce
 * false positive rates very close to the theoretical optimum (Kirsch & Mitzenmacher, 2006).
 *
 * Both h1 and h2 are based on the Murmur3-inspired mix — a fast, non-cryptographic,
 * bit-avalanche hash that distributes bits uniformly.
 */
public final class HashUtils {

    private HashUtils() {}   // utility class — no instances

    // -------------------------------------------------------------------------
    // Public API: two independent hash values for a key
    // -------------------------------------------------------------------------

    /**
     * Computes hash1(data) — first independent hash value.
     * Seed is fixed at 0xDEADBEEF.
     *
     * @param data  raw bytes of the element being hashed
     * @return      signed 64-bit hash (caller takes mod m)
     */
    public static long hash1(byte[] data) {
        return murmur64(data, 0xDEADBEEFL);
    }

    /**
     * Computes hash2(data) — second independent hash value.
     * Seed is fixed at 0xCAFEBABE (different from hash1 → independence).
     *
     * @param data  raw bytes of the element being hashed
     * @return      signed 64-bit hash (caller takes mod m)
     */
    public static long hash2(byte[] data) {
        return murmur64(data, 0xCAFEBABEL);
    }

    // -------------------------------------------------------------------------
    // Murmur3-inspired 64-bit hash
    // -------------------------------------------------------------------------

    /**
     * Non-cryptographic 64-bit hash — Murmur3 mix applied to each 8-byte block.
     *
     * Properties:
     *   - Deterministic (same input + seed → same output, always).
     *   - Fast: only shifts, XORs, and multiplications.
     *   - Good avalanche: every input bit affects ~half the output bits.
     *
     * @param data  bytes to hash
     * @param seed  distinguishes h1 from h2 when called on the same data
     * @return      64-bit hash value
     */
    private static long murmur64(byte[] data, long seed) {
        final long C1 = 0xff51afd7ed558ccdL;
        final long C2 = 0xc4ceb9fe1a85ec53L;

        long h = seed ^ (long) data.length;

        // Process 8 bytes at a time
        int i = 0;
        for (; i + 8 <= data.length; i += 8) {
            long block = readLongLE(data, i);  // read little-endian 8-byte word
            block *= C1;
            block  = Long.rotateLeft(block, 31);
            block *= C2;
            h ^= block;
            h  = Long.rotateLeft(h, 27) * 5 + 0x52dce729L;
        }

        // Process remaining bytes (0–7)
        long tail = 0L;
        for (int shift = 0; i < data.length; i++, shift += 8) {
            tail |= ((long)(data[i] & 0xFF)) << shift;
        }
        if (tail != 0) {
            tail *= C1;
            tail  = Long.rotateLeft(tail, 31);
            tail *= C2;
            h ^= tail;
        }

        // Final mix (fmix64) — ensures full avalanche
        h ^= (long) data.length;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;

        return h;
    }

    /**
     * Reads 8 bytes from data[offset..offset+7] as a little-endian long.
     */
    private static long readLongLE(byte[] data, int offset) {
        long v = 0L;
        for (int i = 0; i < 8; i++) {
            v |= ((long)(data[offset + i] & 0xFF)) << (8 * i);
        }
        return v;
    }
}
