# WalkMate Handoff — End of Phase 7

## What Was Delivered

### Backend

**Database (Flyway V15)**
- `V15__create_user_profile.sql` — adds two new tables:
  - `user_profile` (1:1 with `user_account`) — stores `full_name`, `gender` (VARCHAR), `date_of_birth`, `avatar_url`, `bio`, `search_radius`
  - `profile_tag` — stores personality tags keyed by `(user_id, tag_name)`
- DB-level CHECK on `date_of_birth`: must be ≥ 13 years in the past (defence-in-depth; domain layer also validates this at runtime).

**Domain layer** (`domain/user/`)
| Class | Role |
|---|---|
| `Gender.java` | Enum: `MALE / FEMALE / OTHER / PREFER_NOT_TO_SAY` |
| `UserProfile.java` | Aggregate root; rich `update()` validates age ≥ 13 and radius bounds |
| `ProfileTag.java` | Immutable value type (userId + tagName) |
| `UserProfileRepository.java` | Interface: `findByUserId`, `save`, `replaceTags`, `findTagsByUserId` |

**Application layer** (`application/user/`)
| Class | Role |
|---|---|
| `UserQueryService.java` | `getMyProfile(UUID)` (lazy-creates blank profile), `getProfile(UUID)`, `getUser(UUID)` |
| `UserProfileCommandService.java` | `updateProfile(UpdateProfileCommand)`, `updateAvatar(UUID, String)` |
| `UpdateProfileCommand.java` | Record carrying all editable fields + caller identity |

**Infrastructure layer**
| Class | Role |
|---|---|
| `UserProfileJdbcRepository.java` | JDBC upsert for `user_profile`, bulk replace for `profile_tag` |
| `AvatarStorageService.java` | Stores uploaded files to `${app.file.upload-dir}/avatars/`; returns public URL; path-traversal safe |

**Presentation layer**
| Endpoint | Auth | Description |
|---|---|---|
| `GET /api/v1/profile/me` | JWT required | Returns `ApiResponse<UserProfileResponse>` for the caller |
| `PUT /api/v1/profile/me` | JWT required | Updates profile; returns updated `ApiResponse<UserProfileResponse>` |
| `POST /api/v1/profile/avatar` | JWT required | Multipart upload; returns `ApiResponse<AvatarUploadResponse>` |
| `GET /api/v1/users/{userId}` | Public | Returns public profile for any user |
| `GET /api/v1/files/avatars/{filename}` | Public | Serves uploaded avatar files from disk |

`UserProfileResponse` shape:
```json
{
  "userId": "...",
  "fullName": "Nguyễn Bảo Duy",
  "gender": "MALE",
  "dateOfBirth": "1995-06-15",
  "avatarUrl": "http://localhost:8080/api/v1/files/avatars/xxx.jpg",
  "bio": "I love morning walks!",
  "searchRadius": 3000,
  "trustScore": 42,
  "totalDistanceKm": 248.5,
  "totalSessions": 32,
  "tags": ["Chatty", "Dog Friendly"]
}
```

**Security configuration changes** (`SecurityConfig.java`)
- `GET /api/v1/users/*` → `permitAll()` (public profile)
- `GET /api/v1/files/**` → `permitAll()` (avatar file serving)
- `/api/v1/profile/**` → `authenticated()` (own-profile CRUD)

**application.properties additions**
```properties
app.file.upload-dir=./uploads
app.file.public-base-url=http://localhost:8080
spring.servlet.multipart.max-file-size=5MB
spring.servlet.multipart.max-request-size=6MB
```

---

### Frontend

**Domain** (`domain/user/`)
- `UserProfile.java` — domain entity with all profile fields
- `UserProfileRepository.java` — interface: `getMyProfile`, `getProfile`, `updateProfile`, `uploadAvatar`

**Data layer**
| Class | Role |
|---|---|
| `UserProfileApiService.java` | Retrofit interface for all 4 profile endpoints |
| `UserProfileResponse.java` | Gson DTO mirroring backend response |
| `UpdateProfileRequestDto.java` | Gson DTO for PUT payload |
| `AvatarUploadResponse.java` | Gson DTO for avatar upload response |
| `UserProfileMapper.java` | `UserProfileResponse → UserProfile` domain mapping |
| `UserProfileRepositoryImpl.java` | Executes Retrofit calls on background threads via `DomainCallback<T>` |

**Presentation** (`ui/profile/`)
| Change | Detail |
|---|---|
| `ProfileViewModel.java` | Replaced mock `buildMockState()` with real `loadProfile()` calling `UserProfileRepository.getMyProfile()`. Added `saveProfile()` and `uploadAvatar()` for future edit screen wiring |
| `ProfileViewModelFactory.java` | Now accepts `UserProfileRepository` instead of `UserRepository` |
| `ProfileFragment.java` | Updated factory call to use `app.getUserProfileRepository()` |
| `ProfileUiState.java` | Added `ProfileUiState.error(String)` static factory |

**WalkMateApplication.java**
- Added `UserProfileRepository userProfileRepository` singleton field
- Added `getUserProfileRepository()` getter (lazy-initialised `UserProfileRepositoryImpl`)

---

## Validated Test Cases
| Scenario | Expected result |
|---|---|
| First `GET /api/v1/profile/me` for new user | 200 — blank profile auto-created, returned |
| `GET /api/v1/profile/me` with valid JWT | 200 — profile fields returned |
| `PUT /api/v1/profile/me` with `dateOfBirth` < 13 years ago | 400 — `INVALID_USER_DATA` |
| `PUT /api/v1/profile/me` with `searchRadius = 0` | 422 — `VALIDATION_ERROR` (Bean Validation) |
| `PUT /api/v1/profile/me` with valid body | 200 — updated fields reflected |
| `POST /api/v1/profile/avatar` with JPEG | 200 — file saved, URL returned |
| `GET /api/v1/files/avatars/{filename}` | 200 — image bytes served |
| `GET /api/v1/users/{userId}` (no JWT) | 200 — public profile |
| Android: open Profile tab | Real backend data shown (not mock) |
| Android: save profile changes | PUT call executes, UI refreshes |

---

## Ready for Phase 8
- `UserProfile` aggregate is stable and independently versioned (V15 migration).
- Avatar storage is abstracted behind `AvatarStorageService` — swap the body for an S3/MinIO implementation by replacing only that class.
- `currentStreak` and badge rendering in `ProfileUiState` are set to `0 / empty` — populated once a session analytics and the existing `GamificationRepositoryImpl` are wired into the Profile screen (future phase).
