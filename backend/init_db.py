import sqlite3
import os

DB_PATH = "db/walkmate.db"
SCHEMA_PATH = "../db/db.sql"


def init_database():
    os.makedirs("db", exist_ok=True)

    if os.path.exists(DB_PATH):
        print(f"Database already exists at {DB_PATH}")
        return

    conn = sqlite3.connect(DB_PATH)
    conn.execute("PRAGMA foreign_keys = ON")

    with open(SCHEMA_PATH, "r") as f:
        schema = f.read()

    conn.executescript(schema)
    conn.commit()

    cursor = conn.execute("INSERT INTO users DEFAULT VALUES")
    user1_id = cursor.lastrowid

    cursor = conn.execute("INSERT INTO users DEFAULT VALUES")
    user2_id = cursor.lastrowid

    conn.commit()
    conn.close()

    print(f"Database initialized at {DB_PATH}")
    print(f"Created test users: {user1_id}, {user2_id}")


if __name__ == "__main__":
    init_database()
