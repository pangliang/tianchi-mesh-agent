package com.alibaba.dubbo.performance.demo.agent.registry;

import io.netty.channel.Channel;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class Endpoint {
    private String host;
    private int port;
    private Channel channel;
    private AtomicLong times = new AtomicLong(0);
    private AtomicLong latency = new AtomicLong(0);
    private AtomicInteger active = new AtomicInteger(0);

    public Endpoint(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public Channel getChannel() {
        return channel;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public void start() {
        active.incrementAndGet();
    }

    public void finish(long latency) {
        this.latency.accumulateAndGet(latency, (long pre, long x) -> (pre * 99 + x) / 100);
        times.incrementAndGet();
        active.decrementAndGet();
    }

    public long avgLatency() {
        if (this.times.longValue() == 0 || this.latency.get() == 0) {
            return 0;
        }
        return this.latency.get();
    }

    public int getActive() {
        return this.active.intValue();
    }

    public long getTimes() {
        return this.times.longValue();
    }
}
