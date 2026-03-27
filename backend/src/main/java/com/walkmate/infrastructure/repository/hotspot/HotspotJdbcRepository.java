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

    private final JdbcClient jdbcClient;

    /**
     * active_walker_count is computed inline to avoid a stored counter
     * that could drift out of sync with the actual walk_intent rows.
     */
    @Override
    public List<Hotspot> findAll() {
        final String sql = """
                SELECT
                    h.id::text,
                    h.name,
                    h.lat,
                    h.lng,
                    COUNT(wi.intent_id) FILTER (WHERE wi.status = 'OPEN') AS active_walker_count
                FROM hotspot h
                LEFT JOIN walk_intent wi ON wi.hotspot_id = h.id
                GROUP BY h.id, h.name, h.lat, h.lng
                ORDER BY h.name
                """;

        return jdbcClient.sql(sql)
                .query((rs, rowNum) -> new Hotspot(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getDouble("lat"),
                        rs.getDouble("lng"),
                        rs.getInt("active_walker_count")
                ))
                .list();
    }

    @Override
    public Optional<Hotspot> findById(String id) {
        final String sql = """
                SELECT
                    h.id::text,
                    h.name,
                    h.lat,
                    h.lng,
                    COUNT(wi.intent_id) FILTER (WHERE wi.status = 'OPEN') AS active_walker_count
                FROM hotspot h
                LEFT JOIN walk_intent wi ON wi.hotspot_id = h.id
                WHERE h.id = :id
                GROUP BY h.id, h.name, h.lat, h.lng
                """;

        return jdbcClient.sql(sql)
                .param("id", UUID.fromString(id))
                .query((rs, rowNum) -> new Hotspot(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getDouble("lat"),
                        rs.getDouble("lng"),
                        rs.getInt("active_walker_count")
                ))
                .optional();
    }
}
