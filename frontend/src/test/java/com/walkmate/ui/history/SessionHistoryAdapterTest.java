package com.walkmate.ui.history;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;

import androidx.test.core.app.ApplicationProvider;

import com.walkmate.domain.walksession.ParticipantSummary;
import com.walkmate.domain.walksession.SessionSummary;
import com.walkmate.domain.walksession.WalkSession;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(RobolectricTestRunner.class)
public class SessionHistoryAdapterTest {

  private Context context;
  private Locale originalLocale;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();
    originalLocale = Locale.getDefault();
    Locale.setDefault(Locale.US);
  }

  @After
  public void tearDown() {
    Locale.setDefault(originalLocale);
  }

  @Test
  public void bind_completed_partnerCompleted_showsReviewAndReport_andEmitsClicks() {
    SessionHistoryAdapter adapter = new SessionHistoryAdapter();
    AtomicReference<String> reviewSessionId = new AtomicReference<>();
    AtomicReference<String> reportSessionId = new AtomicReference<>();
    AtomicReference<String> reportPartnerId = new AtomicReference<>();
    AtomicLong reportTerminalAt = new AtomicLong(0L);

    adapter.setOnReviewClickListener(reviewSessionId::set);
    adapter.setOnReportClickListener((sid, pid, terminalAtMs) -> {
      reportSessionId.set(sid);
      reportPartnerId.set(pid);
      reportTerminalAt.set(terminalAtMs);
    });

    SessionSummary summary = summary(
        "s-1",
        WalkSession.Status.COMPLETED,
        WalkSession.Status.COMPLETED,
        WalkSession.Status.COMPLETED,
        "me",
        "p-1",
        "Central Park",
        "2026-05-06T10:30:00Z",
        1_700_000_123_000L,
        2.0,
        75,
        1.25,
        40,
        "Pat");

    SessionHistoryAdapter.ViewHolder holder = bind(adapter, summary, "me");

    assertEquals(View.VISIBLE, holder.btnReview.getVisibility());
    assertEquals(View.VISIBLE, holder.btnReport.getVisibility());
    assertEquals(View.VISIBLE, holder.dividerAction.getVisibility());

    holder.btnReview.performClick();
    holder.btnReport.performClick();

    assertEquals("s-1", reviewSessionId.get());
    assertEquals("s-1", reportSessionId.get());
    assertEquals("p-1", reportPartnerId.get());
    assertEquals(1_700_000_123_000L, reportTerminalAt.get());
  }

  @Test
  public void bind_completed_partnerNotCompleted_showsReportOnly() {
    SessionHistoryAdapter adapter = new SessionHistoryAdapter();
    SessionSummary summary = summary(
        "s-2",
        WalkSession.Status.COMPLETED,
        WalkSession.Status.COMPLETED,
        WalkSession.Status.NO_SHOW,
        "me",
        "p-2",
        "Riverside",
        "2026-05-06T10:30:00Z",
        1_700_000_123_000L,
        2.0,
        75,
        1.25,
        40,
        "Pat");

    SessionHistoryAdapter.ViewHolder holder = bind(adapter, summary, "me");

    assertEquals(View.GONE, holder.btnReview.getVisibility());
    assertEquals(View.VISIBLE, holder.btnReport.getVisibility());
    assertEquals(View.VISIBLE, holder.dividerAction.getVisibility());
  }

  @Test
  public void bind_completed_callerNoShow_hidesAllActions() {
    SessionHistoryAdapter adapter = new SessionHistoryAdapter();
    SessionSummary summary = summary(
        "s-3",
        WalkSession.Status.COMPLETED,
        WalkSession.Status.NO_SHOW,
        WalkSession.Status.COMPLETED,
        "me",
        "p-3",
        "Riverside",
        "2026-05-06T10:30:00Z",
        1_700_000_123_000L,
        2.0,
        75,
        1.25,
        40,
        "Pat");

    SessionHistoryAdapter.ViewHolder holder = bind(adapter, summary, "me");

    assertEquals(View.GONE, holder.btnReview.getVisibility());
    assertEquals(View.GONE, holder.btnReport.getVisibility());
    assertEquals(View.GONE, holder.dividerAction.getVisibility());
  }

  @Test
  public void bind_active_hidesAllActions() {
    SessionHistoryAdapter adapter = new SessionHistoryAdapter();
    SessionSummary summary = summary(
        "s-4",
        WalkSession.Status.ACTIVE,
        WalkSession.Status.ACTIVE,
        WalkSession.Status.ACTIVE,
        "me",
        "p-4",
        "Riverside",
        "2026-05-06T10:30:00Z",
        1_700_000_123_000L,
        2.0,
        75,
        1.25,
        40,
        "Pat");

    SessionHistoryAdapter.ViewHolder holder = bind(adapter, summary, "me");

    assertEquals(View.GONE, holder.btnReview.getVisibility());
    assertEquals(View.GONE, holder.btnReport.getVisibility());
    assertEquals(View.GONE, holder.dividerAction.getVisibility());
  }

  @Test
  public void bind_formatsDateHotspotAndRows() {
    SessionHistoryAdapter adapter = new SessionHistoryAdapter();
    SessionSummary summary = summary(
        "s-5",
        WalkSession.Status.COMPLETED,
        WalkSession.Status.COMPLETED,
        WalkSession.Status.COMPLETED,
        "me",
        "p-5",
        "",
        "2026-05-06T10:30:00Z",
        1_700_000_123_000L,
        2.0,
        50,
        1.25,
        40,
        "");

    SessionHistoryAdapter.ViewHolder holder = bind(adapter, summary, "me");

    assertEquals("2026-05-06", holder.txtDate.getText().toString());
    assertEquals("—", holder.txtHotspotName.getText().toString());
    assertEquals("Unknown", holder.txtParticipant2Name.getText().toString());
    assertEquals("You", holder.txtParticipant1Name.getText().toString());
    assertEquals("Completed", holder.txtParticipant1Status.getText().toString());
  }

  @Test
  public void bind_formatsDistanceAndDuration() {
    SessionHistoryAdapter adapter = new SessionHistoryAdapter();
    SessionSummary summary = summary(
        "s-6",
        WalkSession.Status.COMPLETED,
        WalkSession.Status.COMPLETED,
        WalkSession.Status.COMPLETED,
        "me",
        "p-6",
        "Central Park",
        "2026-05-06T10:30:00Z",
        1_700_000_123_000L,
        2.5,
        75,
        1.2,
        50,
        "Pat");

    SessionHistoryAdapter.ViewHolder holder = bind(adapter, summary, "me");

    assertEquals("2.50 km", holder.txtParticipant1Distance.getText().toString());
    assertEquals("1h 15m", holder.txtParticipant1Duration.getText().toString());
    assertEquals("1.20 km", holder.txtParticipant2Distance.getText().toString());
    assertEquals("50 min", holder.txtParticipant2Duration.getText().toString());
  }

  @Test
  public void click_sessionCard_emitsSessionId() {
    SessionHistoryAdapter adapter = new SessionHistoryAdapter();
    AtomicReference<String> selectedId = new AtomicReference<>();
    adapter.setOnSessionSelectedListener(selectedId::set);

    SessionSummary summary = summary(
        "s-7",
        WalkSession.Status.COMPLETED,
        WalkSession.Status.COMPLETED,
        WalkSession.Status.COMPLETED,
        "me",
        "p-7",
        "Central Park",
        "2026-05-06T10:30:00Z",
        1_700_000_123_000L,
        2.0,
        60,
        1.0,
        30,
        "Pat");

    SessionHistoryAdapter.ViewHolder holder = bind(adapter, summary, "me");
    holder.itemView.performClick();

    assertEquals("s-7", selectedId.get());
  }

  @Test
  public void click_partnerNameAndAvatar_emitsPartnerId() {
    SessionHistoryAdapter adapter = new SessionHistoryAdapter();
    AtomicInteger clickCount = new AtomicInteger(0);
    AtomicReference<String> partnerId = new AtomicReference<>();

    adapter.setOnPartnerClickListener(id -> {
      clickCount.incrementAndGet();
      partnerId.set(id);
    });

    SessionSummary summary = summary(
        "s-8",
        WalkSession.Status.COMPLETED,
        WalkSession.Status.COMPLETED,
        WalkSession.Status.COMPLETED,
        "me",
        "p-8",
        "Central Park",
        "2026-05-06T10:30:00Z",
        1_700_000_123_000L,
        2.0,
        60,
        1.0,
        30,
        "Pat");

    SessionHistoryAdapter.ViewHolder holder = bind(adapter, summary, "me");
    holder.txtParticipant2Name.performClick();
    holder.avatarPartner.performClick();

    assertEquals(2, clickCount.get());
    assertEquals("p-8", partnerId.get());
  }

  @Test
  public void click_partnerViews_whenMissingPartner_doesNotEmit() {
    SessionHistoryAdapter adapter = new SessionHistoryAdapter();
    AtomicInteger clickCount = new AtomicInteger(0);

    adapter.setOnPartnerClickListener(id -> clickCount.incrementAndGet());

    SessionSummary summary = summaryWithSingleParticipant(
        "s-9",
        WalkSession.Status.COMPLETED,
        WalkSession.Status.COMPLETED,
        "me",
        "Central Park",
        "2026-05-06T10:30:00Z",
        1_700_000_123_000L,
        2.0,
        60);

    SessionHistoryAdapter.ViewHolder holder = bind(adapter, summary, "me");
    holder.txtParticipant2Name.performClick();
    holder.avatarPartner.performClick();

    assertEquals(0, clickCount.get());
  }

  @Test
  public void bind_shortDate_showsDash() {
    SessionHistoryAdapter adapter = new SessionHistoryAdapter();
    SessionSummary summary = summary(
        "s-10",
        WalkSession.Status.COMPLETED,
        WalkSession.Status.COMPLETED,
        WalkSession.Status.COMPLETED,
        "me",
        "p-10",
        "Central Park",
        "2026-05",
        1_700_000_123_000L,
        2.0,
        60,
        1.0,
        30,
        "Pat");

    SessionHistoryAdapter.ViewHolder holder = bind(adapter, summary, "me");

    assertEquals("—", holder.txtDate.getText().toString());
  }

  @Test
  public void bind_cancelled_setsStatusBadgeText() {
    SessionHistoryAdapter adapter = new SessionHistoryAdapter();
    SessionSummary summary = summary(
        "s-11",
        WalkSession.Status.CANCELLED,
        WalkSession.Status.CANCELLED,
        WalkSession.Status.CANCELLED,
        "me",
        "p-11",
        "Central Park",
        "2026-05-06T10:30:00Z",
        1_700_000_123_000L,
        2.0,
        60,
        1.0,
        30,
        "Pat");

    SessionHistoryAdapter.ViewHolder holder = bind(adapter, summary, "me");

    assertEquals("CANCELLED", holder.txtStatus.getText().toString());
  }

  private SessionHistoryAdapter.ViewHolder bind(
      SessionHistoryAdapter adapter,
      SessionSummary summary,
      String currentUserId) {
    adapter.setCurrentUserId(currentUserId);
    adapter.submitList(List.of(summary));
    Shadows.shadowOf(Looper.getMainLooper()).idle();

    FrameLayout parent = new FrameLayout(context);
    SessionHistoryAdapter.ViewHolder holder = adapter.onCreateViewHolder(parent, 0);
    adapter.onBindViewHolder(holder, 0);
    return holder;
  }

  private static SessionSummary summary(
      String sessionId,
      WalkSession.Status globalStatus,
      WalkSession.Status callerStatus,
      WalkSession.Status partnerStatus,
      String currentUserId,
      String partnerId,
      String hotspotName,
      String scheduledStart,
      long terminalAtMs,
      double callerDistance,
      int callerDuration,
      double partnerDistance,
      int partnerDuration,
      String partnerName) {
    ParticipantSummary caller = new ParticipantSummary(
        currentUserId,
        "Me",
        "https://example.com/me.png",
        callerDistance,
        callerDuration,
        callerStatus);
    ParticipantSummary partner = new ParticipantSummary(
        partnerId,
        partnerName,
        "https://example.com/partner.png",
        partnerDistance,
        partnerDuration,
        partnerStatus);
    return new SessionSummary(
        sessionId,
        globalStatus,
        scheduledStart,
        false,
        false,
        null,
        null,
        terminalAtMs,
        10.0,
        20.0,
        hotspotName,
        List.of(caller, partner));
  }

  private static SessionSummary summaryWithSingleParticipant(
      String sessionId,
      WalkSession.Status globalStatus,
      WalkSession.Status callerStatus,
      String currentUserId,
      String hotspotName,
      String scheduledStart,
      long terminalAtMs,
      double callerDistance,
      int callerDuration) {
    ParticipantSummary caller = new ParticipantSummary(
        currentUserId,
        "Me",
        "https://example.com/me.png",
        callerDistance,
        callerDuration,
        callerStatus);
    return new SessionSummary(
        sessionId,
        globalStatus,
        scheduledStart,
        false,
        false,
        null,
        null,
        terminalAtMs,
        10.0,
        20.0,
        hotspotName,
        List.of(caller));
  }
}
