package com.walkmate.ui.chatroom;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.walkmate.R;
import com.walkmate.WalkMateApplication;
import com.walkmate.core.designsystem.view.AvatarInitialView;
import com.walkmate.domain.chatroom.ChatRoomService;
import com.walkmate.ui.chatroom.component.ChatMessageAdapter;

/**
 * Full-screen chat screen for a WalkSession's ChatRoom.
 *
 * Launched from SessionFragment via createIntent(). Observes ChatroomViewModel
 * for UiState updates and UiEffect one-shot events.
 */
public class ChatroomActivity extends AppCompatActivity {

    // ── Intent extras ─────────────────────────────────────────────────────────

    public static final String EXTRA_SESSION_ID       = "extra_session_id";
    public static final String EXTRA_PARTNER_NAME     = "extra_partner_name";
    public static final String EXTRA_PARTNER_AVATAR   = "extra_partner_avatar";
    public static final String EXTRA_IS_PENDING       = "extra_is_pending";
    public static final String EXTRA_SCHEDULED_MS     = "extra_scheduled_ms";

    public static Intent createIntent(
            Context context,
            String sessionId,
            String partnerName,
            String partnerAvatarUrl,
            boolean isPending,
            long scheduledMs) {
        Intent intent = new Intent(context, ChatroomActivity.class);
        intent.putExtra(EXTRA_SESSION_ID,     sessionId);
        intent.putExtra(EXTRA_PARTNER_NAME,   partnerName);
        intent.putExtra(EXTRA_PARTNER_AVATAR, partnerAvatarUrl);
        intent.putExtra(EXTRA_IS_PENDING,     isPending);
        intent.putExtra(EXTRA_SCHEDULED_MS,   scheduledMs);
        return intent;
    }

    // ── Views ─────────────────────────────────────────────────────────────────

    private ProgressBar progressBar;
    private ImageButton btnBack;
    private TextView txtPartnerName;
    private TextView txtPartnerStatus;
    private TextView txtCountdownMini;

    private MaterialCardView layoutMatchBanner;
    private AvatarInitialView avatarPartner;
    private TextView txtBannerName;
    private TextView txtBannerMeta;
    private TextView txtCountdownLarge;
    private ImageButton btnDeclineBanner;

    private RecyclerView recyclerView;
    private LinearLayout layoutInput;
    private EditText editMessage;
    private ImageButton btnSend;
    private ImageButton btnEmoji;

    private HorizontalScrollView layoutQuickReplies;
    private TextView chipHey;
    private TextView chipWhere;
    private TextView chipOnMyWay;
    private TextView chipAlmost;

    // ── ViewModel & Adapter ───────────────────────────────────────────────────

    private ChatroomViewModel viewModel;
    private ChatMessageAdapter adapter;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chatroom);

        bindViews();
        setupViewModel();
        setupRecyclerView();
        setupClickListeners();

        ChatroomViewData viewData = extractViewData();
        viewModel.init(viewData);

        // ONLY place that writes to views
        viewModel.getUiState().observe(this, this::renderState);
        viewModel.getUiEffect().observe(this, this::handleEffect);
    }

    // ── Binding ───────────────────────────────────────────────────────────────

    private void bindViews() {
        progressBar      = findViewById(R.id.progressBar);
        btnBack          = findViewById(R.id.btnBack);
        txtPartnerName   = findViewById(R.id.txtPartnerName);
        txtPartnerStatus = findViewById(R.id.txtPartnerStatus);
        txtCountdownMini = findViewById(R.id.txtCountdownMini);

        layoutMatchBanner  = findViewById(R.id.layoutMatchBanner);
        avatarPartner      = findViewById(R.id.avatarPartner);
        txtBannerName      = findViewById(R.id.txtBannerName);
        txtBannerMeta      = findViewById(R.id.txtBannerMeta);
        txtCountdownLarge  = findViewById(R.id.txtCountdownLarge);
        btnDeclineBanner   = findViewById(R.id.btnDeclineBanner);

        recyclerView       = findViewById(R.id.recyclerView);
        layoutInput        = findViewById(R.id.layoutInput);
        editMessage        = findViewById(R.id.editMessage);
        btnSend            = findViewById(R.id.btnSend);
        btnEmoji           = findViewById(R.id.btnEmoji);

        layoutQuickReplies = findViewById(R.id.layoutQuickReplies);
        chipHey            = findViewById(R.id.chipHey);
        chipWhere          = findViewById(R.id.chipWhere);
        chipOnMyWay        = findViewById(R.id.chipOnMyWay);
        chipAlmost         = findViewById(R.id.chipAlmost);
    }

    private void setupViewModel() {
        WalkMateApplication app = (WalkMateApplication) getApplication();
        ChatRoomService service = new ChatRoomService(app.getChatRoomRepository());
        ChatroomViewModelFactory factory = new ChatroomViewModelFactory(service);
        viewModel = new ViewModelProvider(this, factory).get(ChatroomViewModel.class);
    }

    private void setupRecyclerView() {
        adapter = new ChatMessageAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);
    }

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> finish());

        btnSend.setOnClickListener(v -> sendCurrentMessage());

        editMessage.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendCurrentMessage();
                return true;
            }
            return false;
        });

        btnDeclineBanner.setOnClickListener(v ->
                layoutMatchBanner.setVisibility(View.GONE));

        // Quick reply chips
        chipHey.setOnClickListener(v -> viewModel.sendMessage(chipHey.getText().toString()));
        chipWhere.setOnClickListener(v -> viewModel.sendMessage(chipWhere.getText().toString()));
        chipOnMyWay.setOnClickListener(v -> viewModel.sendMessage(chipOnMyWay.getText().toString()));
        chipAlmost.setOnClickListener(v -> viewModel.sendMessage(chipAlmost.getText().toString()));
    }

    private ChatroomViewData extractViewData() {
        Intent intent = getIntent();
        return new ChatroomViewData(
                intent.getStringExtra(EXTRA_SESSION_ID),
                intent.getStringExtra(EXTRA_PARTNER_NAME),
                intent.getStringExtra(EXTRA_PARTNER_AVATAR),
                intent.getBooleanExtra(EXTRA_IS_PENDING, false),
                intent.getLongExtra(EXTRA_SCHEDULED_MS, 0L)
        );
    }

    // ── State rendering — ONLY place that writes to views ────────────────────

    private void renderState(ChatroomUiState state) {
        progressBar.setVisibility(state.isLoading() ? View.VISIBLE : View.GONE);

        if (state.isLoading()) return;

        // Partner header
        if (state.getPartner() != null) {
            txtPartnerName.setText(state.getPartner().partnerName);
            txtPartnerStatus.setText(state.getPartner().isOnline
                    ? getString(R.string.chatroom_status_online)
                    : getString(R.string.chatroom_status_offline));
            avatarPartner.bind(state.getPartner().partnerName, state.getPartner().partnerAvatarUrl);
            txtBannerName.setText(state.getPartner().partnerName);
            txtBannerMeta.setText(getString(R.string.chatroom_banner_meta));
        }

        // Match banner visibility
        layoutMatchBanner.setVisibility(state.isShowMatchBanner() ? View.VISIBLE : View.GONE);

        // Countdown (shown in header when banner is visible)
        txtCountdownMini.setVisibility(state.isShowMatchBanner() ? View.VISIBLE : View.GONE);

        // Input bar — hidden when room is closed
        layoutInput.setVisibility(state.isChatOpen() ? View.VISIBLE : View.GONE);
        layoutQuickReplies.setVisibility(state.isChatOpen() ? View.VISIBLE : View.GONE);

        // Messages
        if (state.getMessages() != null) {
            adapter.submitList(state.getMessages());
        }

        // One-time error
        if (state.getError() != null) {
            Toast.makeText(this, state.getError(), Toast.LENGTH_SHORT).show();
            viewModel.consumeError();
        }
    }

    // ── Effect handling ───────────────────────────────────────────────────────

    private void handleEffect(ChatroomUiEffect effect) {
        if (effect == null) return;

        switch (effect.getType()) {
            case SCROLL_TO_BOTTOM:
                int count = adapter.getItemCount();
                if (count > 0) recyclerView.scrollToPosition(count - 1);
                break;
            case SHOW_ERROR:
                Toast.makeText(this, effect.getMessage(), Toast.LENGTH_SHORT).show();
                break;
        }
        viewModel.consumeEffect();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void sendCurrentMessage() {
        String text = editMessage.getText().toString().trim();
        if (text.isEmpty()) return;
        viewModel.sendMessage(text);
        editMessage.setText("");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        recyclerView.setAdapter(null);
    }
}
