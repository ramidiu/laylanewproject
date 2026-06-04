package com.remitz.modules.notification.config;

import com.remitz.modules.notification.service.EventListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

@Configuration
public class RedisSubscriberConfig {

    @Bean
    public ChannelTopic transactionEventsTopic() {
        return new ChannelTopic("transaction-events");
    }

    @Bean
    public ChannelTopic kycEventsTopic() {
        return new ChannelTopic("kyc-events");
    }

    @Bean
    public ChannelTopic complianceEventsTopic() {
        return new ChannelTopic("compliance-events");
    }

    @Bean
    public MessageListenerAdapter transactionEventsListener(EventListener eventListener) {
        return new MessageListenerAdapter(eventListener, "handleTransactionEvent");
    }

    @Bean
    public MessageListenerAdapter kycEventsListener(EventListener eventListener) {
        return new MessageListenerAdapter(eventListener, "handleKycEvent");
    }

    @Bean
    public MessageListenerAdapter complianceEventsListener(EventListener eventListener) {
        return new MessageListenerAdapter(eventListener, "handleComplianceEvent");
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter transactionEventsListener,
            MessageListenerAdapter kycEventsListener,
            MessageListenerAdapter complianceEventsListener,
            ChannelTopic transactionEventsTopic,
            ChannelTopic kycEventsTopic,
            ChannelTopic complianceEventsTopic) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(transactionEventsListener, transactionEventsTopic);
        container.addMessageListener(kycEventsListener, kycEventsTopic);
        container.addMessageListener(complianceEventsListener, complianceEventsTopic);
        return container;
    }
}
