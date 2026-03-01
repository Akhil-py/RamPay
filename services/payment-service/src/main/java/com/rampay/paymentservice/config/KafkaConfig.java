package com.rampay.paymentservice.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.producer.transaction-id-prefix:rampay-payment-tx-}")
    private String transactionIdPrefix;

    // -------------------------------------------------------------------------
    // Kafka Admin — auto-create topics on startup (missingTopicsFatal=false so
    // the service starts even when the broker is temporarily unavailable)
    // -------------------------------------------------------------------------

    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> config = Map.of(
                org.apache.kafka.clients.admin.AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers
        );
        KafkaAdmin admin = new KafkaAdmin(config);
        admin.setFatalIfBrokerNotAvailable(false);
        return admin;
    }

    // Core topics (3 partitions, RF=1 for single-broker local dev)

    @Bean
    public org.apache.kafka.clients.admin.NewTopic topicPaymentCreated() {
        return TopicBuilder.name("payment-created").partitions(3).replicas(1).build();
    }

    @Bean
    public org.apache.kafka.clients.admin.NewTopic topicPaymentApproved() {
        return TopicBuilder.name("payment-approved").partitions(3).replicas(1).build();
    }

    @Bean
    public org.apache.kafka.clients.admin.NewTopic topicPaymentFailed() {
        return TopicBuilder.name("payment-failed").partitions(3).replicas(1).build();
    }

    @Bean
    public org.apache.kafka.clients.admin.NewTopic topicPaymentRefunded() {
        return TopicBuilder.name("payment-refunded").partitions(3).replicas(1).build();
    }

    @Bean
    public org.apache.kafka.clients.admin.NewTopic topicAnomalyDetected() {
        return TopicBuilder.name("anomaly-detected").partitions(3).replicas(1).build();
    }

    // Dead-letter queue topics (1 partition — ordered, low-volume error stream)

    @Bean
    public org.apache.kafka.clients.admin.NewTopic topicPaymentCreatedDlt() {
        return TopicBuilder.name("payment-created.DLT").partitions(1).replicas(1).build();
    }

    @Bean
    public org.apache.kafka.clients.admin.NewTopic topicPaymentApprovedDlt() {
        return TopicBuilder.name("payment-approved.DLT").partitions(1).replicas(1).build();
    }

    @Bean
    public org.apache.kafka.clients.admin.NewTopic topicPaymentFailedDlt() {
        return TopicBuilder.name("payment-failed.DLT").partitions(1).replicas(1).build();
    }

    @Bean
    public org.apache.kafka.clients.admin.NewTopic topicPaymentRefundedDlt() {
        return TopicBuilder.name("payment-refunded.DLT").partitions(1).replicas(1).build();
    }

    @Bean
    public org.apache.kafka.clients.admin.NewTopic topicAnomalyDetectedDlt() {
        return TopicBuilder.name("anomaly-detected.DLT").partitions(1).replicas(1).build();
    }

    // -------------------------------------------------------------------------
    // Transactional producer — used by EventPublisherService (outbox pattern).
    // Idempotence is implied by transactions; retries=MAX_VALUE is required.
    // -------------------------------------------------------------------------

    @Bean
    @Primary
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
        config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);

        DefaultKafkaProducerFactory<String, String> factory =
                new DefaultKafkaProducerFactory<>(config);
        factory.setTransactionIdPrefix(transactionIdPrefix);
        return factory;
    }

    @Bean
    @Primary
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // -------------------------------------------------------------------------
    // Non-transactional producer — used by the DLQ recoverer and any component
    // that must publish outside a transaction (e.g., the fraud consumer path).
    // -------------------------------------------------------------------------

    @Bean("nonTransactionalProducerFactory")
    public ProducerFactory<String, String> nonTransactionalProducerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
        config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean("nonTransactionalKafkaTemplate")
    public KafkaTemplate<String, String> nonTransactionalKafkaTemplate() {
        return new KafkaTemplate<>(nonTransactionalProducerFactory());
    }

    // -------------------------------------------------------------------------
    // Consumer factory — isolation.level=read_committed so consumers only see
    // messages that were committed as part of a completed transaction.
    // -------------------------------------------------------------------------

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        config.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        config.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000);
        config.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);
        return new DefaultKafkaConsumerFactory<>(config);
    }

    // -------------------------------------------------------------------------
    // Error handler — exponential backoff (3 retries, 1 s initial, 2x factor)
    // followed by routing to the ".DLT" topic via DeadLetterPublishingRecoverer.
    // The recoverer uses the non-transactional template so it can publish DLT
    // records even when no active transaction is in scope.
    // -------------------------------------------------------------------------

    @Bean
    public DefaultErrorHandler errorHandler() {
        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(nonTransactionalKafkaTemplate());

        ExponentialBackOff backOff = new ExponentialBackOff(1_000L, 2.0);
        backOff.setMaxAttempts(3);

        return new DefaultErrorHandler(recoverer, backOff);
    }

    // -------------------------------------------------------------------------
    // Listener container factory — wires consumer factory, manual-ack mode,
    // and the DLQ-aware error handler together.
    // -------------------------------------------------------------------------

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setCommonErrorHandler(errorHandler());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }
}
