package com.walkmate.ui.chatroom;

/**
 * Display-only data extracted from the Intent extras and passed to ViewModel.init().
 * Holds the static context for the screen (partner info, session state).
 */
public class ChatroomViewData {

    private final String sessionId;
    private final String partnerName;
    private final String partnerAvatarUrl;
    private final boolean isSessionPending;   // drives banner + countdown visibility
    private final long scheduledTimeEpochMs;  // 0 if not available

    public ChatroomViewData(
            String sessionId,
            String partnerName,
            String partnerAvatarUrl,
            boolean isSessionPending,
            long scheduledTimeEpochMs) {
        this.sessionId = sessionId;
        this.partnerName = partnerName;
        this.partnerAvatarUrl = partnerAvatarUrl;
        this.isSessionPending = isSessionPending;
        this.scheduledTimeEpochMs = scheduledTimeEpochMs;
    }

    public String getSessionId()            { return sessionId; }
    public String getPartnerName()          { return partnerName; }
    public String getPartnerAvatarUrl()     { return partnerAvatarUrl; }
    public boolean isSessionPending()       { return isSessionPending; }
    public long getScheduledTimeEpochMs()   { return scheduledTimeEpochMs; }
}
