package com.walkmate.infrastructure.repository.proposal;

import com.walkmate.domain.proposal.MatchProposal;
import com.walkmate.domain.proposal.MatchProposalRepository;
import com.walkmate.domain.proposal.ProposalStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class MatchProposalJdbcRepository implements MatchProposalRepository {

    private final JdbcClient jdbcClient;

    @Override
    public MatchProposal save(MatchProposal proposal) {
        final String sql = """
                INSERT INTO match_proposal (
                    proposal_id, intent_id_a, intent_id_b,
                    proposed_start_time, proposed_end_time,
                    proposed_location_lat, proposed_location_lng,
                    accepted_by_a, accepted_by_b,
                    status, created_at, expires_at, confirmed_at
                )
                VALUES (
                    :proposalId, :intentIdA, :intentIdB,
                    :proposedStartTime, :proposedEndTime,
                    :proposedLocationLat, :proposedLocationLng,
                    :acceptedByA, :acceptedByB,
                    CAST(:status AS proposal_status), :createdAt, :expiresAt, :confirmedAt
                )
                ON CONFLICT (proposal_id) DO UPDATE SET
                    accepted_by_a    = EXCLUDED.accepted_by_a,
                    accepted_by_b    = EXCLUDED.accepted_by_b,
                    status           = CAST(EXCLUDED.status AS proposal_status),
                    confirmed_at     = EXCLUDED.confirmed_at
                """;

        jdbcClient.sql(sql)
                .param("proposalId",          UUID.fromString(proposal.getProposalId()))
                .param("intentIdA",           UUID.fromString(proposal.getIntentIdA()))
                .param("intentIdB",           UUID.fromString(proposal.getIntentIdB()))
                .param("proposedStartTime",   Timestamp.from(proposal.getProposedStartTime()))
                .param("proposedEndTime",     Timestamp.from(proposal.getProposedEndTime()))
                .param("proposedLocationLat", proposal.getProposedLocationLat())
                .param("proposedLocationLng", proposal.getProposedLocationLng())
                .param("acceptedByA",         proposal.isAcceptedByA())
                .param("acceptedByB",         proposal.isAcceptedByB())
                .param("status",              proposal.getStatus().name())
                .param("createdAt",           Timestamp.from(proposal.getCreatedAt()))
                .param("expiresAt",           Timestamp.from(proposal.getExpiresAt()))
                .param("confirmedAt",         proposal.getConfirmedAt() != null
                        ? Timestamp.from(proposal.getConfirmedAt()) : null)
                .update();

        return proposal;
    }

    @Override
    public Optional<MatchProposal> findById(String id) {
        return jdbcClient.sql(selectWithJoin() + "WHERE mp.proposal_id = :proposalId")
                .param("proposalId", UUID.fromString(id))
                .query((rs, rowNum) -> mapRow(rs))
                .optional();
    }

    @Override
    public Optional<MatchProposal> findPendingByIntentId(String intentId) {
        final String sql = selectWithJoin() + """
                WHERE (mp.intent_id_a = :intentId OR mp.intent_id_b = :intentId)
                  AND mp.status = 'PENDING'
                LIMIT 1
                """;

        return jdbcClient.sql(sql)
                .param("intentId", UUID.fromString(intentId))
                .query((rs, rowNum) -> mapRow(rs))
                .optional();
    }

    @Override
    public List<MatchProposal> findPendingForUser(String userId) {
        final String sql = selectWithJoin() + """
                WHERE (wi_a.user_id = :userId OR wi_b.user_id = :userId)
                  AND mp.status = 'PENDING'
                ORDER BY mp.created_at DESC
                """;

        return jdbcClient.sql(sql)
                .param("userId", UUID.fromString(userId))
                .query((rs, rowNum) -> mapRow(rs))
                .list();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String selectWithJoin() {
        return """
                SELECT
                    mp.proposal_id::text,
                    mp.intent_id_a::text,
                    mp.intent_id_b::text,
                    wi_a.user_id::text AS user_id_a,
                    wi_b.user_id::text AS user_id_b,
                    mp.proposed_location_lat,
                    mp.proposed_location_lng,
                    mp.proposed_start_time,
                    mp.proposed_end_time,
                    mp.accepted_by_a,
                    mp.accepted_by_b,
                    mp.status,
                    mp.created_at,
                    mp.expires_at,
                    mp.confirmed_at
                FROM match_proposal mp
                JOIN walk_intent wi_a ON mp.intent_id_a = wi_a.intent_id
                JOIN walk_intent wi_b ON mp.intent_id_b = wi_b.intent_id
                """;
    }

    private MatchProposal mapRow(ResultSet rs) throws SQLException {
        return new MatchProposal(
                rs.getString("proposal_id"),
                rs.getString("intent_id_a"),
                rs.getString("intent_id_b"),
                rs.getString("user_id_a"),
                rs.getString("user_id_b"),
                rs.getDouble("proposed_location_lat"),
                rs.getDouble("proposed_location_lng"),
                rs.getTimestamp("proposed_start_time").toInstant(),
                rs.getTimestamp("proposed_end_time").toInstant(),
                rs.getBoolean("accepted_by_a"),
                rs.getBoolean("accepted_by_b"),
                ProposalStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("expires_at").toInstant(),
                rs.getTimestamp("confirmed_at") != null
                        ? rs.getTimestamp("confirmed_at").toInstant() : null
        );
    }
}
