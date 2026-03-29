package com.walkmate.domain.walkproposal;

import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.walksession.WalkSession;

import java.util.List;

public interface WalkProposalRepository {
    void getProposals(DomainCallback<List<WalkProposal>> callback);
    void acceptProposal(String proposalId, DomainCallback<WalkSession> callback);
    void passProposal(String proposalId, DomainCallback<Void> callback);
}
