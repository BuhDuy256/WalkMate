package com.walkmate.infrastructure.repository.proposal;

import com.walkmate.domain.proposal.MatchProposal;
import com.walkmate.domain.proposal.MatchProposalRepository;
import com.walkmate.domain.proposal.ProposalErrorCode;
import com.walkmate.domain.proposal.ProposalStatus;
import com.walkmate.domain.shared.exception.DomainException;
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
        if (proposal.getVersion() == 0) {
            // Fresh proposal — INSERT with version = 0
            final String insertSql = """
                    INSERT INTO match_proposal (
                        proposal_id, intent_id_a, intent_id_b,
                        proposed_start_time, proposed_end_time,
                        hotspot_id,
                        accepted_by_a, accepted_by_b,
                        status, created_at, expires_at, confirmed_at, version
                    )
                    VALUES (
                        :proposalId, :intentIdA, :intentIdB,
                        :proposedStartTime, :proposedEndTime,
                        :hotspotId,
                        :acceptedByA, :acceptedByB,
                        CAST(:status AS proposal_status), :createdAt, :expiresAt, :confirmedAt, 0
                    )
                    """;

            jdbcClient.sql(insertSql)
                    .param("proposalId",        UUID.fromString(proposal.getProposalId()))
                    .param("intentIdA",         UUID.fromString(proposal.getIntentIdA()))
                    .param("intentIdB",         UUID.fromString(proposal.getIntentIdB()))
                    .param("proposedStartTime", Timestamp.from(proposal.getProposedStartTime()))
                    .param("proposedEndTime",   Timestamp.from(proposal.getProposedEndTime()))
                    .param("hotspotId",         UUID.fromString(proposal.getHotspotId()))
                    .param("acceptedByA",       proposal.isAcceptedByA())
                    .param("acceptedByB",       proposal.isAcceptedByB())
                    .param("status",            proposal.getStatus().name())
                    .param("createdAt",         Timestamp.from(proposal.getCreatedAt()))
                    .param("expiresAt",         Timestamp.from(proposal.getExpiresAt()))
                    .param("confirmedAt",       proposal.getConfirmedAt() != null
                            ? Timestamp.from(proposal.getConfirmedAt()) : null)
                    .update();
        } else {
            // Existing proposal — UPDATE with OCC guard (X-5)
            // Domain methods already incremented version; DB holds version - 1.
            final String updateSql = """
                    UPDATE match_proposal
                    SET accepted_by_a = :acceptedByA,
                        accepted_by_b = :acceptedByB,
                        status        = CAST(:status AS proposal_status),
                        confirmed_at  = :confirmedAt,
                        version       = version + 1
                    WHERE proposal_id = :proposalId
                      AND version     = :expectedVersion
                    """;

            int rows = jdbcClient.sql(updateSql)
                    .param("proposalId",       UUID.fromString(proposal.getProposalId()))
                    .param("acceptedByA",      proposal.isAcceptedByA())
                    .param("acceptedByB",      proposal.isAcceptedByB())
                    .param("status",           proposal.getStatus().name())
                    .param("confirmedAt",      proposal.getConfirmedAt() != null
                            ? Timestamp.from(proposal.getConfirmedAt()) : null)
                    .param("expectedVersion",  proposal.getVersion() - 1)
                    .update();

            if (rows == 0) {
                throw new DomainException(ProposalErrorCode.PROPOSAL_CONCURRENT_MODIFICATION);
            }
        }

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

    @Override
    public List<MatchProposal> findExpiredPending() {
        final String sql = selectWithJoin() + """
                WHERE mp.status = 'PENDING'
                  AND mp.expires_at < NOW()
                ORDER BY mp.expires_at ASC
                """;

        return jdbcClient.sql(sql)
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
                    mp.hotspot_id::text,
                    h.name             AS hotspot_name,
                    mp.proposed_start_time,
                    mp.proposed_end_time,
                    mp.accepted_by_a,
                    mp.accepted_by_b,
                    mp.status,
                    mp.created_at,
                    mp.expires_at,
                    mp.confirmed_at,
                    mp.version
                FROM match_proposal mp
                JOIN walk_intent wi_a ON mp.intent_id_a = wi_a.intent_id
                JOIN walk_intent wi_b ON mp.intent_id_b = wi_b.intent_id
                JOIN hotspot h        ON h.id = mp.hotspot_id
                """;
    }

    private MatchProposal mapRow(ResultSet rs) throws SQLException {
        return new MatchProposal(
                rs.getString("proposal_id"),
                rs.getString("intent_id_a"),
                rs.getString("intent_id_b"),
                rs.getString("user_id_a"),
                rs.getString("user_id_b"),
                rs.getString("hotspot_id"),
                rs.getString("hotspot_name"),
                rs.getTimestamp("proposed_start_time").toInstant(),
                rs.getTimestamp("proposed_end_time").toInstant(),
                rs.getBoolean("accepted_by_a"),
                rs.getBoolean("accepted_by_b"),
                ProposalStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("expires_at").toInstant(),
                rs.getTimestamp("confirmed_at") != null
                        ? rs.getTimestamp("confirmed_at").toInstant() : null,
                rs.getLong("version")
        );
    }
}
