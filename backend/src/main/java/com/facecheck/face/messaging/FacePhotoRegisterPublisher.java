package com.facecheck.face.messaging;

import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FacePhotoRegisterPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final String registerQueue;

    public FacePhotoRegisterPublisher(
            RabbitTemplate rabbitTemplate,
            @Value("${facecheck.messaging.face-photo.register-queue}") String registerQueue
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.registerQueue = registerQueue;
    }

    public void publish(UUID photoId, UUID userId) {
        rabbitTemplate.convertAndSend(registerQueue, new FacePhotoRegisterTask(photoId, userId, 0));
    }
}
