package com.walkmate.ui.chat;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.walkmate.R;
import com.walkmate.WalkMateApplication;
import com.walkmate.domain.chat.ChatRepository;

import java.util.ArrayList;

public class ChatFragment extends Fragment {

    public static final String ARG_SESSION_ID      = "SESSION_ID";
    public static final String ARG_CURRENT_USER_ID = "CURRENT_USER_ID";

    private RecyclerView recyclerView;
    private EditText     messageInput;
    private ImageButton  sendButton;
    private TextView     connectingLabel;
    private TextView     errorLabel;

    private ChatAdapter   chatAdapter;
    private ChatViewModel chatViewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_chat, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView    = view.findViewById(R.id.recyclerChat);
        messageInput    = view.findViewById(R.id.inputMessage);
        sendButton      = view.findViewById(R.id.btnSend);
        connectingLabel = view.findViewById(R.id.txtConnecting);
        errorLabel      = view.findViewById(R.id.txtError);

        // Back button
        View btnBack = view.findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        }

        // RecyclerView
        chatAdapter = new ChatAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(chatAdapter);

        // ViewModel
        WalkMateApplication app = (WalkMateApplication) requireActivity().getApplication();
        chatViewModel = new ViewModelProvider(this,
                new ChatViewModelFactory(app.getChatRepository()))
                .get(ChatViewModel.class);

        // Read args
        Bundle args = getArguments();
        String sessionId     = args != null ? args.getString(ARG_SESSION_ID, "")     : "";
        String currentUserId = args != null ? args.getString(ARG_CURRENT_USER_ID, "") : "";

        chatViewModel.startChat(sessionId, currentUserId);

        // Observe messages
        chatViewModel.getMessages().observe(getViewLifecycleOwner(), messages -> {
            chatAdapter.submitList(new ArrayList<>(messages));
            if (!messages.isEmpty()) {
                recyclerView.scrollToPosition(messages.size() - 1);
            }
        });

        // Observe connection state
        chatViewModel.getConnectionState().observe(getViewLifecycleOwner(), state -> {
            connectingLabel.setVisibility(
                    (state == ChatRepository.ConnectionState.CONNECTING ||
                     state == ChatRepository.ConnectionState.RECONNECTING)
                            ? View.VISIBLE : View.GONE);
            errorLabel.setVisibility(
                    state == ChatRepository.ConnectionState.ERROR ? View.VISIBLE : View.GONE);
        });

        // Send button
        sendButton.setOnClickListener(v -> {
            String text = messageInput.getText().toString().trim();
            if (!text.isEmpty()) {
                chatViewModel.sendMessage(text);
                messageInput.setText("");
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        recyclerView.setAdapter(null);
        recyclerView    = null;
        messageInput    = null;
        sendButton      = null;
        connectingLabel = null;
        errorLabel      = null;
        chatAdapter     = null;
    }
}
