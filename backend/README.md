# WalkMate Backend V1

Production-ready FastAPI backend following Clean Architecture principles.

## Architecture

### Layer Structure

```
backend/
├── app/
│   ├── main.py                 # FastAPI application
│   ├── api/routes/             # API layer (HTTP)
│   │   ├── intent.py
│   │   ├── match.py
│   │   └── session.py
│   ├── domain/                 # Domain layer (business logic)
│   │   ├── models/
│   │   ├── state_machine.py
│   │   └── invariants.py
│   ├── services/               # Service layer (orchestration)
│   │   ├── matching_service.py
│   │   └── session_service.py
│   ├── strategies/             # Strategy pattern
│   │   ├── matching_strategy.py
│   │   └── first_compatible.py
│   ├── repositories/           # Repository layer (data access)
│   │   ├── intent_repo.py
│   │   ├── proposal_repo.py
│   │   └── session_repo.py
│   └── infrastructure/         # Infrastructure
│       └── database.py
├── db/
│   └── walkmate.db             # SQLite database
├── init_db.py                  # Database initialization
└── requirements.txt
```

## Setup

### Option 1: Using Conda (Recommended)

1. Create and activate conda environment:

```bash
conda env create -f environment.yml
conda activate walkmate
```

2. Initialize database:

```bash
python init_db.py
```

3. Run server:

```bash
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

### Option 2: Using Pip

1. Install dependencies:

```bash
pip install -r requirements.txt
```

2. Initialize database:

```bash
python init_db.py
```

3. Run server:

```bash
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

## API Endpoints

### Intent

- `POST /api/intents` - Create intent
- `GET /api/intents/user/{userId}` - Get user's intent

### Match

- `POST /api/match` - Find match

### Proposal

- `GET /api/proposals/{proposalId}` - Get proposal
- `GET /api/proposals/user/{userId}` - Get user's proposal
- `PUT /api/proposals/{proposalId}/confirm` - Confirm proposal
- `PUT /api/proposals/{proposalId}/cancel` - Cancel proposal

### Session

- `GET /api/sessions/{sessionId}` - Get session
- `GET /api/sessions/user/{userId}` - Get user's session
- `PUT /api/sessions/{sessionId}/start` - Start session
- `PUT /api/sessions/{sessionId}/complete` - Complete session

## Clean Architecture Compliance

### API Layer

- Accepts HTTP requests
- Returns JSON responses
- No business logic
- No direct state updates

### Domain Layer

- State machine logic
- Invariant validation
- Pure business rules
- No database access

### Service Layer

- Workflow orchestration
- Calls repositories
- Calls matching strategy
- Centralized state transitions

### Repository Layer

- Database access only
- No business logic

### Strategy Layer

- MatchingStrategy interface
- FirstCompatibleStrategy (V1)
- Dependency inversion principle

## Database

SQLite with foreign key enforcement enabled.
Uses transactions for matching operations.

## Testing

Test users created during initialization:

- User ID 1
- User ID 2

Use these IDs for testing the complete workflow.
