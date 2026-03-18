# Oracle Read Performance Benchmark

Compares read performance of:
- **Single wide table (100 columns)** -- always reads ALL columns (simulates a denormalized design where you must fetch everything)
- **3 normalized tables joined** -- reads only the NEEDED columns (default: 10), showing the advantage of normalization

## Read Patterns Tested

### Single Table (reads ALL 100 columns every time)
| # | Pattern | Description |
|---|---------|-------------|
| 1 | `SELECT (all 100 cols)` | Explicitly selects all 100 columns |
| 2 | `VIEW (all 100 cols)` | Reads through a VIEW over all 100 columns |
| 3 | `SELECT *` | `SELECT *` from the wide table |

### 3-Table JOIN (reads only 10 columns)
| # | Pattern | Description |
|---|---------|-------------|
| 1 | `SELECT (10 cols) + JOIN` | Selects 10 columns via a 3-way JOIN |
| 2 | `VIEW (10 cols) + JOIN` | Reads through a VIEW built on a 3-way JOIN |
| 3 | `SELECT * + JOIN` | Selects columns via a 3-way JOIN |

Each pattern is executed multiple times (default: 10) and min/max/avg/median/stdev are reported.

## What It Does

1. **Phase 1 -- Single Table**: Creates `BENCH_SINGLE` with 100 `VARCHAR2(50)` columns, inserts 10 000 rows of random data, and benchmarks reading ALL columns.
2. **Phase 2 -- Three Tables with JOIN**: Creates `BENCH_MULTI_A` (34 cols), `BENCH_MULTI_B` (33 cols), `BENCH_MULTI_C` (33 cols), inserts the same volume of random data, and benchmarks reading only 10 needed columns through a 3-table JOIN.
3. **Comparison**: Prints a side-by-side table showing the ratio. The key insight: even with the overhead of a 3-table JOIN, reading only 10 columns can be faster than reading all 100 from a single wide table.

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
  Phase 1: Single Table -- read ALL 100 columns
======================================================================

[Benchmarking Reads]
+---------------------------+----------+----------+----------+------------+-----------+
| Pattern                   |  Min (s) |  Max (s) |  Avg (s) | Median (s) | Stdev (s) |
+===========================+==========+==========+==========+============+===========+
| SELECT (all 100 cols)     |   0.1780 |   0.2100 |   0.1920 |     0.1900 |    0.0090 |
| VIEW   (all 100 cols)     |   0.1790 |   0.2080 |   0.1910 |     0.1895 |    0.0085 |
| SELECT *                  |   0.1820 |   0.2150 |   0.1935 |     0.1910 |    0.0095 |
+---------------------------+----------+----------+----------+------------+-----------+

======================================================================
  Phase 2: 3 Normalized Tables + JOIN -- read only 10 cols
======================================================================

[Benchmarking Reads (3-table JOIN, selected cols only)]
+------------------------------+----------+----------+----------+------------+-----------+
| Pattern                      |  Min (s) |  Max (s) |  Avg (s) | Median (s) | Stdev (s) |
+==============================+==========+==========+==========+============+===========+
| SELECT (10 cols) + JOIN      |   0.0485 |   0.0612 |   0.0530 |     0.0520 |    0.0038 |
| VIEW   (10 cols) + JOIN      |   0.0490 |   0.0608 |   0.0535 |     0.0528 |    0.0035 |
| SELECT * + JOIN              |   0.0488 |   0.0615 |   0.0532 |     0.0522 |    0.0036 |
+------------------------------+----------+----------+----------+------------+-----------+

======================================================================
  Comparison: Single Table (all 100 cols) vs JOIN (10 cols)
======================================================================
+---------------------------+------------------+------------------------------+----------------+----------------------+-------------+
| Single-Table Pattern      | Single Avg (s)   | JOIN Pattern                 | JOIN Avg (s)   | JOIN / Single Ratio  | Verdict     |
+===========================+==================+==============================+================+======================+=============+
| SELECT (all 100 cols)     |           0.1920 | SELECT (10 cols) + JOIN      |         0.0530 |               0.2760 | JOIN faster |
| VIEW   (all 100 cols)     |           0.1910 | VIEW   (10 cols) + JOIN      |         0.0535 |               0.2801 | JOIN faster |
| SELECT *                  |           0.1935 | SELECT * + JOIN              |         0.0532 |               0.2749 | JOIN faster |
+---------------------------+------------------+------------------------------+----------------+----------------------+-------------+
```

*(Values above are illustrative -- actual numbers depend on your Oracle instance.)*

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
