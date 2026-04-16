package com.walkmate.domain.walkproposal;

import com.walkmate.domain.shared.DomainCallback;

import java.util.List;

public interface WalkProposalRepository {
    void getProposals(DomainCallback<List<WalkProposal>> callback);
    void acceptProposal(String proposalId, DomainCallback<WalkProposal> callback);
    void passProposal(String proposalId, DomainCallback<Void> callback);
    void cancelProposal(String proposalId, DomainCallback<Void> callback);
}
