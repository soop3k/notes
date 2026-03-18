package com.benchmark;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Oracle Read Performance Benchmark
 *
 * Compares read performance between:
 *   1) A single wide table with 100 columns -- always reads ALL columns (SELECT *)
 *   2) Three normalized tables (same data split across 3 tables) -- reads only
 *      NEEDED columns (10 cols) via a single JOIN of 2 tables.
 *
 * The point: with a wide table you must read everything, but with normalized
 * tables you only JOIN the tables that have the columns you need.
 */
public class OracleBenchmark {

    // Configuration
    private static final int NUM_COLUMNS = 100;
    private static final int NORM_SELECT_COLS = 10;
    private static int numRows = 10_000;
    private static int iterations = 10;
    private static boolean fetchAll = true;

    // Table split: 34 + 33 + 33 = 100
    private static final int[] SPLIT = {34, 33, 33};

    // Table/view names
    private static final String SINGLE_TABLE = "BENCH_SINGLE";
    private static final String SINGLE_VIEW = "BENCH_SINGLE_V";
    private static final String[] MULTI_TABLES = {"BENCH_MULTI_A", "BENCH_MULTI_B", "BENCH_MULTI_C"};
    private static final String MULTI_VIEW = "BENCH_MULTI_V";

    private static final Random RANDOM = new Random();
    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private static String randomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    private static String colName(int index) {
        return String.format("COL_%03d", index);
    }

    private static void dropIfExists(Statement stmt, String objType, String name) {
        try {
            if ("TABLE".equals(objType)) {
                stmt.execute("DROP " + objType + " " + name + " PURGE");
            } else {
                stmt.execute("DROP " + objType + " " + name);
            }
        } catch (SQLException ignored) {
            // Object doesn't exist
        }
    }

    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println("  " + title);
        System.out.println("=".repeat(70));
    }

    /**
     * Returns (start, end) column index ranges for each of the 3 tables.
     */
    private static int[][] colRanges() {
        int s1 = 1, e1 = SPLIT[0];
        int s2 = e1 + 1, e2 = e1 + SPLIT[1];
        int s3 = e2 + 1, e3 = e2 + SPLIT[2];
        return new int[][]{{s1, e1}, {s2, e2}, {s3, e3}};
    }

    // ---------------------------------------------------------------------------
    // Result formatting
    // ---------------------------------------------------------------------------

    private static class BenchmarkResult {
        final String pattern;
        final double min;
        final double max;
        final double avg;
        final double median;
        final double stdev;

        BenchmarkResult(String pattern, List<Double> timings) {
            this.pattern = pattern;
            DoubleSummaryStatistics stats = timings.stream()
                    .mapToDouble(Double::doubleValue)
                    .summaryStatistics();
            this.min = stats.getMin();
            this.max = stats.getMax();
            this.avg = stats.getAverage();

            List<Double> sorted = timings.stream().sorted().collect(Collectors.toList());
            int n = sorted.size();
            this.median = (n % 2 == 0)
                    ? (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0
                    : sorted.get(n / 2);

            double variance = timings.stream()
                    .mapToDouble(t -> (t - this.avg) * (t - this.avg))
                    .sum() / (n - 1);
            this.stdev = (n > 1) ? Math.sqrt(variance) : 0.0;
        }
    }

    private static void formatResults(List<BenchmarkResult> results) {
        // Calculate column widths
        int patternWidth = Math.max(7, results.stream()
                .mapToInt(r -> r.pattern.length())
                .max().orElse(7));

        String headerFmt = "| %-" + patternWidth + "s | %9s | %9s | %9s | %11s | %10s |%n";
        String rowFmt = "| %-" + patternWidth + "s | %9.4f | %9.4f | %9.4f | %11.4f | %10.4f |%n";
        String sep = "+" + "-".repeat(patternWidth + 2) + "+"
                + "-".repeat(11) + "+" + "-".repeat(11) + "+"
                + "-".repeat(11) + "+" + "-".repeat(13) + "+"
                + "-".repeat(12) + "+";

        System.out.println(sep);
        System.out.printf(headerFmt, "Pattern", "Min (s)", "Max (s)", "Avg (s)", "Median (s)", "Stdev (s)");
        System.out.println(sep);
        for (BenchmarkResult r : results) {
            System.out.printf(rowFmt, r.pattern, r.min, r.max, r.avg, r.median, r.stdev);
            System.out.println(sep);
        }
    }

    private static void formatComparison(List<BenchmarkResult> singleResults,
                                         List<BenchmarkResult> multiResults) {
        String sep = "+" + "-".repeat(27) + "+" + "-".repeat(18) + "+"
                + "-".repeat(27) + "+" + "-".repeat(22) + "+"
                + "-".repeat(21) + "+" + "-".repeat(21) + "+";

        System.out.println(sep);
        System.out.printf("| %-25s | %16s | %-25s | %20s | %19s | %-19s |%n",
                "Single-Table Pattern", "Single Avg (s)", "Normalized Pattern",
                "Normalized Avg (s)", "Norm / Wide Ratio", "Verdict");
        System.out.println(sep);

        for (int i = 0; i < singleResults.size() && i < multiResults.size(); i++) {
            BenchmarkResult s = singleResults.get(i);
            BenchmarkResult m = multiResults.get(i);
            double ratio = (s.avg > 0) ? m.avg / s.avg : Double.POSITIVE_INFINITY;
            String verdict;
            if (ratio > 1.05) {
                verdict = "Normalized slower";
            } else if (ratio > 0.95) {
                verdict = "Similar";
            } else {
                verdict = "Normalized faster";
            }
            System.out.printf("| %-25s | %16.4f | %-25s | %20.4f | %19.4f | %-19s |%n",
                    s.pattern, s.avg, m.pattern, m.avg, ratio, verdict);
            System.out.println(sep);
        }
    }

    // ---------------------------------------------------------------------------
    // Single wide table
    // ---------------------------------------------------------------------------

    private static void createSingleTable(Statement stmt) throws SQLException {
        dropIfExists(stmt, "VIEW", SINGLE_VIEW);
        dropIfExists(stmt, "TABLE", SINGLE_TABLE);

        StringBuilder cols = new StringBuilder();
        StringBuilder allCols = new StringBuilder();
        for (int i = 1; i <= NUM_COLUMNS; i++) {
            if (i > 1) {
                cols.append(", ");
                allCols.append(", ");
            }
            cols.append(colName(i)).append(" VARCHAR2(50)");
            allCols.append(colName(i));
        }

        stmt.execute("CREATE TABLE " + SINGLE_TABLE + " (ID NUMBER PRIMARY KEY, " + cols + ")");
        stmt.execute("CREATE VIEW " + SINGLE_VIEW + " AS SELECT ID, " + allCols + " FROM " + SINGLE_TABLE);

        System.out.println("  Created table  " + SINGLE_TABLE + " with " + NUM_COLUMNS + " columns");
        System.out.println("  Created view   " + SINGLE_VIEW + " (all " + NUM_COLUMNS + " columns)");
    }

    private static void insertSingleTable(Connection conn, Statement stmt) throws SQLException {
        StringBuilder colNames = new StringBuilder();
        StringBuilder placeholders = new StringBuilder();
        for (int i = 1; i <= NUM_COLUMNS; i++) {
            if (i > 1) {
                colNames.append(", ");
                placeholders.append(", ");
            }
            colNames.append(colName(i));
            placeholders.append("?");
        }

        String sql = "INSERT INTO " + SINGLE_TABLE + " (ID, " + colNames + ") VALUES (?, " + placeholders + ")";
        int batchSize = 1000;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int start = 0; start < numRows; start += batchSize) {
                int end = Math.min(start + batchSize, numRows);
                for (int rowId = start + 1; rowId <= end; rowId++) {
                    ps.setInt(1, rowId);
                    for (int c = 1; c <= NUM_COLUMNS; c++) {
                        ps.setString(c + 1, randomString(20));
                    }
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
        conn.commit();
        System.out.println("  Inserted " + numRows + " rows into " + SINGLE_TABLE);
    }

    private static List<BenchmarkResult> benchmarkSingleTable(Connection conn) throws SQLException {
        String allCols = IntStream.rangeClosed(1, NUM_COLUMNS)
                .mapToObj(OracleBenchmark::colName)
                .collect(Collectors.joining(", "));

        Map<String, String> queries = new LinkedHashMap<>();
        queries.put("SELECT (all " + NUM_COLUMNS + " cols)",
                "SELECT ID, " + allCols + " FROM " + SINGLE_TABLE);
        queries.put("VIEW   (all " + NUM_COLUMNS + " cols)",
                "SELECT * FROM " + SINGLE_VIEW);
        queries.put("SELECT *",
                "SELECT * FROM " + SINGLE_TABLE);

        return runBenchmark(conn, queries);
    }

    // ---------------------------------------------------------------------------
    // Normalized tables (3 tables, read via 1 JOIN of 2 tables)
    // ---------------------------------------------------------------------------

    private static void createMultiTables(Statement stmt) throws SQLException {
        dropIfExists(stmt, "VIEW", MULTI_VIEW);
        for (String tbl : MULTI_TABLES) {
            dropIfExists(stmt, "TABLE", tbl);
        }

        int[][] ranges = colRanges();
        for (int t = 0; t < MULTI_TABLES.length; t++) {
            int start = ranges[t][0];
            int end = ranges[t][1];
            StringBuilder cols = new StringBuilder();
            for (int i = start; i <= end; i++) {
                if (i > start) cols.append(", ");
                cols.append(colName(i)).append(" VARCHAR2(50)");
            }
            stmt.execute("CREATE TABLE " + MULTI_TABLES[t]
                    + " (ID NUMBER PRIMARY KEY, " + cols + ")");
            System.out.printf("  Created table %s with %d columns (COL_%03d..COL_%03d)%n",
                    MULTI_TABLES[t], end - start + 1, start, end);
        }

        // Create a view that JOINs only 2 tables (A and B)
        int[][] r = colRanges();
        int aStart = r[0][0];
        int bStart = r[1][0];
        int half = NORM_SELECT_COLS / 2;

        String viewACols = IntStream.range(aStart, aStart + half)
                .mapToObj(i -> "a." + colName(i))
                .collect(Collectors.joining(", "));
        String viewBCols = IntStream.range(bStart, bStart + (NORM_SELECT_COLS - half))
                .mapToObj(i -> "b." + colName(i))
                .collect(Collectors.joining(", "));

        stmt.execute("CREATE VIEW " + MULTI_VIEW + " AS SELECT a.ID, " + viewACols + ", " + viewBCols
                + " FROM " + MULTI_TABLES[0] + " a JOIN " + MULTI_TABLES[1] + " b ON a.ID = b.ID");
        System.out.println("  Created view  " + MULTI_VIEW + " (2-table JOIN, " + NORM_SELECT_COLS + " cols)");
    }

    private static void insertMultiTables(Connection conn) throws SQLException {
        int[][] ranges = colRanges();
        int batchSize = 1000;

        // Prepare statements for each table
        PreparedStatement[] psList = new PreparedStatement[MULTI_TABLES.length];
        for (int t = 0; t < MULTI_TABLES.length; t++) {
            int start = ranges[t][0];
            int end = ranges[t][1];
            int numCols = end - start + 1;
            StringBuilder colNames = new StringBuilder();
            StringBuilder placeholders = new StringBuilder();
            for (int i = start; i <= end; i++) {
                if (i > start) {
                    colNames.append(", ");
                    placeholders.append(", ");
                }
                colNames.append(colName(i));
                placeholders.append("?");
            }
            psList[t] = conn.prepareStatement(
                    "INSERT INTO " + MULTI_TABLES[t] + " (ID, " + colNames + ") VALUES (?, " + placeholders + ")");
        }

        for (int startRow = 0; startRow < numRows; startRow += batchSize) {
            int endRow = Math.min(startRow + batchSize, numRows);
            for (int rowId = startRow + 1; rowId <= endRow; rowId++) {
                // Generate all 100 column values for this row
                String[] allColValues = new String[NUM_COLUMNS];
                for (int c = 0; c < NUM_COLUMNS; c++) {
                    allColValues[c] = randomString(20);
                }

                for (int t = 0; t < MULTI_TABLES.length; t++) {
                    int start = ranges[t][0];
                    int end = ranges[t][1];
                    psList[t].setInt(1, rowId);
                    for (int i = start; i <= end; i++) {
                        psList[t].setString(i - start + 2, allColValues[i - 1]);
                    }
                    psList[t].addBatch();
                }
            }
            for (PreparedStatement ps : psList) {
                ps.executeBatch();
            }
        }
        conn.commit();

        for (PreparedStatement ps : psList) {
            ps.close();
        }
        System.out.println("  Inserted " + numRows + " rows into each of "
                + String.join(", ", MULTI_TABLES));
    }

    private static List<BenchmarkResult> benchmarkMultiTables(Connection conn) throws SQLException {
        int[][] ranges = colRanges();
        int aStart = ranges[0][0];
        int bStart = ranges[1][0];
        int half = NORM_SELECT_COLS / 2;

        String aCols = IntStream.range(aStart, aStart + half)
                .mapToObj(i -> "a." + colName(i))
                .collect(Collectors.joining(", "));
        String bCols = IntStream.range(bStart, bStart + (NORM_SELECT_COLS - half))
                .mapToObj(i -> "b." + colName(i))
                .collect(Collectors.joining(", "));
        String selectCols = aCols + ", " + bCols;

        String joinClause = "FROM " + MULTI_TABLES[0] + " a JOIN " + MULTI_TABLES[1] + " b ON a.ID = b.ID";

        Map<String, String> queries = new LinkedHashMap<>();
        queries.put("SELECT (" + NORM_SELECT_COLS + " cols) + JOIN",
                "SELECT a.ID, " + selectCols + " " + joinClause);
        queries.put("VIEW   (" + NORM_SELECT_COLS + " cols) + JOIN",
                "SELECT * FROM " + MULTI_VIEW);
        queries.put("SELECT * + JOIN",
                "SELECT * " + joinClause);

        return runBenchmark(conn, queries);
    }

    // ---------------------------------------------------------------------------
    // Common benchmark runner
    // ---------------------------------------------------------------------------

    private static List<BenchmarkResult> runBenchmark(Connection conn,
                                                      Map<String, String> queries) throws SQLException {
        List<BenchmarkResult> results = new ArrayList<>();

        for (Map.Entry<String, String> entry : queries.entrySet()) {
            String label = entry.getKey();
            String sql = entry.getValue();
            List<Double> timings = new ArrayList<>();

            for (int iter = 0; iter < iterations; iter++) {
                long startTime = System.nanoTime();
                try (Statement stmt = conn.createStatement()) {
                    stmt.setFetchSize(5000);
                    try (ResultSet rs = stmt.executeQuery(sql)) {
                        if (fetchAll) {
                            while (rs.next()) {
                                // consume all rows
                            }
                        }
                    }
                }
                double elapsed = (System.nanoTime() - startTime) / 1_000_000_000.0;
                timings.add(elapsed);
            }

            results.add(new BenchmarkResult(label, timings));
        }

        return results;
    }

    // ---------------------------------------------------------------------------
    // Cleanup
    // ---------------------------------------------------------------------------

    private static void cleanup(Statement stmt) {
        dropIfExists(stmt, "VIEW", SINGLE_VIEW);
        dropIfExists(stmt, "TABLE", SINGLE_TABLE);
        dropIfExists(stmt, "VIEW", MULTI_VIEW);
        for (String tbl : MULTI_TABLES) {
            dropIfExists(stmt, "TABLE", tbl);
        }
        System.out.println("  Cleaned up all benchmark objects.");
    }

    // ---------------------------------------------------------------------------
    // CLI argument parsing
    // ---------------------------------------------------------------------------

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("host", envOrDefault("ORACLE_HOST", "localhost"));
        params.put("port", envOrDefault("ORACLE_PORT", "1521"));
        params.put("service", envOrDefault("ORACLE_SERVICE", "FREEPDB1"));
        params.put("user", envOrDefault("ORACLE_USER", "benchmark"));
        params.put("password", envOrDefault("ORACLE_PASSWORD", "benchmark"));
        params.put("rows", String.valueOf(numRows));
        params.put("iterations", String.valueOf(iterations));
        params.put("no-cleanup", "false");
        params.put("no-fetch", "false");

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--host" -> params.put("host", args[++i]);
                case "--port" -> params.put("port", args[++i]);
                case "--service" -> params.put("service", args[++i]);
                case "--user" -> params.put("user", args[++i]);
                case "--password" -> params.put("password", args[++i]);
                case "--rows" -> params.put("rows", args[++i]);
                case "--iterations" -> params.put("iterations", args[++i]);
                case "--no-cleanup" -> params.put("no-cleanup", "true");
                case "--no-fetch" -> params.put("no-fetch", "true");
                default -> {
                    System.err.println("Unknown argument: " + args[i]);
                    System.exit(1);
                }
            }
        }
        return params;
    }

    private static String envOrDefault(String envVar, String defaultVal) {
        String val = System.getenv(envVar);
        return (val != null && !val.isEmpty()) ? val : defaultVal;
    }

    // ---------------------------------------------------------------------------
    // Main
    // ---------------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        Map<String, String> params = parseArgs(args);

        numRows = Integer.parseInt(params.get("rows"));
        iterations = Integer.parseInt(params.get("iterations"));
        fetchAll = !"true".equals(params.get("no-fetch"));
        boolean noCleanup = "true".equals(params.get("no-cleanup"));

        String jdbcUrl = "jdbc:oracle:thin:@//" + params.get("host") + ":"
                + params.get("port") + "/" + params.get("service");

        printSection("Oracle Benchmark Tool (Java)");
        System.out.println("  Rows: " + numRows + "  |  Iterations: " + iterations + "  |  Fetch: " + fetchAll);
        System.out.println("  Wide table:       reads ALL " + NUM_COLUMNS + " columns");
        System.out.println("  Normalized:       reads only " + NORM_SELECT_COLS + " columns via 1 JOIN (2 tables)");

        try (Connection conn = DriverManager.getConnection(jdbcUrl, params.get("user"), params.get("password"))) {
            conn.setAutoCommit(false);
            Statement stmt = conn.createStatement();

            try {
                // ---- Single Table Benchmark ----
                printSection("Phase 1: Single Table -- read ALL " + NUM_COLUMNS + " columns");
                System.out.println("\n[Setup]");
                createSingleTable(stmt);
                conn.commit();

                System.out.println("\n[Insert Data]");
                long t0 = System.nanoTime();
                insertSingleTable(conn, stmt);
                double insertTime = (System.nanoTime() - t0) / 1_000_000_000.0;
                System.out.printf("  Insert time: %.2fs%n", insertTime);

                System.out.println("\n[Benchmarking Reads]");
                List<BenchmarkResult> singleResults = benchmarkSingleTable(conn);
                formatResults(singleResults);

                // ---- Multi-Table Benchmark ----
                printSection("Phase 2: Normalized Tables -- read only " + NORM_SELECT_COLS + " cols via 1 JOIN");
                System.out.println("\n[Setup]");
                createMultiTables(stmt);
                conn.commit();

                System.out.println("\n[Insert Data]");
                t0 = System.nanoTime();
                insertMultiTables(conn);
                insertTime = (System.nanoTime() - t0) / 1_000_000_000.0;
                System.out.printf("  Insert time: %.2fs%n", insertTime);

                System.out.println("\n[Benchmarking Reads (2-table JOIN, selected cols only)]");
                List<BenchmarkResult> multiResults = benchmarkMultiTables(conn);
                formatResults(multiResults);

                // ---- Comparison ----
                printSection("Comparison: Wide Table (all " + NUM_COLUMNS
                        + " cols) vs Normalized (" + NORM_SELECT_COLS + " cols)");
                formatComparison(singleResults, multiResults);

            } finally {
                if (!noCleanup) {
                    printSection("Cleanup");
                    cleanup(stmt);
                    conn.commit();
                }
                stmt.close();
            }
        }

        printSection("Done");
    }
}
