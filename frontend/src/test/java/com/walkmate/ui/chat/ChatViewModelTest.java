package com.walkmate.ui.chat;

import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;

import com.walkmate.domain.chat.ChatMessage;
import com.walkmate.domain.chat.ChatRepository;
import com.walkmate.domain.shared.DomainCallback;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class ChatViewModelTest {

  @Rule
  public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

  @Mock
  private ChatRepository chatRepository;

  private ChatViewModel viewModel;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    viewModel = new ChatViewModel(chatRepository);
  }

  @Test
  public void startChat_loadsHistory_thenConnectsOnSuccess() {
    AtomicReference<DomainCallback<List<ChatMessage>>> cbRef = new AtomicReference<>();

    doAnswer(invocation -> {
      cbRef.set(invocation.getArgument(2));
      return null;
    }).when(chatRepository).loadHistory(eq("s-1"), eq("me"), any(DomainCallback.class));

    viewModel.startChat("s-1", "me");

    verify(chatRepository, times(1)).loadHistory(eq("s-1"), eq("me"), any(DomainCallback.class));
    verify(chatRepository, never()).connect(any(), any());

    cbRef.get().onSuccess(List.of(message("m-1", "Hello", 1000L, true)));

    verify(chatRepository, times(1)).connect("s-1", "me");
  }

  @Test
  public void startChat_loadsHistory_thenConnectsOnError() {
    AtomicReference<DomainCallback<List<ChatMessage>>> cbRef = new AtomicReference<>();

    doAnswer(invocation -> {
      cbRef.set(invocation.getArgument(2));
      return null;
    }).when(chatRepository).loadHistory(eq("s-2"), eq("me"), any(DomainCallback.class));

    viewModel.startChat("s-2", "me");

    verify(chatRepository, times(1)).loadHistory(eq("s-2"), eq("me"), any(DomainCallback.class));
    verify(chatRepository, never()).connect(any(), any());

    cbRef.get().onError(new Exception("HISTORY_FAIL"));

    verify(chatRepository, times(1)).connect("s-2", "me");
  }

  @Test
  public void sendMessage_ignoresNullOrBlank() {
    viewModel.sendMessage(null);
    viewModel.sendMessage(" ");
    viewModel.sendMessage("\n\t");

    verify(chatRepository, never()).sendMessage(any());
  }

  @Test
  public void sendMessage_trimsContent() {
    viewModel.sendMessage("  hello  ");

    verify(chatRepository, times(1)).sendMessage("hello");
  }

  @Test
  public void getMessages_delegatesToRepository() {
    MutableLiveData<List<ChatMessage>> liveData = new MutableLiveData<>();
    when(chatRepository.getMessages()).thenReturn(liveData);

    assertSame(liveData, viewModel.getMessages());
  }

  @Test
  public void getConnectionState_delegatesToRepository() {
    MutableLiveData<ChatRepository.ConnectionState> liveData = new MutableLiveData<>();
    when(chatRepository.getConnectionState()).thenReturn(liveData);

    assertSame(liveData, viewModel.getConnectionState());
  }

  @Test
  public void onCleared_disconnectsRepository() {
    viewModel.onCleared();

    verify(chatRepository, times(1)).disconnect();
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
