package com.duong.module.kafka.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import java.util.HashSet;
import java.util.List;

@ConfigurationProperties("kafka-module")
public record KafkaModuleProperties(@DefaultValue("false") boolean provisionTopics, List<Topic> topics) {
    public KafkaModuleProperties {
        topics = topics == null ? List.of() : List.copyOf(topics);
        var names = new HashSet<String>();
        for (Topic topic : topics) {
            if (!names.add(topic.name())) {
                throw new IllegalArgumentException("Duplicate Kafka topic declaration");
            }
        }
    }

    public record Topic(String name, @DefaultValue("1") int partitions, @DefaultValue("1") short replicas) {
        public Topic {
            requireTopic(name);
            if (partitions < 1 || replicas < 1) {
                throw new IllegalArgumentException("Kafka partitions and replicas must be positive");
            }
        }
    }

    public static void requireTopic(String name) {
        if (name == null || !name.matches("[a-zA-Z0-9._-]{1,249}") || name.equals(".") || name.equals("..")) {
            throw new IllegalArgumentException("Invalid Kafka topic name");
        }
    }
}
