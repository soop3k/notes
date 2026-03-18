# Oracle Read Performance Benchmark (Java)

Compares read performance of:
- **Single wide table (100 columns)** -- always reads ALL columns (simulates a denormalized design where you must fetch everything)
- **3 normalized tables** -- reads only the NEEDED columns (default: 10) by joining just 2 of the 3 tables (single JOIN)

## Read Patterns Tested

### Single Table (reads ALL 100 columns every time)
| # | Pattern | Description |
|---|---------|-------------|
| 1 | `SELECT (all 100 cols)` | Explicitly selects all 100 columns |
| 2 | `VIEW (all 100 cols)` | Reads through a VIEW over all 100 columns |
| 3 | `SELECT *` | `SELECT *` from the wide table |

### Normalized (reads only 10 columns via 1 JOIN of 2 tables)
| # | Pattern | Description |
|---|---------|-------------|
| 1 | `SELECT (10 cols) + JOIN` | Selects 10 columns via a single JOIN of 2 tables |
| 2 | `VIEW (10 cols) + JOIN` | Reads through a VIEW built on a 2-table JOIN |
| 3 | `SELECT * + JOIN` | `SELECT *` across a 2-table JOIN |

Each pattern is executed multiple times (default: 10) and min/max/avg/median/stdev are reported.

## What It Does

1. **Phase 1 -- Single Table**: Creates `BENCH_SINGLE` with 100 `VARCHAR2(50)` columns, inserts 10 000 rows of random data, and benchmarks reading ALL columns.
2. **Phase 2 -- Three Normalized Tables**: Creates `BENCH_MULTI_A` (34 cols), `BENCH_MULTI_B` (33 cols), `BENCH_MULTI_C` (33 cols), inserts the same volume of random data, and benchmarks reading only 10 needed columns by joining just 2 of the 3 tables (5 cols from each).
3. **Comparison**: Prints a side-by-side table showing the ratio.

## Requirements

- Java 17+
- Maven 3.6+
- Access to an Oracle database (e.g. Oracle XE / Free, or any Oracle instance)

## Build

```bash
mvn clean package
```

## Usage

### Via command-line arguments

```bash
java -jar target/oracle-benchmark-1.0-SNAPSHOT.jar \
    --host localhost \
    --port 1521 \
    --service FREEPDB1 \
    --user benchmark \
    --password benchmark
```

### Via environment variables

```bash
export ORACLE_HOST=localhost
export ORACLE_PORT=1521
export ORACLE_SERVICE=FREEPDB1
export ORACLE_USER=benchmark
export ORACLE_PASSWORD=benchmark

java -jar target/oracle-benchmark-1.0-SNAPSHOT.jar
```

### Options

| Flag | Default | Description |
|------|---------|-------------|
| `--host` | `localhost` | Oracle host |
| `--port` | `1521` | Oracle port |
| `--service` | `FREEPDB1` | Oracle service name |
| `--user` | `benchmark` | DB user |
| `--password` | `benchmark` | DB password |
| `--rows` | `10000` | Number of rows to insert |
| `--iterations` | `10` | Times each query is executed |
| `--no-cleanup` | off | Keep benchmark tables after run |
| `--no-fetch` | off | Execute queries without fetching rows |

## Example Output

```
======================================================================
  Oracle Benchmark Tool (Java)
======================================================================
  Rows: 10000  |  Iterations: 10  |  Fetch: true
  Wide table:       reads ALL 100 columns
  Normalized:       reads only 10 columns via 1 JOIN (2 tables)

======================================================================
  Phase 1: Single Table -- read ALL 100 columns
======================================================================

[Benchmarking Reads]
+-----------------------+-----------+-----------+-----------+-------------+------------+
| Pattern               |   Min (s) |   Max (s) |   Avg (s) |  Median (s) |  Stdev (s) |
+-----------------------+-----------+-----------+-----------+-------------+------------+
| SELECT (all 100 cols) |    0.0932 |    0.3743 |    0.1695 |      0.1449 |     0.0803 |
+-----------------------+-----------+-----------+-----------+-------------+------------+
| VIEW   (all 100 cols) |    0.1008 |    0.1444 |    0.1121 |      0.1109 |     0.0120 |
+-----------------------+-----------+-----------+-----------+-------------+------------+
| SELECT *              |    0.1011 |    0.1232 |    0.1088 |      0.1076 |     0.0075 |
+-----------------------+-----------+-----------+-----------+-------------+------------+

======================================================================
  Phase 2: Normalized Tables -- read only 10 cols via 1 JOIN
======================================================================

[Benchmarking Reads (2-table JOIN, selected cols only)]
+-------------------------+-----------+-----------+-----------+-------------+------------+
| Pattern                 |   Min (s) |   Max (s) |   Avg (s) |  Median (s) |  Stdev (s) |
+-------------------------+-----------+-----------+-----------+-------------+------------+
| SELECT (10 cols) + JOIN |    0.0229 |    0.0689 |    0.0355 |      0.0325 |     0.0137 |
+-------------------------+-----------+-----------+-----------+-------------+------------+
| VIEW   (10 cols) + JOIN |    0.0233 |    0.0558 |    0.0300 |      0.0255 |     0.0101 |
+-------------------------+-----------+-----------+-----------+-------------+------------+
| SELECT * + JOIN         |    0.0775 |    0.1713 |    0.1173 |      0.1187 |     0.0238 |
+-------------------------+-----------+-----------+-----------+-------------+------------+

======================================================================
  Comparison: Wide Table (all 100 cols) vs Normalized (10 cols)
======================================================================
+---------------------------+------------------+---------------------------+----------------------+---------------------+---------------------+
| Single-Table Pattern      |   Single Avg (s) | Normalized Pattern        |   Normalized Avg (s) |   Norm / Wide Ratio | Verdict             |
+---------------------------+------------------+---------------------------+----------------------+---------------------+---------------------+
| SELECT (all 100 cols)     |           0.1695 | SELECT (10 cols) + JOIN   |               0.0355 |              0.2097 | Normalized faster   |
+---------------------------+------------------+---------------------------+----------------------+---------------------+---------------------+
| VIEW   (all 100 cols)     |           0.1121 | VIEW   (10 cols) + JOIN   |               0.0300 |              0.2677 | Normalized faster   |
+---------------------------+------------------+---------------------------+----------------------+---------------------+---------------------+
| SELECT *                  |           0.1088 | SELECT * + JOIN           |               0.1173 |              1.0778 | Normalized slower   |
+---------------------------+------------------+---------------------------+----------------------+---------------------+---------------------+
```

*(Values above are from an actual run against Oracle Free in Docker.)*

## Database User Setup

If you need to create a dedicated benchmark user in Oracle:

```sql
-- Connect as SYSDBA
ALTER SESSION SET CONTAINER = FREEPDB1;

CREATE USER benchmark IDENTIFIED BY benchmark
    DEFAULT TABLESPACE USERS
    TEMPORARY TABLESPACE TEMP
    QUOTA UNLIMITED ON USERS;

GRANT CREATE SESSION, CREATE TABLE, CREATE VIEW TO benchmark;
```
