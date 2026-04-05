1. Create Intent Flow

Gửi request lấy tất cả Hotspot và openIntentCount.

- Backend: 
    - GET /api/v1/hotspots => getAllHotspot
    - GET /api/v1/hotspots/{id} => getHotspotById
- Frontend:
    - Didn't render Hotspot in the first time click "Create a match button".
        => Error related to call API at the wrong time.
    