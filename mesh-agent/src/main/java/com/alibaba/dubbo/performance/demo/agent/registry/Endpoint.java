package com.alibaba.dubbo.performance.demo.agent.registry;

import com.alibaba.dubbo.performance.demo.agent.dubbo.RpcClient;
import com.google.common.util.concurrent.AtomicDouble;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class Endpoint {
    private final String host;
    private final int port;
    private RpcClient rpcClient;
    private AtomicLong times = new AtomicLong(0);
    private AtomicDouble elapsed = new AtomicDouble(0);
    private AtomicInteger active = new AtomicInteger(0);

    public Endpoint(String host,int port){
        this.host = host;
        this.port = port;
        this.rpcClient = new RpcClient(host, port);
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String toString(){
        return host + ":" + port;
    }

    public boolean equals(Object o){
        if (!(o instanceof Endpoint)){
            return false;
        }
        Endpoint other = (Endpoint) o;
        return other.host.equals(this.host) && other.port == this.port;
    }

    public int hashCode(){
        return host.hashCode() + port;
    }

    public RpcClient getRpcClient() {
        return rpcClient;
    }

    public void start(){
        active.incrementAndGet();
    }

    public void finish(long elapsed){
        this.elapsed.addAndGet(elapsed);
        times.incrementAndGet();
        active.decrementAndGet();
    }

    public double qps(){
        if(this.times.longValue() == 0 || this.elapsed.doubleValue() == 0){
            return 1;
        }
        return this.times.longValue()/(this.elapsed.doubleValue()/TimeUnit.SECONDS.toNanos(1));
    }

    public int getActive(){
        return this.active.intValue();
    }

    public long getTimes(){
        return this.times.longValue();
    }

    public void reset() {
        this.times.set(0);
        this.elapsed.set(0);
        this.active.set(0);
    }
}
