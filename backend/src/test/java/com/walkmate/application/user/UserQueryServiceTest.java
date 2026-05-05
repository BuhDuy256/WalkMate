package com.walkmate.application.user;

import com.walkmate.domain.shared.exception.DomainException;
import com.walkmate.domain.user.ProfileTagMaster;
import com.walkmate.domain.user.User;
import com.walkmate.domain.user.UserProfile;
import com.walkmate.domain.user.UserProfileRepository;
import com.walkmate.domain.user.UserRepository;
import com.walkmate.domain.user.UserErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserQueryServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProfileRepository profileRepository;

    private UserQueryService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new UserQueryService(userRepository, profileRepository);
    }

    @Test
    void getMyProfile_existingProfile_returnsIt() {
        UUID id = UUID.randomUUID();
        UserProfile p = new UserProfile(id, "Alice", null, null, null, null);

        when(userRepository.findById(id.toString())).thenReturn(Optional.of(new User(id, "a@b.com", null, null, null, null, null, null, null, 0,0,0.0,0, null, null)));
        when(profileRepository.findByUserId(id)).thenReturn(Optional.of(p));

        UserProfile out = service.getMyProfile(id);

        assertThat(out).isSameAs(p);
    }

    @Test
    void getMyProfile_noProfile_createsAndSavesBlank() {
        UUID id = UUID.randomUUID();

        when(userRepository.findById(id.toString())).thenReturn(Optional.of(new User(id, "a@b.com", null, null, null, null, null, null, null, 0,0,0.0,0, null, null)));
        when(profileRepository.findByUserId(id)).thenReturn(Optional.empty());
        when(profileRepository.save(any(UserProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserProfile out = service.getMyProfile(id);

        assertThat(out).isNotNull();
        assertThat(out.getFullName()).isEmpty();

        ArgumentCaptor<UserProfile> cap = ArgumentCaptor.forClass(UserProfile.class);
        verify(profileRepository).save(cap.capture());
        assertThat(cap.getValue().getUserId()).isEqualTo(id);
    }

    @Test
    void getProfile_existing_returnsIt() {
        UUID id = UUID.randomUUID();
        UserProfile p = new UserProfile(id, "Bob", null, null, null, null);

        when(userRepository.findById(id.toString())).thenReturn(Optional.of(new User(id, "b@c.com", null, null, null, null, null, null, null, 0,0,0.0,0, null, null)));
        when(profileRepository.findByUserId(id)).thenReturn(Optional.of(p));

        UserProfile out = service.getProfile(id);

        assertThat(out).isSameAs(p);
    }

    @Test
    void getProfile_none_returnsBlankWithoutSaving() {
        UUID id = UUID.randomUUID();

        when(userRepository.findById(id.toString())).thenReturn(Optional.of(new User(id, "b@c.com", null, null, null, null, null, null, null, 0,0,0.0,0, null, null)));
        when(profileRepository.findByUserId(id)).thenReturn(Optional.empty());

        UserProfile out = service.getProfile(id);

        assertThat(out).isNotNull();
        assertThat(out.getFullName()).isEmpty();
    }

    @Test
    void getUser_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id.toString())).thenReturn(Optional.empty());

        DomainException ex = assertThrows(DomainException.class, () -> service.getUser(id));
        assertThat(ex.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND);
    }

    @Test
    void getDisplayName_returnsNameOrNull() {
        UUID id = UUID.randomUUID();
        UserProfile p = new UserProfile(id, "Carol", null, null, null, null);
        when(profileRepository.findByUserId(id)).thenReturn(Optional.of(p));

        String name = service.getDisplayName(id);
        assertThat(name).isEqualTo("Carol");

        UUID id2 = UUID.randomUUID();
        when(profileRepository.findByUserId(id2)).thenReturn(Optional.empty());
        assertThat(service.getDisplayName(id2)).isNull();
    }

    @Test
    void getTagsByUserId_delegatesToRepository() {
        UUID id = UUID.randomUUID();
        when(profileRepository.findTagsByUserId(id)).thenReturn(List.of("t1","t2"));

        List<String> tags = service.getTagsByUserId(id);
        assertThat(tags).containsExactly("t1","t2");
    }
}
