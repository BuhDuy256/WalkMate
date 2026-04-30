package com.walkmate.ui.chat;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsAnimationCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.walkmate.R;
import com.walkmate.WalkMateApplication;
import com.walkmate.core.designsystem.view.AvatarInitialView;
import com.walkmate.core.util.WindowInsetUtils;
import com.walkmate.domain.chat.ChatRepository;

import java.util.ArrayList;
import java.util.List;

public class ChatFragment extends Fragment {

    public static final String ARG_SESSION_ID      = "SESSION_ID";
    public static final String ARG_CURRENT_USER_ID = "CURRENT_USER_ID";
    public static final String ARG_PARTNER_NAME    = "PARTNER_NAME";
    public static final String ARG_PARTNER_AVATAR  = "PARTNER_AVATAR";
    public static final String ARG_HOTSPOT_NAME    = "HOTSPOT_NAME";

    private RecyclerView      recyclerView;
    private EditText          messageInput;
    private ImageButton       sendButton;
    private TextView          connectingLabel;
    private TextView          errorLabel;
    private AvatarInitialView avatarPartner;
    private TextView          txtPartnerName;
    private TextView          txtPartnerLocation;

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

        // Header: pad top by status-bar height so it never goes under the status bar.
        WindowInsetUtils.applyStatusBarPadding(view.findViewById(R.id.headerBar));

        // Keyboard (IME) + nav bar are handled together on the root view.
        // This is the Messenger pattern:
        //   - Root gets bottom padding = max(keyboard height, nav bar height)
        //   - inputRow (constrained to bottom of parent) floats above the keyboard
        //   - RecyclerView (between header and inputRow) shrinks to fill the gap
        //   - headerBar stays pinned at the top — never pushed off screen
        //
        // WindowInsetsAnimationCompat.Callback.onProgress() fires every frame during
        // the keyboard slide-in/out animation, giving smooth real-time movement.
        // DISPATCH_MODE_STOP prevents child views from receiving insets during the
        // animation (the header's top padding is constant, so no re-dispatch needed).
        ViewCompat.setWindowInsetsAnimationCallback(view,
            new WindowInsetsAnimationCompat.Callback(
                    WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_STOP) {
                @NonNull
                @Override
                public WindowInsetsCompat onProgress(
                        @NonNull WindowInsetsCompat insets,
                        @NonNull List<WindowInsetsAnimationCompat> runningAnimations) {
                    applyBottomInset(view, insets);
                    return insets;
                }
            }
        );
        // Also handle the static case: initial layout + keyboard already open on entry.
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            applyBottomInset(v, insets);
            return insets; // not consumed — headerBar's top-inset listener still fires
        });

        recyclerView       = view.findViewById(R.id.recyclerChat);
        messageInput       = view.findViewById(R.id.inputMessage);
        sendButton         = view.findViewById(R.id.btnSend);
        connectingLabel    = view.findViewById(R.id.txtConnecting);
        errorLabel         = view.findViewById(R.id.txtError);
        avatarPartner      = view.findViewById(R.id.avatarPartner);
        txtPartnerName     = view.findViewById(R.id.txtPartnerName);
        txtPartnerLocation = view.findViewById(R.id.txtPartnerLocation);

        view.findViewById(R.id.btnBack).setOnClickListener(
                v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());

        view.findViewById(R.id.btnAttach).setOnClickListener(v -> { /* no-op placeholder */ });

        // Read args
        Bundle args        = getArguments();
        String sessionId     = args != null ? args.getString(ARG_SESSION_ID,      "")   : "";
        String currentUserId = args != null ? args.getString(ARG_CURRENT_USER_ID, "")   : "";
        String partnerName   = args != null ? args.getString(ARG_PARTNER_NAME,    "")   : "";
        String partnerAvatar = args != null ? args.getString(ARG_PARTNER_AVATAR,  null) : null;
        String hotspotName   = args != null ? args.getString(ARG_HOTSPOT_NAME,    "")   : "";

        // Bind header
        avatarPartner.bind(partnerName.isEmpty() ? "?" : partnerName, partnerAvatar);
        txtPartnerName.setText(partnerName);
        txtPartnerLocation.setText(hotspotName);

        // RecyclerView
        chatAdapter = new ChatAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(chatAdapter);

        // Dismiss keyboard when the user taps anywhere in the message list.
        recyclerView.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) hideKeyboard();
                return false;
            }
        });

        // When the RecyclerView shrinks (keyboard appears), scroll to keep the last
        // message visible — same behaviour as Messenger.
        recyclerView.addOnLayoutChangeListener(
            (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                if (bottom < oldBottom) {
                    int last = chatAdapter.getItemCount() - 1;
                    if (last >= 0) recyclerView.post(() -> recyclerView.scrollToPosition(last));
                }
            }
        );

        // ViewModel
        WalkMateApplication app = (WalkMateApplication) requireActivity().getApplication();
        chatViewModel = new ViewModelProvider(this,
                new ChatViewModelFactory(app.getChatRepository()))
                .get(ChatViewModel.class);

        chatViewModel.startChat(sessionId, currentUserId);

        chatViewModel.getMessages().observe(getViewLifecycleOwner(), messages -> {
            chatAdapter.submitList(new ArrayList<>(messages));
            if (!messages.isEmpty()) {
                recyclerView.scrollToPosition(messages.size() - 1);
            }
        });

        chatViewModel.getConnectionState().observe(getViewLifecycleOwner(), state -> {
            connectingLabel.setVisibility(
                    (state == ChatRepository.ConnectionState.CONNECTING ||
                     state == ChatRepository.ConnectionState.RECONNECTING)
                            ? View.VISIBLE : View.GONE);
            errorLabel.setVisibility(
                    state == ChatRepository.ConnectionState.ERROR ? View.VISIBLE : View.GONE);
        });

        // Send button visual state driven by whether the input has text.
        messageInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                boolean hasText = s != null && s.length() > 0;
                messageInput.setBackground(requireContext().getDrawable(
                        hasText ? R.drawable.bg_chat_input_active : R.drawable.bg_chat_input_idle));
                sendButton.setBackground(requireContext().getDrawable(
                        hasText ? R.drawable.bg_send_active : R.drawable.bg_send_idle));
                sendButton.setColorFilter(hasText ? Color.WHITE : Color.parseColor("#C9C5C1"));
            }
        });
        sendButton.setColorFilter(Color.parseColor("#C9C5C1"));

        sendButton.setOnClickListener(v -> {
            String text = messageInput.getText().toString().trim();
            if (!text.isEmpty()) {
                chatViewModel.sendMessage(text);
                messageInput.setText("");
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Sets the root view's bottom padding to max(keyboard height, nav bar height).
     * Called both during keyboard animation frames and on static inset changes.
     */
    private static void applyBottomInset(View root, WindowInsetsCompat insets) {
        Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
        Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
        root.setPadding(0, 0, 0, Math.max(ime.bottom, sys.bottom));
    }

    private void hideKeyboard() {
        View focused = requireActivity().getCurrentFocus();
        if (focused != null) {
            InputMethodManager imm = (InputMethodManager) requireContext()
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(focused.getWindowToken(), 0);
            focused.clearFocus();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        recyclerView.setAdapter(null);
        recyclerView       = null;
        messageInput       = null;
        sendButton         = null;
        connectingLabel    = null;
        errorLabel         = null;
        avatarPartner      = null;
        txtPartnerName     = null;
        txtPartnerLocation = null;
        chatAdapter        = null;
    }
}
