package com.walkmate.ui.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import com.walkmate.R;
import com.walkmate.domain.chat.ChatMessage;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

@RunWith(RobolectricTestRunner.class)
public class ChatAdapterTest {

  private Context context;
  private Locale originalLocale;
  private TimeZone originalTimeZone;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();
    originalLocale = Locale.getDefault();
    originalTimeZone = TimeZone.getDefault();
    Locale.setDefault(Locale.US);
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
  }

  @After
  public void tearDown() {
    Locale.setDefault(originalLocale);
    TimeZone.setDefault(originalTimeZone);
  }

  @Test
  public void submitList_null_setsEmptyCount() {
    ChatAdapter adapter = new ChatAdapter();
    adapter.submitList(null);

    assertEquals(0, adapter.getItemCount());
  }

  @Test
  public void getItemViewType_mineAndTheirs() {
    ChatAdapter adapter = new ChatAdapter();
    adapter.submitList(List.of(
        message("m-1", "Hello", 0L, true),
        message("m-2", "Hi", 0L, false)));

    assertEquals(0, adapter.getItemViewType(0));
    assertEquals(1, adapter.getItemViewType(1));
  }

  @Test
  public void bind_mine_setsContentTime_andHasReadReceipt() {
    ChatAdapter adapter = new ChatAdapter();
    ChatMessage msg = message("m-1", "Hello", 0L, true);

    ChatAdapter.MessageViewHolder holder = bind(adapter, msg);

    TextView content = holder.itemView.findViewById(R.id.txtContent);
    TextView time = holder.itemView.findViewById(R.id.txtTime);
    View readReceipt = holder.itemView.findViewById(R.id.txtReadReceipt);
    View senderName = holder.itemView.findViewById(R.id.txtSenderName);

    assertEquals("Hello", content.getText().toString());
    assertEquals("00:00", time.getText().toString());
    assertEquals(View.VISIBLE, readReceipt.getVisibility());
    assertNull(senderName);
  }

  @Test
  public void bind_theirs_showsSenderNameWhenProvided() {
    ChatAdapter adapter = new ChatAdapter();
    ChatMessage msg = message("m-2", "Hi", 0L, false);

    ChatAdapter.MessageViewHolder holder = bind(adapter, msg);

    TextView senderName = holder.itemView.findViewById(R.id.txtSenderName);
    TextView content = holder.itemView.findViewById(R.id.txtContent);
    TextView time = holder.itemView.findViewById(R.id.txtTime);
    View readReceipt = holder.itemView.findViewById(R.id.txtReadReceipt);

    assertEquals("Pat", senderName.getText().toString());
    assertEquals(View.VISIBLE, senderName.getVisibility());
    assertEquals("Hi", content.getText().toString());
    assertEquals("00:00", time.getText().toString());
    assertNull(readReceipt);
  }

  @Test
  public void bind_theirs_hidesSenderNameWhenMissing() {
    ChatAdapter adapter = new ChatAdapter();
    ChatMessage msg = new ChatMessage(
        "m-3",
        "s-1",
        "partner",
        "",
        "No name",
        0L,
        false);

    ChatAdapter.MessageViewHolder holder = bind(adapter, msg);

    TextView senderName = holder.itemView.findViewById(R.id.txtSenderName);

    assertEquals(View.GONE, senderName.getVisibility());
  }

  @Test
  public void bind_theirs_noReadReceipt() {
    ChatAdapter adapter = new ChatAdapter();
    ChatMessage msg = message("m-4", "Yo", 0L, false);

    ChatAdapter.MessageViewHolder holder = bind(adapter, msg);
    View readReceipt = holder.itemView.findViewById(R.id.txtReadReceipt);

    assertNull(readReceipt);
  }

  private ChatAdapter.MessageViewHolder bind(ChatAdapter adapter, ChatMessage message) {
    adapter.submitList(List.of(message));

    FrameLayout parent = new FrameLayout(context);
    int viewType = adapter.getItemViewType(0);
    ChatAdapter.MessageViewHolder holder = adapter.onCreateViewHolder(parent, viewType);
    adapter.onBindViewHolder(holder, 0);
    return holder;
  }

  private static ChatMessage message(String id, String content, long timestampMs, boolean fromMe) {
    return new ChatMessage(
        id,
        "s-1",
        fromMe ? "me" : "partner",
        fromMe ? "Me" : "Pat",
        content,
        timestampMs,
        fromMe);
  }
}
