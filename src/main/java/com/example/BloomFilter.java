package com.example;

import java.nio.charset.StandardCharsets;
import java.util.BitSet;

/**
 * BloomFilter.java
 *
 * A space-efficient probabilistic data structure that answers membership queries
 * of the form "is element x in the set?" with the following guarantees:
 *
 *   ✓ NO false negatives: if x was added, mightContain(x) always returns true.
 *   ✗ FALSE POSITIVES possible: mightContain(x) may return true for x never added,
 *     with probability ≈ (1 − e^(−k·n/m))^k  where n = items added, m = bit array
 *     size, k = number of hash functions.
 *
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │ How a Bloom filter works                                                │
 * │                                                                         │
 * │  Bit array (m bits), all 0 initially:                                   │
 * │  [ 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 ]   (m = 16 for illustration)      │
 * │                                                                         │
 * │  add("hello") with k=3 hash functions → positions {2, 7, 13}:          │
 * │  [ 0 0 1 0 0 0 0 1 0 0 0 0 0 1 0 0 ]                                  │
 * │                                                                         │
 * │  add("world") with k=3 → positions {1, 7, 11}:                         │
 * │  [ 0 1 1 0 0 0 0 1 0 0 0 1 0 1 0 0 ]                                  │
 * │                                                                         │
 * │  mightContain("hello") → check bits {2,7,13}: all 1 → TRUE  (correct)  │
 * │  mightContain("java")  → check bits {3,7,9}:  bit 3 = 0 → FALSE        │
 * │  mightContain("test")  → check bits {1,7,11}: all 1 → TRUE (false pos!)│
 * └─────────────────────────────────────────────────────────────────────────┘
 *
 * Optimal parameters for a desired false positive rate p given n expected items:
 *   m = -n * ln(p) / (ln 2)²       (optimal bit array size)
 *   k =  m / n * ln 2              (optimal number of hash functions)
 *
 * This class provides a factory method {@link #create(int, double)} that
 * automatically computes optimal m and k from (expectedInsertions, falsePositiveRate).
 *
 * Thread safety: this implementation is NOT thread-safe.
 */
public class BloomFilter {

    // ── Internal state ────────────────────────────────────────────────────────

    /** The bit array: m bits, all initialised to 0. */
    private final BitSet bits;

    /** Number of bits in the bit array (m). */
    private final int bitCount;

    /** Number of independent hash functions (k). */
    private final int hashCount;

    /** Number of elements added so far. */
    private int insertions;

    // ── Constructors ──────────────────────────────────────────────────────────

    /**
     * Creates a Bloom filter with explicit parameters.
     *
     * @param bitCount   size of the bit array in bits (m). Must be > 0.
     * @param hashCount  number of hash functions (k). Must be ≥ 1.
     */
    public BloomFilter(int bitCount, int hashCount) {
        if (bitCount  < 1) throw new IllegalArgumentException("bitCount must be > 0");
        if (hashCount < 1) throw new IllegalArgumentException("hashCount must be >= 1");
        this.bitCount  = bitCount;
        this.hashCount = hashCount;
        this.bits      = new BitSet(bitCount);
        this.insertions = 0;
    }

    // ── Factory method ────────────────────────────────────────────────────────

    /**
     * Creates a Bloom filter sized for a target false-positive rate.
     *
     * Computes optimal m and k from:
     *   m = ceil( -n * ln(p) / (ln 2)² )
     *   k = round( m / n * ln 2 )
     *
     * @param expectedInsertions  expected number of elements to add (n). Must be > 0.
     * @param falsePositiveRate   desired probability of a false positive (p). Must be in (0, 1).
     * @return                    a Bloom filter with optimal parameters.
     */
    public static BloomFilter create(int expectedInsertions, double falsePositiveRate) {
        if (expectedInsertions <= 0)
            throw new IllegalArgumentException("expectedInsertions must be > 0");
        if (falsePositiveRate <= 0.0 || falsePositiveRate >= 1.0)
            throw new IllegalArgumentException("falsePositiveRate must be in (0, 1)");

        // Optimal bit array size
        int    m = optimalBitCount(expectedInsertions, falsePositiveRate);
        // Optimal number of hash functions
        int    k = optimalHashCount(m, expectedInsertions);

        return new BloomFilter(m, k);
    }

    // ── Core operations ───────────────────────────────────────────────────────

    /**
     * Adds an element to the filter.
     *
     * For each of the k hash functions h_i, computes the bit position
     *   pos_i = |h1(x) + i * h2(x)| mod m
     * and sets bit[pos_i] to 1.
     *
     * After adding n elements the filter guarantees that mightContain(x)
     * returns true for every added x (no false negatives).
     *
     * @param element  the string element to add (UTF-8 encoded internally)
     */
    public void add(String element) {
        byte[] data = element.getBytes(StandardCharsets.UTF_8);
        long h1 = HashUtils.hash1(data);
        long h2 = HashUtils.hash2(data);

        for (int i = 0; i < hashCount; i++) {
            // Double hashing: h_i(x) = h1(x) + i * h2(x)
            long combined = h1 + (long) i * h2;
            // Map to [0, m) — use Math.floorMod to handle negative values correctly
            int pos = (int) Math.floorMod(combined, bitCount);
            bits.set(pos);
        }
        insertions++;
    }

    /**
     * Tests whether an element MIGHT be in the set.
     *
     * Returns false  → element was DEFINITELY NOT added (0% false negative rate).
     * Returns true   → element was PROBABLY added (small false positive rate).
     *
     * Uses the same double-hashing positions as {@link #add(String)}.
     *
     * @param element  element to test
     * @return         false if definitely absent; true if probably present
     */
    public boolean mightContain(String element) {
        byte[] data = element.getBytes(StandardCharsets.UTF_8);
        long h1 = HashUtils.hash1(data);
        long h2 = HashUtils.hash2(data);

        for (int i = 0; i < hashCount; i++) {
            long combined = h1 + (long) i * h2;
            int pos = (int) Math.floorMod(combined, bitCount);
            if (!bits.get(pos)) {
                return false;   // definitively absent: at least one bit is 0
            }
        }
        return true;   // all k bits are 1 → probably present
    }

    // ── Statistics & diagnostics ──────────────────────────────────────────────

    /**
     * Returns the current (empirical) false-positive probability estimate,
     * computed from the number of bits currently set in the array.
     *
     * Formula:  p ≈ (setBits / m) ^ k
     *
     * This converges to the theoretical value (1 − e^(−kn/m))^k as n grows.
     */
    public double currentFalsePositiveRate() {
        int setBits = bits.cardinality();
        double saturation = (double) setBits / bitCount;   // fraction of bits set
        return Math.pow(saturation, hashCount);
    }

    /**
     * Returns the theoretical false-positive probability given n insertions:
     *
     *   p_theory = (1 − e^(−k·n/m))^k
     */
    public double theoreticalFalsePositiveRate() {
        double exponent = -((double) hashCount * insertions) / bitCount;
        return Math.pow(1.0 - Math.exp(exponent), hashCount);
    }

    /** Number of bits in the bit array (m). */
    public int getBitCount()  { return bitCount; }

    /** Number of hash functions (k). */
    public int getHashCount() { return hashCount; }

    /** Number of elements added. */
    public int getInsertions() { return insertions; }

    /** Number of bits currently set to 1. */
    public int getSetBits() { return bits.cardinality(); }

    /**
     * Resets the filter to its initial empty state.
     * All bits cleared, insertion counter reset to 0.
     */
    public void clear() {
        bits.clear();
        insertions = 0;
    }

    /**
     * Returns a human-readable summary of the filter's state.
     */
    @Override
    public String toString() {
        return String.format(
            "BloomFilter{m=%d bits (%.1f KB), k=%d, n=%d, setBits=%d (%.1f%%), " +
            "theoretical FP=%.4f%%, empirical FP=%.4f%%}",
            bitCount,
            bitCount / 8.0 / 1024.0,
            hashCount,
            insertions,
            getSetBits(),
            100.0 * getSetBits() / bitCount,
            theoreticalFalsePositiveRate() * 100.0,
            currentFalsePositiveRate()     * 100.0
        );
    }

    // ── Optimal parameter formulas ────────────────────────────────────────────

    /**
     * Optimal bit array size:
     *   m = ceil( -n * ln(p) / (ln 2)² )
     */
    static int optimalBitCount(int n, double p) {
        double ln2squared = Math.log(2) * Math.log(2);
        return (int) Math.ceil(-n * Math.log(p) / ln2squared);
    }

    /**
     * Optimal number of hash functions:
     *   k = round( m / n * ln 2 )
     */
    static int optimalHashCount(int m, int n) {
        return Math.max(1, (int) Math.round((double) m / n * Math.log(2)));
    }
}
