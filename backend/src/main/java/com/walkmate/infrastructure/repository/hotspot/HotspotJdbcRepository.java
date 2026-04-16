package com.walkmate.infrastructure.repository.hotspot;

import com.walkmate.domain.hotspot.Hotspot;
import com.walkmate.domain.hotspot.HotspotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class HotspotJdbcRepository implements HotspotRepository {
    private static final String FIND_ALL_SQL = """
            SELECT
                    h.id::text AS id,
                    h.name,
                    h.lat,
                    h.lng,
                    COUNT(wi.intent_id) FILTER (WHERE wi.status = 'OPEN') AS open_intent_count
            FROM hotspot h
            LEFT JOIN walk_intent wi ON wi.hotspot_id = h.id
            GROUP BY h.id, h.name, h.lat, h.lng
            ORDER BY h.name
            """;

    private static final String FIND_BY_ID_SQL = """
            SELECT
                    h.id::text AS id,
                    h.name,
                    h.lat,
                    h.lng,
                    COUNT(wi.intent_id) FILTER (WHERE wi.status = 'OPEN') AS open_intent_count
            FROM hotspot h
            LEFT JOIN walk_intent wi ON wi.hotspot_id = h.id
            WHERE h.id = :id
            GROUP BY h.id, h.name, h.lat, h.lng
            """;

    private final JdbcClient jdbcClient;

    /**
     * active_intent_count is computed inline to avoid a stored counter
     * that could drift out of sync with the actual walk_intent rows.
     */
    @Override
    public List<Hotspot> findAll() {
        return queryAll(FIND_ALL_SQL);
    }

    @Override
    public Optional<Hotspot> findById(String id) {
        UUID hotspotId = UUID.fromString(id);
        return queryById(FIND_BY_ID_SQL, hotspotId);
    }

    private List<Hotspot> queryAll(String sql) {
        return jdbcClient.sql(sql)
                .query((rs, rowNum) -> new Hotspot(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getDouble("lat"),
                        rs.getDouble("lng"),
                        rs.getInt("open_intent_count")))
                .list();
    }

    private Optional<Hotspot> queryById(String sql, UUID hotspotId) {
        return jdbcClient.sql(sql)
                .param("id", hotspotId)
                .query((rs, rowNum) -> new Hotspot(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getDouble("lat"),
                        rs.getDouble("lng"),
                        rs.getInt("open_intent_count")))
                .optional();
    }
}
