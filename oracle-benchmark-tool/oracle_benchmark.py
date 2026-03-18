#!/usr/bin/env python3
"""
Oracle Performance Benchmark Tool

Compares read performance between:
  1) A single wide table with 100 columns
  2) Three normalized tables joined together (same data, split across 3 tables)

Read patterns tested:
  - SELECT specific columns (5 columns)
  - SELECT through a VIEW
  - SELECT * (all columns)

Usage:
    python oracle_benchmark.py --host <host> --port <port> --service <service> --user <user> --password <password>

Requirements:
    pip install oracledb tabulate
"""

import argparse
import os
import random
import string
import statistics
import sys
import time
from contextlib import contextmanager

try:
    import oracledb
except ImportError:
    print("ERROR: 'oracledb' package is required. Install with: pip install oracledb")
    sys.exit(1)

try:
    from tabulate import tabulate
except ImportError:
    tabulate = None

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

NUM_COLUMNS = 100          # total number of data columns
NUM_ROWS = 10_000          # rows to insert
ITERATIONS = 10            # how many times each query is executed for averaging
FETCH_ALL = True           # whether to fetch all rows (True) or just execute (False)

# For the 3-table split we divide the 100 columns roughly equally
SPLIT = (34, 33, 33)       # columns per table (must sum to NUM_COLUMNS)

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def random_string(length: int = 20) -> str:
    return "".join(random.choices(string.ascii_letters + string.digits, k=length))


def col_name(index: int) -> str:
    """Return a column name like COL_001, COL_002, ..."""
    return f"COL_{index:03d}"


def get_connection(args):
    """Create an Oracle connection from CLI args or environment variables."""
    dsn = oracledb.makedsn(
        host=args.host or os.getenv("ORACLE_HOST", "localhost"),
        port=int(args.port or os.getenv("ORACLE_PORT", "1521")),
        service_name=args.service or os.getenv("ORACLE_SERVICE", "FREEPDB1"),
    )
    return oracledb.connect(
        user=args.user or os.getenv("ORACLE_USER", "benchmark"),
        password=args.password or os.getenv("ORACLE_PASSWORD", "benchmark"),
        dsn=dsn,
    )


@contextmanager
def timer():
    """Context manager that records elapsed wall-clock time in seconds."""
    result = {"elapsed": 0.0}
    start = time.perf_counter()
    yield result
    result["elapsed"] = time.perf_counter() - start


def drop_if_exists(cursor, obj_type: str, name: str):
    """Drop an Oracle object if it exists (TABLE, VIEW, etc.)."""
    try:
        cursor.execute(f"DROP {obj_type} {name} PURGE" if obj_type == "TABLE"
                       else f"DROP {obj_type} {name}")
    except oracledb.DatabaseError:
        pass


def print_section(title: str):
    print(f"\n{'=' * 70}")
    print(f"  {title}")
    print(f"{'=' * 70}")


def format_results(results: list[dict]):
    """Pretty-print benchmark results."""
    if tabulate:
        headers = results[0].keys()
        rows = [r.values() for r in results]
        print(tabulate(rows, headers=headers, tablefmt="grid", floatfmt=".4f"))
    else:
        for r in results:
            parts = [f"{k}: {v}" for k, v in r.items()]
            print(" | ".join(parts))


# ---------------------------------------------------------------------------
# Benchmark: Single wide table
# ---------------------------------------------------------------------------

SINGLE_TABLE = "BENCH_SINGLE"
SINGLE_VIEW = "BENCH_SINGLE_V"


def create_single_table(cursor):
    """Create a single table with 100 VARCHAR2 columns + an ID."""
    drop_if_exists(cursor, "VIEW", SINGLE_VIEW)
    drop_if_exists(cursor, "TABLE", SINGLE_TABLE)

    cols = ", ".join(f"{col_name(i)} VARCHAR2(50)" for i in range(1, NUM_COLUMNS + 1))
    ddl = f"CREATE TABLE {SINGLE_TABLE} (ID NUMBER PRIMARY KEY, {cols})"
    cursor.execute(ddl)

    # Create a view that selects 5 specific columns
    view_cols = ", ".join(col_name(i) for i in range(1, 6))
    cursor.execute(
        f"CREATE VIEW {SINGLE_VIEW} AS SELECT ID, {view_cols} FROM {SINGLE_TABLE}"
    )
    print(f"  Created table  {SINGLE_TABLE} with {NUM_COLUMNS} columns")
    print(f"  Created view   {SINGLE_VIEW}")


def insert_single_table(conn, cursor):
    """Insert NUM_ROWS rows of random data into the single table."""
    cols = ", ".join(col_name(i) for i in range(1, NUM_COLUMNS + 1))
    placeholders = ", ".join(f":{i}" for i in range(1, NUM_COLUMNS + 2))  # +1 for ID
    sql = f"INSERT INTO {SINGLE_TABLE} (ID, {cols}) VALUES ({placeholders})"

    batch_size = 1000
    for start in range(0, NUM_ROWS, batch_size):
        rows = []
        for row_id in range(start + 1, min(start + batch_size, NUM_ROWS) + 1):
            row = [row_id] + [random_string(20) for _ in range(NUM_COLUMNS)]
            rows.append(row)
        cursor.executemany(sql, rows)
    conn.commit()
    print(f"  Inserted {NUM_ROWS} rows into {SINGLE_TABLE}")


def benchmark_single_table(cursor) -> list[dict]:
    """Run 3 read patterns on the single wide table and return timing results."""
    select_cols = ", ".join(col_name(i) for i in range(1, 6))
    queries = {
        "SELECT (5 cols)": f"SELECT ID, {select_cols} FROM {SINGLE_TABLE}",
        "VIEW (5 cols)":   f"SELECT * FROM {SINGLE_VIEW}",
        "SELECT *":        f"SELECT * FROM {SINGLE_TABLE}",
    }

    results = []
    for label, sql in queries.items():
        timings = []
        for _ in range(ITERATIONS):
            with timer() as t:
                cursor.execute(sql)
                if FETCH_ALL:
                    cursor.fetchall()
            timings.append(t["elapsed"])

        results.append({
            "Pattern": label,
            "Min (s)": min(timings),
            "Max (s)": max(timings),
            "Avg (s)": statistics.mean(timings),
            "Median (s)": statistics.median(timings),
            "Stdev (s)": statistics.stdev(timings) if len(timings) > 1 else 0.0,
        })
    return results


# ---------------------------------------------------------------------------
# Benchmark: Three normalized tables with JOINs
# ---------------------------------------------------------------------------

MULTI_TABLES = ("BENCH_MULTI_A", "BENCH_MULTI_B", "BENCH_MULTI_C")
MULTI_VIEW = "BENCH_MULTI_V"


def _col_ranges():
    """Return (start, end) column index ranges for each of the 3 tables."""
    s1 = 1
    e1 = SPLIT[0]
    s2 = e1 + 1
    e2 = e1 + SPLIT[1]
    s3 = e2 + 1
    e3 = e2 + SPLIT[2]
    return (s1, e1), (s2, e2), (s3, e3)


def create_multi_tables(cursor):
    """Create 3 tables that together hold the same 100 columns, linked by ID."""
    drop_if_exists(cursor, "VIEW", MULTI_VIEW)
    for tbl in MULTI_TABLES:
        drop_if_exists(cursor, "TABLE", tbl)

    ranges = _col_ranges()
    for tbl, (start, end) in zip(MULTI_TABLES, ranges):
        cols = ", ".join(f"{col_name(i)} VARCHAR2(50)" for i in range(start, end + 1))
        ddl = f"CREATE TABLE {tbl} (ID NUMBER PRIMARY KEY, {cols})"
        cursor.execute(ddl)
        print(f"  Created table {tbl} with {end - start + 1} columns (COL_{start:03d}..COL_{end:03d})")

    # Create a view that joins all 3 tables on ID and picks 5 columns
    view_cols = ", ".join(f"a.{col_name(i)}" for i in range(1, 6))
    cursor.execute(f"""
        CREATE VIEW {MULTI_VIEW} AS
        SELECT a.ID, {view_cols}
        FROM {MULTI_TABLES[0]} a
        JOIN {MULTI_TABLES[1]} b ON a.ID = b.ID
        JOIN {MULTI_TABLES[2]} c ON a.ID = c.ID
    """)
    print(f"  Created view  {MULTI_VIEW} (JOIN of all 3 tables)")


def insert_multi_tables(conn, cursor):
    """Insert the same random data into the 3 normalized tables."""
    ranges = _col_ranges()
    batch_size = 1000

    for start_row in range(0, NUM_ROWS, batch_size):
        # Generate a batch of full rows first
        batch_data = []
        for row_id in range(start_row + 1, min(start_row + batch_size, NUM_ROWS) + 1):
            all_cols = [random_string(20) for _ in range(NUM_COLUMNS)]
            batch_data.append((row_id, all_cols))

        for tbl_idx, (tbl, (cs, ce)) in enumerate(zip(MULTI_TABLES, ranges)):
            num_cols = ce - cs + 1
            cols = ", ".join(col_name(i) for i in range(cs, ce + 1))
            placeholders = ", ".join(f":{j}" for j in range(1, num_cols + 2))
            sql = f"INSERT INTO {tbl} (ID, {cols}) VALUES ({placeholders})"

            rows = []
            for row_id, all_cols in batch_data:
                row = [row_id] + all_cols[cs - 1: ce]
                rows.append(row)
            cursor.executemany(sql, rows)

    conn.commit()
    print(f"  Inserted {NUM_ROWS} rows into each of {', '.join(MULTI_TABLES)}")


def benchmark_multi_tables(cursor) -> list[dict]:
    """Run 3 read patterns using JOINs across the 3 tables and return timing results."""
    select_cols = ", ".join(f"a.{col_name(i)}" for i in range(1, 6))

    all_cols_parts = []
    for tbl_alias, (cs, ce) in zip(("a", "b", "c"), _col_ranges()):
        for i in range(cs, ce + 1):
            all_cols_parts.append(f"{tbl_alias}.{col_name(i)}")
    all_cols = ", ".join(all_cols_parts)

    join_clause = (
        f"FROM {MULTI_TABLES[0]} a "
        f"JOIN {MULTI_TABLES[1]} b ON a.ID = b.ID "
        f"JOIN {MULTI_TABLES[2]} c ON a.ID = c.ID"
    )

    queries = {
        "SELECT (5 cols) + JOIN": f"SELECT a.ID, {select_cols} {join_clause}",
        "VIEW (5 cols) + JOIN":   f"SELECT * FROM {MULTI_VIEW}",
        "SELECT * + JOIN":        f"SELECT a.ID, {all_cols} {join_clause}",
    }

    results = []
    for label, sql in queries.items():
        timings = []
        for _ in range(ITERATIONS):
            with timer() as t:
                cursor.execute(sql)
                if FETCH_ALL:
                    cursor.fetchall()
            timings.append(t["elapsed"])

        results.append({
            "Pattern": label,
            "Min (s)": min(timings),
            "Max (s)": max(timings),
            "Avg (s)": statistics.mean(timings),
            "Median (s)": statistics.median(timings),
            "Stdev (s)": statistics.stdev(timings) if len(timings) > 1 else 0.0,
        })
    return results


# ---------------------------------------------------------------------------
# Cleanup
# ---------------------------------------------------------------------------

def cleanup(cursor):
    """Drop all benchmark objects."""
    drop_if_exists(cursor, "VIEW", SINGLE_VIEW)
    drop_if_exists(cursor, "TABLE", SINGLE_TABLE)
    drop_if_exists(cursor, "VIEW", MULTI_VIEW)
    for tbl in MULTI_TABLES:
        drop_if_exists(cursor, "TABLE", tbl)
    print("  Cleaned up all benchmark objects.")


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def parse_args():
    parser = argparse.ArgumentParser(
        description="Oracle read-performance benchmark: single wide table vs 3-table JOIN"
    )
    parser.add_argument("--host", default=None, help="Oracle host (default: env ORACLE_HOST or localhost)")
    parser.add_argument("--port", default=None, help="Oracle port (default: env ORACLE_PORT or 1521)")
    parser.add_argument("--service", default=None, help="Oracle service name (default: env ORACLE_SERVICE or FREEPDB1)")
    parser.add_argument("--user", default=None, help="Oracle user (default: env ORACLE_USER or benchmark)")
    parser.add_argument("--password", default=None, help="Oracle password (default: env ORACLE_PASSWORD or benchmark)")
    parser.add_argument("--rows", type=int, default=NUM_ROWS, help=f"Number of rows to insert (default: {NUM_ROWS})")
    parser.add_argument("--iterations", type=int, default=ITERATIONS, help=f"Query iterations for averaging (default: {ITERATIONS})")
    parser.add_argument("--no-cleanup", action="store_true", help="Keep benchmark tables after run")
    parser.add_argument("--no-fetch", action="store_true", help="Execute queries without fetching rows")
    return parser.parse_args()


def main():
    args = parse_args()

    global NUM_ROWS, ITERATIONS, FETCH_ALL
    NUM_ROWS = args.rows
    ITERATIONS = args.iterations
    FETCH_ALL = not args.no_fetch

    print_section("Oracle Benchmark Tool")
    print(f"  Rows: {NUM_ROWS}  |  Iterations: {ITERATIONS}  |  Fetch: {FETCH_ALL}")

    conn = get_connection(args)
    cursor = conn.cursor()
    # Increase array size for faster fetches
    cursor.arraysize = 5000

    try:
        # ---- Single Table Benchmark ----
        print_section("Phase 1: Single Table (100 columns)")
        print("\n[Setup]")
        create_single_table(cursor)
        conn.commit()

        print("\n[Insert Data]")
        with timer() as t_ins:
            insert_single_table(conn, cursor)
        print(f"  Insert time: {t_ins['elapsed']:.2f}s")

        print("\n[Benchmarking Reads]")
        single_results = benchmark_single_table(cursor)
        format_results(single_results)

        # ---- Multi-Table Benchmark ----
        print_section("Phase 2: Three Tables with JOIN (100 columns total)")
        print("\n[Setup]")
        create_multi_tables(cursor)
        conn.commit()

        print("\n[Insert Data]")
        with timer() as t_ins:
            insert_multi_tables(conn, cursor)
        print(f"  Insert time: {t_ins['elapsed']:.2f}s")

        print("\n[Benchmarking Reads with JOINs]")
        multi_results = benchmark_multi_tables(cursor)
        format_results(multi_results)

        # ---- Comparison ----
        print_section("Comparison: Single Table vs 3-Table JOIN")
        comparison = []
        for s, m in zip(single_results, multi_results):
            ratio = m["Avg (s)"] / s["Avg (s)"] if s["Avg (s)"] > 0 else float("inf")
            comparison.append({
                "Pattern": s["Pattern"].replace(" (5 cols)", "").replace(" *", " *"),
                "Single Avg (s)": s["Avg (s)"],
                "JOIN Avg (s)": m["Avg (s)"],
                "JOIN / Single Ratio": ratio,
                "Verdict": "JOIN slower" if ratio > 1.05 else ("Similar" if ratio > 0.95 else "JOIN faster"),
            })
        format_results(comparison)

    finally:
        if not args.no_cleanup:
            print_section("Cleanup")
            cleanup(cursor)
            conn.commit()
        cursor.close()
        conn.close()

    print_section("Done")


if __name__ == "__main__":
    main()
