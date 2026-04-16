# WalkMate — Discovery Use Cases

> Part of: [Use Cases Index](README.md)

**Domain:** Hotspot Discovery
**Last Updated:** 2026-04-12

---

## Table of Contents

| UC# | Use Case | API Endpoint |
|-----|----------|--------------|
| UC-14 | [Browse Hotspot Map](#uc-14--browse-hotspot-map) | `GET /api/v1/hotspots` |

---

### UC-14 — Browse Hotspot Map

**Use Case Name:** Browse Hotspot Map

**Initial assumption:** User is on the Home/Map screen. Authentication is optional (endpoint is public).

**Normal:**
1. UI calls `GET /api/v1/hotspots`.
2. Backend returns `200 OK` with list of hotspots, each including `id`, `name`, `lat`, `lng`, `openIntentCount`.
3. UI renders hotspot pins on the map. Each pin's visual weight (size or color) reflects `openIntentCount` — more intents = more prominent pin.
4. User taps a pin to see the hotspot's detail card (name, intent count, "Create Intent" CTA).
5. If user taps "Create Intent":
   - If authenticated and token is valid: navigate to UC-15 with selected hotspot.
   - If unauthenticated or token expired: clear local session and navigate to Login. After login, return to Create Intent with selected hotspot prefilled.
6. Optionally, user taps a hotspot to call `GET /api/v1/hotspots/{id}` for the detail view.

**What can go wrong:**

| Condition | Error Code | UI Reaction |
|-----------|-----------|-------------|
| Specific hotspot not found | `HOTSPOT_NOT_FOUND` | Show toast: "This hotspot is no longer available." |
| Network failure | — | Show cached hotspot list with a "Refresh" banner. |
| User taps "Create Intent" while not authenticated | — (client-side guard) | Navigate to Login/Auth flow instead of calling intent APIs. |

**Other activities:** Refresh hotspot list every time the screen gains focus or the user pulls to refresh.

**System state on completion:** Map is populated with live hotspot data. Intent creation is protected by auth gate (UC-15 requires authentication).
