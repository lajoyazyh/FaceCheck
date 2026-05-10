package com.facecheck.face.messaging;

import com.facecheck.face.service.FacePhotoDeletionService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FacePhotoDeleteCompensationConsumer {

    static final int MAX_RETRIES = 3;

    private final FacePhotoDeletionService deletionService;
    private final RabbitTemplate rabbitTemplate;
    private final String retryQueue;
    private final String dlqQueue;

    public FacePhotoDeleteCompensationConsumer(
            FacePhotoDeletionService deletionService,
            RabbitTemplate rabbitTemplate,
            @Value("${facecheck.messaging.face-photo.delete-retry-queue}") String retryQueue,
            @Value("${facecheck.messaging.face-photo.delete-dlq-queue}") String dlqQueue
    ) {
        this.deletionService = deletionService;
        this.rabbitTemplate = rabbitTemplate;
        this.retryQueue = retryQueue;
        this.dlqQueue = dlqQueue;
    }

    @RabbitListener(queues = "${facecheck.messaging.face-photo.delete-queue}")
    public void receive(FacePhotoDeleteCompensationTask task) {
        consume(task);
    }

    public void consume(FacePhotoDeleteCompensationTask task) {
        try {
            deletionService.compensateDeletion(task.userId(), task.photoId());
        } catch (RetryableFacePhotoException exception) {
            if (task.retryCount() >= MAX_RETRIES) {
                rabbitTemplate.convertAndSend(dlqQueue, task);
                return;
            }
            rabbitTemplate.convertAndSend(retryQueue, new FacePhotoDeleteCompensationTask(
                    task.photoId(),
                    task.userId(),
                    task.retryCount() + 1
            ));
        }
    }
}
