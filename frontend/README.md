# WalkMate V1 Frontend Implementation

## Architecture

Clean Architecture + MVVM Pattern

### Package Structure

```
com.walkingapp
├── model/                  # Data models
│   ├── Intent.java
│   ├── Proposal.java
│   └── Session.java
├── data/                   # Data layer
│   ├── api/
│   │   ├── ApiClient.java
│   │   └── ApiService.java
│   └── repository/
│       └── WalkingRepository.java
└── ui/                     # Presentation layer
    ├── intent/
    │   ├── IntentActivity.java
    │   └── IntentViewModel.java
    └── session/
        ├── ProposalActivity.java
        ├── SessionActivity.java
        └── SessionViewModel.java
```

## Setup Instructions

1. **Sync Gradle**: Open project in Android Studio and sync Gradle files
2. **Update Base URL**: In `ApiClient.java`, the base URL is set to `http://10.0.2.2:8000/` (Android emulator localhost)
   - For physical device: Update to your backend server IP
3. **Run Backend**: Ensure FastAPI backend is running on port 8000

## User Flow

### 1. Create Intent (IntentActivity)

- Enter walk type (e.g., casual, exercise)
- Select start time using date/time picker
- Choose flexibility (±30 minutes or ±1 hour)
- Enter location (latitude, longitude)
- Set radius in meters
- Click "Create Intent"

### 2. Find Match

- After intent created, click "Find Match"
- System calls backend matching service
- If match found, navigates to ProposalActivity

### 3. Confirm Proposal (ProposalActivity)

- View proposal details
- Click "Confirm Proposal" to accept
- When both users confirm, proposal becomes CONFIRMED
- Click "View Session" to navigate to SessionActivity

### 4. Manage Session (SessionActivity)

- View session details and status
- When status is CONFIRMED: "Start Session" button enabled
- When status is IN_PROGRESS: "Complete Session" button enabled
- Click "Refresh" to reload session state

## API Endpoints Used

### Intent

- `POST /api/intents` - Create intent
- `GET /api/intents/user/{userId}` - Get user's intent

### Match

- `POST /api/match` - Find match (body: {user_id})

### Proposal

- `GET /api/proposals/user/{userId}` - Get user's proposal
- `PUT /api/proposals/{proposalId}/confirm` - Confirm proposal
- `PUT /api/proposals/{proposalId}/cancel` - Cancel proposal

### Session

- `GET /api/sessions/user/{userId}` - Get user's session
- `PUT /api/sessions/{sessionId}/start` - Start session
- `PUT /api/sessions/{sessionId}/complete` - Complete session

## Key Design Principles

### No Business Logic in Frontend

- All state transitions handled by backend
- All matching logic handled by backend
- Frontend only renders UI based on backend state

### MVVM Separation

- **Activity**: Only UI rendering and user interaction
- **ViewModel**: Orchestrates repository calls, exposes LiveData
- **Repository**: API calls only, no business logic

### State Management

- Uses LiveData for reactive state updates
- Result wrapper class with LOADING, SUCCESS, ERROR states
- UI updates based on observed state changes

## Configuration Notes

### User ID

Currently hardcoded as `USER_ID = 1` in IntentActivity. For multiple users testing:

- User 1: Install on Emulator 1
- User 2: Install on Emulator 2 and change USER_ID to 2

### Network Configuration

- Emulator: `http://10.0.2.2:8000/` maps to host machine localhost
- Physical device: Replace with actual IP address of backend server
- Ensure `android:usesCleartextTraffic="true"` is set in AndroidManifest.xml for HTTP

## Dependencies

- AndroidX Lifecycle (ViewModel + LiveData)
- Retrofit 2.9.0
- OkHttp Logging Interceptor
- Gson Converter

## Build Notes

Minimum SDK: 24 (Android 7.0)
Target SDK: 36
Java Version: 11
