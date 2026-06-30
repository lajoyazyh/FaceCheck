package com.facecheck.infrastructure.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitTopologyConfig {

    @Bean
    MessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter(
                "com.facecheck.face.messaging",
                "com.facecheck.checkin.messaging"
        );
    }

    @Bean
    Declarables facePhotoDeclarables(
            @Value("${facecheck.messaging.face-photo.register-queue}") String registerQueueName,
            @Value("${facecheck.messaging.face-photo.register-retry-queue}") String registerRetryQueueName,
            @Value("${facecheck.messaging.face-photo.register-dlq-queue}") String registerDlqQueueName,
            @Value("${facecheck.messaging.face-photo.delete-queue}") String deleteQueueName,
            @Value("${facecheck.messaging.face-photo.delete-retry-queue}") String deleteRetryQueueName,
            @Value("${facecheck.messaging.face-photo.delete-dlq-queue}") String deleteDlqQueueName,
            @Value("${facecheck.messaging.face-photo.retry-ttl-ms}") int retryTtlMs
    ) {
        DirectExchange registerExchange = new DirectExchange("face.photo.register.exchange");
        DirectExchange registerRetryExchange = new DirectExchange("face.photo.register.retry.exchange");
        DirectExchange registerDlqExchange = new DirectExchange("face.photo.register.dlq.exchange");

        DirectExchange deleteExchange = new DirectExchange("face.photo.delete.exchange");
        DirectExchange deleteRetryExchange = new DirectExchange("face.photo.delete.retry.exchange");
        DirectExchange deleteDlqExchange = new DirectExchange("face.photo.delete.dlq.exchange");

        Queue registerQueue = QueueBuilder.durable(registerQueueName).build();
        Queue registerRetryQueue = QueueBuilder.durable(registerRetryQueueName)
                .withArgument("x-message-ttl", retryTtlMs)
                .withArgument("x-dead-letter-exchange", registerExchange.getName())
                .withArgument("x-dead-letter-routing-key", registerQueueName)
                .build();
        Queue registerDlqQueue = QueueBuilder.durable(registerDlqQueueName).build();

        Queue deleteQueue = QueueBuilder.durable(deleteQueueName).build();
        Queue deleteRetryQueue = QueueBuilder.durable(deleteRetryQueueName)
                .withArgument("x-message-ttl", retryTtlMs)
                .withArgument("x-dead-letter-exchange", deleteExchange.getName())
                .withArgument("x-dead-letter-routing-key", deleteQueueName)
                .build();
        Queue deleteDlqQueue = QueueBuilder.durable(deleteDlqQueueName).build();

        Binding registerBinding = BindingBuilder.bind(registerQueue).to(registerExchange).with(registerQueueName);
        Binding registerRetryBinding = BindingBuilder.bind(registerRetryQueue).to(registerRetryExchange).with(registerRetryQueueName);
        Binding registerDlqBinding = BindingBuilder.bind(registerDlqQueue).to(registerDlqExchange).with(registerDlqQueueName);

        Binding deleteBinding = BindingBuilder.bind(deleteQueue).to(deleteExchange).with(deleteQueueName);
        Binding deleteRetryBinding = BindingBuilder.bind(deleteRetryQueue).to(deleteRetryExchange).with(deleteRetryQueueName);
        Binding deleteDlqBinding = BindingBuilder.bind(deleteDlqQueue).to(deleteDlqExchange).with(deleteDlqQueueName);

        return new Declarables(
                registerExchange,
                registerRetryExchange,
                registerDlqExchange,
                deleteExchange,
                deleteRetryExchange,
                deleteDlqExchange,
                registerQueue,
                registerRetryQueue,
                registerDlqQueue,
                deleteQueue,
                deleteRetryQueue,
                deleteDlqQueue,
                registerBinding,
                registerRetryBinding,
                registerDlqBinding,
                deleteBinding,
                deleteRetryBinding,
                deleteDlqBinding
        );
    }
}
