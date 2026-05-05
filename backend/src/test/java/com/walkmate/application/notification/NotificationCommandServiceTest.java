package com.walkmate.application.notification;

import com.walkmate.domain.notification.Notification;
import com.walkmate.domain.notification.NotificationErrorCode;
import com.walkmate.domain.notification.NotificationRepository;
import com.walkmate.domain.notification.NotificationStatus;
import com.walkmate.domain.notification.NotificationType;
import com.walkmate.domain.shared.exception.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationCommandServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationCommandService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new NotificationCommandService(notificationRepository);
    }

    @Test
    void markRead_existingOwned_marksReadAndSaves() {
        Notification n = new Notification("nid","user-1", NotificationType.SESSION_ACTIVE, Map.of(), NotificationStatus.PENDING, Instant.now(), null);
        when(notificationRepository.findById("nid")).thenReturn(Optional.of(n));

        service.markRead("nid", "user-1");

        assertThat(n.getStatus()).isEqualTo(NotificationStatus.READ);
        assertThat(n.getReadAt()).isNotNull();
        verify(notificationRepository).save(n);
    }

    @Test
    void markRead_notFound_throwsDomainException() {
        when(notificationRepository.findById("missing")).thenReturn(Optional.empty());
        DomainException ex = assertThrows(DomainException.class, () -> service.markRead("missing", "user-1"));
        assertThat(ex.getErrorCode()).isEqualTo(NotificationErrorCode.NOTIFICATION_NOT_FOUND);
    }

    @Test
    void markRead_notOwner_throwsDomainException() {
        Notification n = new Notification("nid","other-user", NotificationType.SESSION_ACTIVE, Map.of(), NotificationStatus.PENDING, Instant.now(), null);
        when(notificationRepository.findById("nid")).thenReturn(Optional.of(n));
        DomainException ex = assertThrows(DomainException.class, () -> service.markRead("nid", "user-1"));
        assertThat(ex.getErrorCode()).isEqualTo(NotificationErrorCode.NOTIFICATION_NOT_OWNER);
    }
}
