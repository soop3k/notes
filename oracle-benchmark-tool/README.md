# Oracle Read Performance Benchmark

Compares read performance of a **single wide table (100 columns)** vs **3 normalized tables joined together** (same data split across 3 tables).

## Read Patterns Tested

| # | Pattern | Description |
|---|---------|-------------|
| 1 | `SELECT` (specific columns) | Reads 5 specific columns from the table(s) |
| 2 | `VIEW` | Reads through a pre-created VIEW (also 5 columns) |
| 3 | `SELECT *` | Reads all 100 columns from the table(s) |

Each pattern is executed multiple times (default: 10) and min/max/avg/median/stdev are reported.

## What It Does

1. **Phase 1 – Single Table**: Creates `BENCH_SINGLE` with 100 `VARCHAR2(50)` columns, inserts 10 000 rows of random data, and benchmarks the 3 read patterns.
2. **Phase 2 – Three Tables with JOIN**: Creates `BENCH_MULTI_A` (34 cols), `BENCH_MULTI_B` (33 cols), `BENCH_MULTI_C` (33 cols), inserts the same volume of random data, and benchmarks the same 3 read patterns using a 3-way JOIN on `ID`.
3. **Comparison**: Prints a side-by-side table showing the ratio of JOIN vs single-table performance.

## Requirements

```bash
pip install -r requirements.txt
```

- Python 3.9+
- `oracledb` — Oracle Database driver
- `tabulate` — pretty-print tables (optional but recommended)
- Access to an Oracle database (e.g. Oracle XE / Free, or any Oracle instance)

## Usage

### Via command-line arguments

```bash
python oracle_benchmark.py \
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

python oracle_benchmark.py
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
  Phase 1: Single Table (100 columns)
======================================================================

[Benchmarking Reads]
+------------------+----------+----------+----------+------------+-----------+
| Pattern          |  Min (s) |  Max (s) |  Avg (s) | Median (s) | Stdev (s) |
+==================+==========+==========+==========+============+===========+
| SELECT (5 cols)  |   0.0312 |   0.0425 |   0.0354 |     0.0348 |    0.0032 |
| VIEW (5 cols)    |   0.0308 |   0.0410 |   0.0350 |     0.0345 |    0.0029 |
| SELECT *         |   0.1820 |   0.2150 |   0.1935 |     0.1910 |    0.0095 |
+------------------+----------+----------+----------+------------+-----------+

======================================================================
  Phase 2: Three Tables with JOIN (100 columns total)
======================================================================

[Benchmarking Reads with JOINs]
+-------------------------+----------+----------+----------+------------+-----------+
| Pattern                 |  Min (s) |  Max (s) |  Avg (s) | Median (s) | Stdev (s) |
+=========================+==========+==========+==========+============+===========+
| SELECT (5 cols) + JOIN  |   0.0485 |   0.0612 |   0.0530 |     0.0520 |    0.0038 |
| VIEW (5 cols) + JOIN    |   0.0490 |   0.0608 |   0.0535 |     0.0528 |    0.0035 |
| SELECT * + JOIN         |   0.2510 |   0.2890 |   0.2680 |     0.2660 |    0.0110 |
+-------------------------+----------+----------+----------+------------+-----------+

======================================================================
  Comparison: Single Table vs 3-Table JOIN
======================================================================
+-----------+------------------+----------------+----------------------+-------------+
| Pattern   | Single Avg (s)   | JOIN Avg (s)   | JOIN / Single Ratio  | Verdict     |
+===========+==================+================+======================+=============+
| SELECT    |           0.0354 |         0.0530 |               1.4972 | JOIN slower |
| VIEW      |           0.0350 |         0.0535 |               1.5286 | JOIN slower |
| SELECT *  |           0.1935 |         0.2680 |               1.3850 | JOIN slower |
+-----------+------------------+----------------+----------------------+-------------+
```

*(Values above are illustrative — actual numbers depend on your Oracle instance.)*

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
