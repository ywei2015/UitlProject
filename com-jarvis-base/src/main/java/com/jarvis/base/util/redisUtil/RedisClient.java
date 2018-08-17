package haiya.agent.admin.util;

import haiya.agent.admin.util.serializer.Serializer;
import redis.clients.jedis.Jedis;
import redis.clients.util.Pool;
import redis.clients.util.SafeEncoder;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author YWW select from phz
 */
public class RedisClient {

    private static final byte[] BINX = SafeEncoder.encode("NX");
    private static final byte[] BIPX = SafeEncoder.encode("PX");

    private static final String KEYWORD_OK = "OK";

    private final Pool<Jedis> pool;
    private final Serializer serializer;

    public RedisClient(Pool<Jedis> pool, Serializer serializer) {
        this.pool = pool;
        this.serializer = serializer;
    }

    public <R> R execute(Function<Jedis, R> function) {
        try (Jedis jedis = pool.getResource()) {
            return function.apply(jedis);
        }
    }

    public void consume(Consumer<Jedis> comsumer) {
        try (Jedis jedis = pool.getResource()) {
            comsumer.accept(jedis);
        }
    }

    public <T> T get(String key) {
        return execute(jedis -> {
            byte[] v = jedis.get(SafeEncoder.encode(key));
            return serializer.deserialize(v);
        });
    }

    public void set(String key, Object value) {
        consume(jedis -> jedis.set(SafeEncoder.encode(key), serializer.serialize(value)));
    }

    /**
     * 将值 value 关联到 key ，并将 key 的生存时间设为 seconds (以秒为单位)。
     *
     * 如果 key 已经存在， SETEX 命令将覆写旧值。
     *
     * @param key Key
     * @param expire 生存时间(以秒为单位)
     * @param value 值
     */
    public void setex(String key, int expire, Object value) {
        consume(jedis -> jedis.setex(SafeEncoder.encode(key), expire, serializer.serialize(value)));
    }

    public Long del(String key) {
        return execute(jedis -> jedis.del(key));
    }

    public Long del(byte[] key) {
        return execute(jedis -> jedis.del(key));
    }

    public boolean setIfAbsentMillisecond(String key, Object value, long expireMillis) {
        byte[] k = SafeEncoder.encode(key);
        byte[] v = serializer.serialize(value);
        String r = execute(jedis -> jedis.set(k, v, BINX, BIPX, expireMillis));
        return KEYWORD_OK.equals(r);
    }

    /**
     * @see redis.clients.jedis.BinaryJedis#zadd(byte[], double, byte[])
     */
    public Long zadd(String key, double score, Object member) {
        byte[] k = SafeEncoder.encode(key);
        byte[] m = serializer.serialize(member);
        return execute(jedis -> jedis.zadd(k, score, m));
    }

    /**
     * @see redis.clients.jedis.BinaryJedis#zrevrange(byte[], long, long)
     */
    public <T> Set<T> zrevrange(String key, long start, long end) {
        byte[] k = SafeEncoder.encode(key);
        Set<byte[]> r = execute(jedis -> jedis.zrevrange(k, start, end));
        return r.stream().<T>map(serializer::deserialize).collect(Collectors.toSet());
    }

    /**
     * @see redis.clients.jedis.BinaryJedis#zrevrank(byte[], byte[])
     */
    public Long zrevrank(String key, Object member) {
        byte[] k = SafeEncoder.encode(key);
        byte[] m = serializer.serialize(member);
        return execute(jedis -> jedis.zrevrank(k, m));
    }

    public boolean exists(String key) {
        byte[] k = SafeEncoder.encode(key);
        return execute(jedis -> jedis.exists(k));
    }

    public Long incr(String key) {
        byte[] k = SafeEncoder.encode(key);
        return execute(jedis -> jedis.incr(k));
    }

    public Long expire(String key, int seconds) {
        byte[] k = SafeEncoder.encode(key);
        return execute(jedis -> jedis.expire(k, seconds));
    }

    /**
     * 将信息发送到指定的频道。
     *
     * @param channel 频道
     * @param message 信息
     * @return 接收到信息的订阅者数量
     */
    public Long publish(String channel, Object message) {
        byte[] c = SafeEncoder.encode(channel);
        byte[] m = serializer.serialize(message);
        return execute(jedis -> jedis.publish(c, m));
    }

}
