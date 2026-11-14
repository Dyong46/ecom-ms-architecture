package com.duong.module.caching.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.data.redis.serializer.SerializationException;
import java.io.IOException;
import org.springframework.data.redis.serializer.RedisSerializer;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CacheJson {
    private CacheJson() {}

    public static ObjectMapper typedMapper() {
        return JsonMapper.builder().addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    }

    /** Spring Cache has no caller-provided result type; restrict its polymorphic JSON types. */
    public static RedisSerializer<Object> springSerializer() {
        var validator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.duong.")
                .allowIfSubType("java.time.")
                .allowIfSubType("java.math.")
                .allowIfSubType(List.class).allowIfSubType(Map.class).allowIfSubType(Set.class)
                .allowIfSubType(String.class).allowIfSubType(Number.class).allowIfSubType(Boolean.class)
                .build();
        var mapper = typedMapper();
        mapper.activateDefaultTyping(validator, ObjectMapper.DefaultTyping.EVERYTHING, JsonTypeInfo.As.PROPERTY);
        return new RedisSerializer<>() {
            @Override
            public byte[] serialize(Object value) {
                if (value == null) { return new byte[0]; }
                try { return mapper.writerFor(Object.class).writeValueAsBytes(value); }
                catch (IOException ex) { throw new SerializationException("Cache JSON serialization failed"); }
            }

            @Override
            public Object deserialize(byte[] bytes) {
                if (bytes == null || bytes.length == 0) { return null; }
                try { return mapper.readerFor(Object.class).readValue(bytes); }
                catch (IOException ex) { throw new SerializationException("Cache JSON deserialization failed"); }
            }
        };
    }
}
