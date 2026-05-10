package com.facecheck.face.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.facecheck.auth.security.CurrentUserAccess;
import com.facecheck.face.FaceRecognitionProvider;
import com.facecheck.face.messaging.FacePhotoDeleteCompensationTask;
import com.facecheck.face.model.FaceDetectStatus;
import com.facecheck.face.model.FacePhoto;
import com.facecheck.face.model.FaceRegisterStatus;
import com.facecheck.face.model.HuaweiFaceRef;
import com.facecheck.face.model.HuaweiFaceRefStatus;
import com.facecheck.face.repo.FacePhotoRepository;
import com.facecheck.face.repo.HuaweiFaceRefRepository;
import com.facecheck.identity.model.User;
import com.facecheck.storage.HuaweiObsStorageService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class FacePhotoDeletionServiceTest {

    @Mock
    private FacePhotoRepository facePhotoRepository;

    @Mock
    private HuaweiFaceRefRepository huaweiFaceRefRepository;

    @Mock
    private CurrentUserAccess currentUserAccess;

    @Mock
    private FaceRecognitionProvider faceRecognitionProvider;

    @Mock
    private HuaweiObsStorageService storageService;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private FacePhotoDeletionService deletionService;

    @BeforeEach
    void setUp() {
        deletionService = new FacePhotoDeletionService(
                facePhotoRepository,
                huaweiFaceRefRepository,
                currentUserAccess,
                faceRecognitionProvider,
                storageService,
                rabbitTemplate,
                "face.photo.delete.compensate"
        );

        when(facePhotoRepository.save(any(FacePhoto.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(huaweiFaceRefRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void shouldMarkDeleteFailedAndPublishCompensationTaskWhenObsDeletionFails() {
        DeletionFixture fixture = createFixture();
        when(facePhotoRepository.findByIdAndUserIdAndEnabledTrue(fixture.photo().getId(), fixture.user().getId()))
                .thenReturn(Optional.of(fixture.photo()));
        when(huaweiFaceRefRepository.findAllByFacePhotoId(fixture.photo().getId()))
                .thenReturn(List.of(fixture.ref()));
        doThrow(new RuntimeException("OBS delete failed"))
                .when(storageService)
                .delete(fixture.photo().getObsObjectKey());

        deletionService.deleteUserPhoto(fixture.user().getId(), fixture.photo().getId());

        assertThat(fixture.photo().isEnabled()).isFalse();
        assertThat(fixture.photo().getRegisterStatus()).isEqualTo(FaceRegisterStatus.DELETE_FAILED);
        assertThat(fixture.photo().getFailureCode()).isEqualTo("DELETE_COMPENSATION_FAILED");
        assertThat(fixture.ref().getStatus()).isEqualTo(HuaweiFaceRefStatus.DELETE_FAILED);
        verifyNoInteractions(faceRecognitionProvider);

        ArgumentCaptor<FacePhotoDeleteCompensationTask> taskCaptor =
                ArgumentCaptor.forClass(FacePhotoDeleteCompensationTask.class);
        verify(rabbitTemplate).convertAndSend(eq("face.photo.delete.compensate"), taskCaptor.capture());
        assertThat(taskCaptor.getValue().photoId()).isEqualTo(fixture.photo().getId());
        assertThat(taskCaptor.getValue().userId()).isEqualTo(fixture.user().getId());
        assertThat(taskCaptor.getValue().retryCount()).isZero();
    }

    @Test
    void shouldMarkDeleteFailedAndPublishCompensationTaskWhenFrsDeletionFails() {
        DeletionFixture fixture = createFixture();
        when(facePhotoRepository.findByIdAndUserIdAndEnabledTrue(fixture.photo().getId(), fixture.user().getId()))
                .thenReturn(Optional.of(fixture.photo()));
        when(huaweiFaceRefRepository.findAllByFacePhotoId(fixture.photo().getId()))
                .thenReturn(List.of(fixture.ref()));
        doNothing().when(storageService).delete(fixture.photo().getObsObjectKey());
        doThrow(new RuntimeException("FRS delete failed"))
                .when(faceRecognitionProvider)
                .deleteFace(fixture.ref().getFrsFaceId());

        deletionService.deleteUserPhoto(fixture.user().getId(), fixture.photo().getId());

        assertThat(fixture.photo().isEnabled()).isFalse();
        assertThat(fixture.photo().getRegisterStatus()).isEqualTo(FaceRegisterStatus.DELETE_FAILED);
        assertThat(fixture.photo().getFailureCode()).isEqualTo("DELETE_COMPENSATION_FAILED");
        assertThat(fixture.ref().getStatus()).isEqualTo(HuaweiFaceRefStatus.DELETE_FAILED);
        verify(faceRecognitionProvider).deleteFace(fixture.ref().getFrsFaceId());

        ArgumentCaptor<FacePhotoDeleteCompensationTask> taskCaptor =
                ArgumentCaptor.forClass(FacePhotoDeleteCompensationTask.class);
        verify(rabbitTemplate).convertAndSend(eq("face.photo.delete.compensate"), taskCaptor.capture());
        assertThat(taskCaptor.getValue().photoId()).isEqualTo(fixture.photo().getId());
        assertThat(taskCaptor.getValue().userId()).isEqualTo(fixture.user().getId());
        assertThat(taskCaptor.getValue().retryCount()).isZero();
    }

    private DeletionFixture createFixture() {
        User user = new User();
        user.setId(UUID.randomUUID());

        FacePhoto photo = new FacePhoto();
        photo.setId(UUID.randomUUID());
        photo.setUserId(user.getId());
        photo.setCreatedByUserId(user.getId());
        photo.setObsObjectKey("face-photos/" + photo.getId() + ".png");
        photo.setDetectStatus(FaceDetectStatus.PASSED);
        photo.setRegisterStatus(FaceRegisterStatus.ACTIVE);
        photo.setEnabled(true);

        HuaweiFaceRef ref = new HuaweiFaceRef();
        ref.setId(UUID.randomUUID());
        ref.setUserId(user.getId());
        ref.setFacePhotoId(photo.getId());
        ref.setFaceSetName("facecheck-users");
        ref.setFrsFaceId("frs-" + photo.getId());
        ref.setExternalImageId(photo.getId().toString());
        ref.setExternalFields(Map.of(
                "userId", user.getId().toString(),
                "photoId", photo.getId().toString()
        ));
        ref.setStatus(HuaweiFaceRefStatus.ACTIVE);

        return new DeletionFixture(user, photo, ref);
    }

    private record DeletionFixture(User user, FacePhoto photo, HuaweiFaceRef ref) {
    }
}
