package com.duong.module.caching;

import com.duong.module.caching.core.CacheKeys;
import com.duong.module.caching.core.CacheScope;
import com.duong.module.caching.lua.RedisLuaExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RedisLuaExecutorTest {
    @Test
    void relatedKeysShareHashTagAndRemainIsolatedByOwnerGroupAndDataFormat() {
        var order = new CacheKeys(CachingModuleTest.defaults(), "order-service");
        var payment = new CacheKeys(CachingModuleTest.defaults(), "payment-service");
        var first = order.luaKey(CacheScope.SERVICE, "counters", "ORDER-1", "first");
        var second = order.luaKey(CacheScope.SERVICE, "counters", "ORDER-1", "second");
        String firstTag = first.substring(first.indexOf('{'), first.indexOf('}') + 1);
        assertThat(second).contains(firstTag).isNotEqualTo(first);
        assertThat(first).isNotEqualTo(payment.luaKey(CacheScope.SERVICE, "counters", "ORDER-1", "first"))
                .isNotEqualTo(order.luaKey(CacheScope.SERVICE, "counters", "ORDER-2", "first"))
                .isNotEqualTo(order.jsonKey(CacheScope.SERVICE, "counters", "first"));
        assertThat(order.luaKey(CacheScope.GLOBAL, "counters", "ORDER-1", "first"))
                .isEqualTo(payment.luaKey(CacheScope.GLOBAL, "counters", "ORDER-1", "first"));
    }

    @Test
    void missingScriptsAndKeylessExecutionFailBeforeRedisAccess() {
        var redis = mock(StringRedisTemplate.class);
        var executor = new RedisLuaExecutor(redis, new CacheKeys(CachingModuleTest.defaults(), "order-service"));
        assertThatThrownBy(() -> RedisLuaExecutor.loadScript("redis/missing.lua", Long.class))
                .isInstanceOf(IllegalArgumentException.class);
        var script = RedisLuaExecutor.loadScript("redis/increment-if-below.lua", Long.class);
        assertThatThrownBy(() -> executor.execute(script, CacheScope.SERVICE, "counters", "group", List.of(), "1", "5000"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(redis);
    }
}
