package com.walkmate.ui.social.blocked;

import com.walkmate.domain.social.UserSummary;

import java.util.Collections;
import java.util.List;

/**
 * Immutable state snapshot for the Blocked Users screen.
 */
public class BlockedUsersUiState {

    private final boolean isLoading;
    private final String error;
    private final List<UserSummary> blockedUsers;

    public BlockedUsersUiState(boolean isLoading, String error, List<UserSummary> blockedUsers) {
        this.isLoading    = isLoading;
        this.error        = error;
        this.blockedUsers = blockedUsers != null ? blockedUsers : Collections.emptyList();
    }

    public static BlockedUsersUiState loading() {
        return new BlockedUsersUiState(true, null, null);
    }

    public static BlockedUsersUiState error(String msg) {
        return new BlockedUsersUiState(false, msg, null);
    }

    public boolean isLoading()                 { return isLoading; }
    public String getError()                   { return error; }
    public List<UserSummary> getBlockedUsers() { return blockedUsers; }
}
