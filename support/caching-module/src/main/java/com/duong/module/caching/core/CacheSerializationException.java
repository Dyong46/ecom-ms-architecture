package com.duong.module.caching.core;

/** Does not retain a Jackson exception that could include cached payload contents. */
public final class CacheSerializationException extends RuntimeException {
    public CacheSerializationException() { super("Cache JSON serialization or deserialization failed"); }
}
