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
5. Optionally, user taps a hotspot to call `GET /api/v1/hotspots/{id}` for the detail view.

**What can go wrong:**

| Condition | Error Code | UI Reaction |
|-----------|-----------|-------------|
| Specific hotspot not found | `HOTSPOT_NOT_FOUND` | Show toast: "This hotspot is no longer available." |
| Network failure | — | Show cached hotspot list with a "Refresh" banner. |

**Other activities:** Refresh hotspot list every time the screen gains focus or the user pulls to refresh.

**System state on completion:** Map is populated with live hotspot data. User can navigate to UC-15 to create an intent at a chosen hotspot.
