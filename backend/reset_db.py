"""
Reset database for testing - clears all intents, proposals, and sessions
Usage: python reset_db.py
"""
import sqlite3

def reset_database():
    conn = sqlite3.connect('db/walkmate.db')
    cursor = conn.cursor()
    
    # Delete in correct order (respect foreign keys)
    cursor.execute('DELETE FROM sessions')
    cursor.execute('DELETE FROM proposals')
    cursor.execute('DELETE FROM intents')
    
    conn.commit()
    
    # Verify
    cursor.execute('SELECT COUNT(*) FROM intents')
    intent_count = cursor.fetchone()[0]
    cursor.execute('SELECT COUNT(*) FROM proposals')
    proposal_count = cursor.fetchone()[0]
    cursor.execute('SELECT COUNT(*) FROM sessions')
    session_count = cursor.fetchone()[0]
    
    print(f"✓ Database reset complete")
    print(f"  - Intents: {intent_count}")
    print(f"  - Proposals: {proposal_count}")
    print(f"  - Sessions: {session_count}")
    print(f"\nUsers still available: 1, 2")
    
    conn.close()

if __name__ == '__main__':
    reset_database()
