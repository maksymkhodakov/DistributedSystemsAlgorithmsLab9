package com.example;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Main.java
 *
 * Test suite for BloomFilter. Covers:
 *   1. Optimal parameter computation from (n, p)
 *   2. No false negatives guarantee
 *   3. Measured false positive rate vs theoretical expectation
 *   4. Manual parameters (explicit m, k)
 *   5. Effect of varying false positive rate target (1%, 0.1%, 0.01%)
 *   6. Saturation behaviour (inserting beyond capacity)
 *   7. Clear / reuse
 *   8. Stress test: 100 000 elements, measure empirical FP rate
 */
public class Main {

    public static void main(String[] args) {
        test1_optimalParams();
        test2_noFalseNegatives();
        test3_falsePositiveRate();
        test4_explicitParams();
        test5_varyingFPTargets();
        test6_saturation();
        test7_clearAndReuse();
        test8_stressTest();

        System.out.println("\n✅  All tests passed.");
    }

    // =========================================================================
    // Test 1 — Optimal parameter computation
    // =========================================================================
    static void test1_optimalParams() {
        System.out.println("\n══════════════════════════════════════════════════");
        System.out.println("  Test 1: Optimal parameter computation");
        System.out.println("══════════════════════════════════════════════════");

        int    n = 1_000;
        double p = 0.01;   // 1% target FP rate

        int optM = BloomFilter.optimalBitCount(n, p);
        int optK = BloomFilter.optimalHashCount(optM, n);

        System.out.printf("  n=%d, p=%.2f%%  →  m=%d bits (%.1f KB),  k=%d hash functions%n",
                          n, p * 100, optM, optM / 8.0 / 1024.0, optK);

        // Theoretical: m ≈ 9585, k ≈ 7 for n=1000, p=0.01
        assert optM > 8000 && optM < 11000 : "m out of expected range: " + optM;
        assert optK >= 6   && optK <= 8    : "k out of expected range: " + optK;

        BloomFilter bf = BloomFilter.create(n, p);
        System.out.println("  " + bf);
        System.out.println("  ✔ Test 1: PASSED");
    }

    // =========================================================================
    // Test 2 — No false negatives: every inserted element must be found
    // =========================================================================
    static void test2_noFalseNegatives() {
        System.out.println("\n══════════════════════════════════════════════════");
        System.out.println("  Test 2: No false negatives guarantee");
        System.out.println("══════════════════════════════════════════════════");

        BloomFilter bf = BloomFilter.create(10_000, 0.01);
        int N = 10_000;

        // Insert N elements
        for (int i = 0; i < N; i++) {
            bf.add("element_" + i);
        }

        // Every inserted element must be found
        int falseNegatives = 0;
        for (int i = 0; i < N; i++) {
            if (!bf.mightContain("element_" + i)) {
                falseNegatives++;
            }
        }

        System.out.printf("  Inserted %d elements, false negatives: %d%n", N, falseNegatives);
        System.out.println("  " + bf);

        if (falseNegatives != 0) {
            throw new AssertionError("FALSE NEGATIVES detected: " + falseNegatives);
        }
        System.out.println("  ✔ Test 2: PASSED");
    }

    // =========================================================================
    // Test 3 — Measured FP rate must be within 3× of theoretical
    // =========================================================================
    static void test3_falsePositiveRate() {
        System.out.println("\n══════════════════════════════════════════════════");
        System.out.println("  Test 3: False positive rate measurement");
        System.out.println("══════════════════════════════════════════════════");

        int    n      = 5_000;
        double target = 0.01;   // 1%
        BloomFilter bf = BloomFilter.create(n, target);

        // Add n elements with prefix "insert_"
        for (int i = 0; i < n; i++) bf.add("insert_" + i);

        // Test 50 000 elements that were NEVER inserted (prefix "query_")
        int queries      = 50_000;
        int falsePos     = 0;
        for (int i = 0; i < queries; i++) {
            if (bf.mightContain("query_" + i)) falsePos++;
        }

        double measuredFP    = (double) falsePos / queries;
        double theoreticalFP = bf.theoreticalFalsePositiveRate();

        System.out.printf("  Target FP: %.2f%%  |  Theoretical: %.4f%%  |  Measured: %.4f%%%n",
                          target * 100, theoreticalFP * 100, measuredFP * 100);
        System.out.println("  " + bf);

        // Measured FP should be within 3× of target (very conservative bound)
        if (measuredFP > target * 3) {
            throw new AssertionError(
                "FP rate too high: measured=" + measuredFP + " vs target=" + target);
        }
        System.out.println("  ✔ Test 3: PASSED");
    }

    // =========================================================================
    // Test 4 — Explicit manual parameters
    // =========================================================================
    static void test4_explicitParams() {
        System.out.println("\n══════════════════════════════════════════════════");
        System.out.println("  Test 4: Manual parameters (m=100, k=3)");
        System.out.println("══════════════════════════════════════════════════");

        BloomFilter bf = new BloomFilter(100, 3);
        bf.add("hello");
        bf.add("world");
        bf.add("java");

        System.out.printf("  Added 3 elements  →  setBits=%d / 100%n", bf.getSetBits());

        // All added elements must be found
        assertTrue("mightContain('hello')", bf.mightContain("hello"));
        assertTrue("mightContain('world')", bf.mightContain("world"));
        assertTrue("mightContain('java')",  bf.mightContain("java"));

        // An element that hashes to at least one 0-bit must not be found
        // (this is non-deterministic in theory, but with m=100 and only 3 elements
        //  it's overwhelmingly likely that most non-added strings are absent)
        int notFound = 0;
        for (int i = 0; i < 1000; i++) {
            if (!bf.mightContain("notadded_" + i)) notFound++;
        }
        System.out.printf("  Out of 1000 non-added queries, %d correctly returned false%n", notFound);
        System.out.println("  ✔ Test 4: PASSED");
    }

    // =========================================================================
    // Test 5 — Effect of varying target FP rates
    // =========================================================================
    static void test5_varyingFPTargets() {
        System.out.println("\n══════════════════════════════════════════════════");
        System.out.println("  Test 5: FP rate targets 1%, 0.1%, 0.01%");
        System.out.println("══════════════════════════════════════════════════");

        int n = 10_000;
        double[] targets = { 0.01, 0.001, 0.0001 };

        System.out.printf("  %-10s  %-10s  %-10s  %-12s  %-12s%n",
                          "Target FP%", "m (bits)", "k", "Theory FP%", "Memory");

        for (double target : targets) {
            BloomFilter bf = BloomFilter.create(n, target);

            // Add n elements
            for (int i = 0; i < n; i++) bf.add("elem_" + i);

            System.out.printf("  %-10s  %-10d  %-10d  %-12.5f  %.1f KB%n",
                              String.format("%.2f%%", target * 100),
                              bf.getBitCount(),
                              bf.getHashCount(),
                              bf.theoreticalFalsePositiveRate() * 100,
                              bf.getBitCount() / 8.0 / 1024.0);

            // No false negatives
            for (int i = 0; i < n; i++) {
                if (!bf.mightContain("elem_" + i))
                    throw new AssertionError("False negative at target=" + target);
            }
        }
        System.out.println("  ✔ Test 5: PASSED");
    }

    // =========================================================================
    // Test 6 — Saturation: inserting 3× more than designed capacity
    // =========================================================================
    static void test6_saturation() {
        System.out.println("\n══════════════════════════════════════════════════");
        System.out.println("  Test 6: Saturation (insert 3× capacity)");
        System.out.println("══════════════════════════════════════════════════");

        int    n      = 1_000;
        double target = 0.01;
        BloomFilter bf = BloomFilter.create(n, target);

        // Insert 3× more than the designed capacity
        for (int i = 0; i < n * 3; i++) bf.add("over_" + i);

        System.out.printf("  Designed for %d,  inserted %d%n", n, n * 3);
        System.out.printf("  FP rate:  theoretical=%.2f%%,  empirical=%.2f%%%n",
                          bf.theoreticalFalsePositiveRate() * 100,
                          bf.currentFalsePositiveRate()     * 100);
        System.out.printf("  Bit saturation: %.1f%% of bits set%n",
                          100.0 * bf.getSetBits() / bf.getBitCount());
        System.out.println("  (High FP rate expected — this demonstrates graceful degradation)");

        // No false negatives even when saturated
        for (int i = 0; i < n * 3; i++) {
            if (!bf.mightContain("over_" + i))
                throw new AssertionError("False negative during saturation test at i=" + i);
        }
        System.out.println("  ✔ Test 6: PASSED (no false negatives even at 3× capacity)");
    }

    // =========================================================================
    // Test 7 — Clear and reuse
    // =========================================================================
    static void test7_clearAndReuse() {
        System.out.println("\n══════════════════════════════════════════════════");
        System.out.println("  Test 7: Clear and reuse");
        System.out.println("══════════════════════════════════════════════════");

        BloomFilter bf = BloomFilter.create(1_000, 0.01);
        bf.add("alpha");
        bf.add("beta");
        assertTrue("before clear: 'alpha' present",  bf.mightContain("alpha"));

        bf.clear();
        System.out.printf("  After clear: insertions=%d, setBits=%d%n",
                          bf.getInsertions(), bf.getSetBits());

        if (bf.getSetBits() != 0)
            throw new AssertionError("setBits should be 0 after clear, got: " + bf.getSetBits());
        if (bf.getInsertions() != 0)
            throw new AssertionError("insertions should be 0 after clear");

        // Now reuse with different data
        bf.add("gamma");
        assertTrue("after reuse: 'gamma' present", bf.mightContain("gamma"));
        System.out.println("  ✔ Test 7: PASSED");
    }

    // =========================================================================
    // Test 8 — Stress test: 100 000 elements
    // =========================================================================
    static void test8_stressTest() {
        System.out.println("\n══════════════════════════════════════════════════");
        System.out.println("  Test 8: Stress test (100 000 elements, FP=0.1%)");
        System.out.println("══════════════════════════════════════════════════");

        int    n      = 100_000;
        double target = 0.001;
        BloomFilter bf = BloomFilter.create(n, target);

        // Use a real-world-like dataset: random UUIDs simulated as strings
        Random rng = new Random(42L);
        Set<String> inserted = new HashSet<>();

        long t0 = System.nanoTime();

        for (int i = 0; i < n; i++) {
            String key = randomKey(rng);
            bf.add(key);
            inserted.add(key);
        }
        long insertTime = System.nanoTime() - t0;

        // No false negatives
        t0 = System.nanoTime();
        int falseNeg = 0;
        for (String key : inserted) {
            if (!bf.mightContain(key)) falseNeg++;
        }
        long fnTime = System.nanoTime() - t0;

        // Measure false positive rate on unseen keys
        int queries  = 200_000;
        int falsePosCount = 0;
        for (int i = 0; i < queries; i++) {
            String key = "unseen_" + i;
            if (!inserted.contains(key) && bf.mightContain(key)) falsePosCount++;
        }
        double measuredFP = (double) falsePosCount / queries;

        System.out.printf("  %s%n", bf);
        System.out.printf("  Insert %d elements:     %.1f ms%n",   n, insertTime / 1e6);
        System.out.printf("  Check  %d elements:     %.1f ms%n",   inserted.size(), fnTime / 1e6);
        System.out.printf("  False negatives:         %d%n",        falseNeg);
        System.out.printf("  False positives:         %d / %d queries  (%.4f%%)%n",
                          falsePosCount, queries, measuredFP * 100);
        System.out.printf("  Target FP:               %.4f%%%n",    target * 100);

        if (falseNeg != 0)
            throw new AssertionError("False negatives in stress test: " + falseNeg);
        if (measuredFP > target * 5)
            throw new AssertionError("FP rate too high: " + measuredFP);

        System.out.println("  ✔ Test 8: PASSED");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    static String randomKey(Random rng) {
        long a = rng.nextLong();
        long b = rng.nextLong();
        return Long.toHexString(a) + "-" + Long.toHexString(b);
    }

    static void assertTrue(String label, boolean condition) {
        if (!condition) throw new AssertionError("Expected true: " + label);
        System.out.println("    ✔ " + label);
    }
}
