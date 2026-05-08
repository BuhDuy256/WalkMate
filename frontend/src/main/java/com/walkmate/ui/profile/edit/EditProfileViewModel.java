package com.walkmate.ui.profile.edit;

import android.content.ContentResolver;
import android.net.Uri;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.user.ProfileTagMaster;
import com.walkmate.domain.user.UserProfile;
import com.walkmate.domain.user.UserProfileRepository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EditProfileViewModel extends ViewModel {

    private static final int MAX_BIO_LENGTH = 500;
    private static final int MAX_TAG_COUNT  = 10;

    private final MutableLiveData<EditProfileUiState> uiState =
            new MutableLiveData<>(EditProfileUiState.idle());

    private final UserProfileRepository profileRepo;
    private final ExecutorService        executor = Executors.newSingleThreadExecutor();

    public EditProfileViewModel(UserProfileRepository profileRepo) {
        this.profileRepo = profileRepo;
    }

    public LiveData<EditProfileUiState> getUiState() {
        return uiState;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void loadCurrentProfile() {
        uiState.postValue(EditProfileUiState.loading());

        profileRepo.getMyProfile(new DomainCallback<UserProfile>() {
            @Override
            public void onSuccess(UserProfile profile) {
                EditProfileUiState current = currentState();
                uiState.postValue(new EditProfileUiState(
                        false,
                        profile.getFullName(),
                        profile.getGender(),
                        profile.getDateOfBirth(),
                        profile.getBio(),
                        profile.getTags(),
                        profile.getAvatarUrl(),
                        false,
                        null,
                        current.masterTags));
            }

            @Override
            public void onError(Exception e) {
                uiState.postValue(EditProfileUiState.idle().withError(friendlyError(e)));
            }
        });

        // Eagerly fetch master tags in parallel with the profile load.
        profileRepo.getMasterTags(new DomainCallback<List<ProfileTagMaster>>() {
            @Override
            public void onSuccess(List<ProfileTagMaster> tags) {
                uiState.postValue(currentState().withMasterTags(tags));
            }

            @Override
            public void onError(Exception e) {
                // Non-blocking: chip group stays empty; user can retry by re-opening.
            }
        });
    }

    /**
     * Validates inputs then persists profile changes.
     * tagIds is a list of UUID strings from profile_tag_master.
     */
    public void save(String fullName, String gender, String dob,
                     String bio, List<String> tagIds) {
        if (fullName == null || fullName.trim().isEmpty()) {
            postError("Full name cannot be empty.");
            return;
        }
        if (bio != null && bio.length() > MAX_BIO_LENGTH) {
            postError("Bio must be " + MAX_BIO_LENGTH + " characters or fewer.");
            return;
        }
        if (tagIds != null && tagIds.size() > MAX_TAG_COUNT) {
            postError("You can have at most " + MAX_TAG_COUNT + " tags.");
            return;
        }

        uiState.postValue(currentState().withLoading(true));

        profileRepo.updateProfile(fullName.trim(), gender, dob, bio, tagIds,
                new DomainCallback<UserProfile>() {
                    @Override
                    public void onSuccess(UserProfile profile) {
                        uiState.postValue(currentState().withSaveSuccess());
                    }

                    @Override
                    public void onError(Exception e) {
                        postError(friendlyError(e));
                    }
                });
    }

    public void uploadAvatar(Uri imageUri, ContentResolver resolver) {
        uiState.postValue(currentState().withLoading(true));

        executor.execute(() -> {
            try {
                byte[] bytes = readBytes(resolver, imageUri);
                String filename = "avatar_" + System.currentTimeMillis() + ".jpg";
                String mimeType = resolver.getType(imageUri);
                if (mimeType == null) mimeType = "image/jpeg";

                profileRepo.uploadAvatar(bytes, filename, mimeType,
                        new DomainCallback<String>() {
                            @Override
                            public void onSuccess(String avatarUrl) {
                                loadCurrentProfile();
                            }

                            @Override
                            public void onError(Exception e) {
                                postError(friendlyError(e));
                            }
                        });
            } catch (IOException e) {
                postError("Could not read image: " + e.getMessage());
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdownNow();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private EditProfileUiState currentState() {
        EditProfileUiState s = uiState.getValue();
        return s != null ? s : EditProfileUiState.idle();
    }

    private void postError(String message) {
        uiState.postValue(currentState().withError(message));
    }

    private static byte[] readBytes(ContentResolver resolver, Uri uri) throws IOException {
        try (InputStream is = resolver.openInputStream(uri)) {
            if (is == null) throw new IOException("Cannot open URI");
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = is.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return buffer.toByteArray();
        }
    }

    private static String friendlyError(Exception e) {
        String msg = e.getMessage();
        return (msg != null && !msg.isEmpty()) ? msg : "An error occurred. Please try again.";
    }
}
