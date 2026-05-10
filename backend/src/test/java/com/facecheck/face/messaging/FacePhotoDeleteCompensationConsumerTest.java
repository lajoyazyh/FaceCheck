package com.facecheck.face.messaging;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;

import com.facecheck.face.service.FacePhotoDeletionService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class FacePhotoDeleteCompensationConsumerTest {

    @Mock
    private FacePhotoDeletionService deletionService;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private FacePhotoDeleteCompensationConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new FacePhotoDeleteCompensationConsumer(
                deletionService,
                rabbitTemplate,
                "face.photo.delete.compensate.retry",
                "face.photo.delete.compensate.dlq"
        );
    }

    @Test
    void shouldRepublishRetryableFailuresToRetryQueue() {
        FacePhotoDeleteCompensationTask task = new FacePhotoDeleteCompensationTask(UUID.randomUUID(), UUID.randomUUID(), 0);
        doThrow(new RetryableFacePhotoException("DELETE_COMPENSATION_FAILED", "temporary failure"))
                .when(deletionService)
                .compensateDeletion(task.userId(), task.photoId());

        consumer.consume(task);

        verify(rabbitTemplate).convertAndSend(
                "face.photo.delete.compensate.retry",
                new FacePhotoDeleteCompensationTask(task.photoId(), task.userId(), 1)
        );
        verify(rabbitTemplate, never()).convertAndSend("face.photo.delete.compensate.dlq", task);
    }

    @Test
    void shouldRouteExhaustedFailuresToDlq() {
        FacePhotoDeleteCompensationTask task = new FacePhotoDeleteCompensationTask(
                UUID.randomUUID(),
                UUID.randomUUID(),
                FacePhotoDeleteCompensationConsumer.MAX_RETRIES
        );
        doThrow(new RetryableFacePhotoException("DELETE_COMPENSATION_FAILED", "temporary failure"))
                .when(deletionService)
                .compensateDeletion(task.userId(), task.photoId());

        consumer.consume(task);

        verify(rabbitTemplate).convertAndSend("face.photo.delete.compensate.dlq", task);
        verify(rabbitTemplate, never()).convertAndSend(
                "face.photo.delete.compensate.retry",
                new FacePhotoDeleteCompensationTask(task.photoId(), task.userId(), task.retryCount() + 1)
        );
    }
}
