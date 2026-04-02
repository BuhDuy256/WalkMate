package com.walkmate.infrastructure.repository.hotspot;

import com.walkmate.domain.hotspot.Hotspot;
import com.walkmate.domain.hotspot.HotspotRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class HotspotJdbcRepository implements HotspotRepository {

        private static final Logger log = LoggerFactory.getLogger(HotspotJdbcRepository.class);

        private static final String FIND_ALL_WITH_SESSION_SQL = """
                        SELECT
                                h.id::text AS id,
                                h.name,
                                h.lat,
                                h.lng,
                                COALESCE(oi.open_intent_count, 0) + COALESCE(sc.session_walker_count, 0) AS active_walker_count
                        FROM hotspot h
                        LEFT JOIN (
                                SELECT wi.hotspot_id, COUNT(*)::int AS open_intent_count
                                FROM walk_intent wi
                                WHERE wi.status = 'OPEN'
                                GROUP BY wi.hotspot_id
                        ) oi ON oi.hotspot_id = h.id
                        LEFT JOIN (
                                SELECT ia.hotspot_id, (COUNT(*) * 2)::int AS session_walker_count
                                FROM walk_session ws
                                JOIN walk_intent ia ON ia.intent_id = ws.source_intent_id_a
                                JOIN walk_intent ib ON ib.intent_id = ws.source_intent_id_b
                                WHERE ws.status IN ('PENDING', 'ACTIVE')
                                  AND ia.hotspot_id = ib.hotspot_id
                                GROUP BY ia.hotspot_id
                        ) sc ON sc.hotspot_id = h.id
                        ORDER BY h.name
                        """;

        private static final String FIND_BY_ID_WITH_SESSION_SQL = """
                        SELECT
                                h.id::text AS id,
                                h.name,
                                h.lat,
                                h.lng,
                                COALESCE(oi.open_intent_count, 0) + COALESCE(sc.session_walker_count, 0) AS active_walker_count
                        FROM hotspot h
                        LEFT JOIN (
                                SELECT wi.hotspot_id, COUNT(*)::int AS open_intent_count
                                FROM walk_intent wi
                                WHERE wi.status = 'OPEN'
                                GROUP BY wi.hotspot_id
                        ) oi ON oi.hotspot_id = h.id
                        LEFT JOIN (
                                SELECT ia.hotspot_id, (COUNT(*) * 2)::int AS session_walker_count
                                FROM walk_session ws
                                JOIN walk_intent ia ON ia.intent_id = ws.source_intent_id_a
                                JOIN walk_intent ib ON ib.intent_id = ws.source_intent_id_b
                                WHERE ws.status IN ('PENDING', 'ACTIVE')
                                  AND ia.hotspot_id = ib.hotspot_id
                                GROUP BY ia.hotspot_id
                        ) sc ON sc.hotspot_id = h.id
                        WHERE h.id = :id
                        """;

        private static final String FIND_ALL_FALLBACK_SQL = """
                        SELECT
                                h.id::text AS id,
                                h.name,
                                h.lat,
                                h.lng,
                                COUNT(wi.intent_id) FILTER (WHERE wi.status = 'OPEN') AS active_walker_count
                        FROM hotspot h
                        LEFT JOIN walk_intent wi ON wi.hotspot_id = h.id
                        GROUP BY h.id, h.name, h.lat, h.lng
                        ORDER BY h.name
                        """;

        private static final String FIND_BY_ID_FALLBACK_SQL = """
                        SELECT
                                h.id::text AS id,
                                h.name,
                                h.lat,
                                h.lng,
                                COUNT(wi.intent_id) FILTER (WHERE wi.status = 'OPEN') AS active_walker_count
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
                try {
                        return queryAll(FIND_ALL_WITH_SESSION_SQL);
                } catch (BadSqlGrammarException ex) {
                        log.warn("walk_session or expected columns not available yet; fallback to open-intent counting", ex);
                        return queryAll(FIND_ALL_FALLBACK_SQL);
                }
    }

    @Override
    public Optional<Hotspot> findById(String id) {
        UUID hotspotId = UUID.fromString(id);
        try {
            return queryById(FIND_BY_ID_WITH_SESSION_SQL, hotspotId);
        } catch (BadSqlGrammarException ex) {
            log.warn("walk_session or expected columns not available yet; fallback to open-intent counting", ex);
            return queryById(FIND_BY_ID_FALLBACK_SQL, hotspotId);
        }
    }

    private List<Hotspot> queryAll(String sql) {
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

    private Optional<Hotspot> queryById(String sql, UUID hotspotId) {
        return jdbcClient.sql(sql)
                .param("id", hotspotId)
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
