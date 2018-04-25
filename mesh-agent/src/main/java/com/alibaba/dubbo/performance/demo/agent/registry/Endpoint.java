package com.alibaba.dubbo.performance.demo.agent.registry;

import com.alibaba.dubbo.performance.demo.agent.dubbo.DubboRpcDecoder;
import com.alibaba.dubbo.performance.demo.agent.dubbo.DubboRpcEncoder;
import com.alibaba.dubbo.performance.demo.agent.dubbo.RpcClientHandler;
import com.alibaba.dubbo.performance.demo.agent.utils.NettyUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class Endpoint {
    private static Bootstrap bootstrap = NettyUtils.createBootstrap(4)
        .handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                ch.pipeline().addLast(
                    new DubboRpcEncoder(),
                    new DubboRpcDecoder(),
                    new RpcClientHandler()
                );
            }
        });
    private Channel channel;
    private AtomicLong times = new AtomicLong(0);
    private AtomicLong latency = new AtomicLong(0);
    private AtomicInteger active = new AtomicInteger(0);

    public Endpoint(String host,int port) throws InterruptedException {
        this.channel = bootstrap.connect(host, port).sync().channel();
    }

    public Channel getChannel() {
        return channel;
    }

    public void start(){
        active.incrementAndGet();
    }

    public void finish(long latency){
        this.latency.accumulateAndGet(latency, (long pre, long x)-> (pre*9+x)/10);
        times.incrementAndGet();
        active.decrementAndGet();
    }

    public long avgLatency(){
        if(this.times.longValue() == 0 || this.latency.get() == 0){
            return 0;
        }
        return this.latency.get();
    }

    public int getActive(){
        return this.active.intValue();
    }

    public long getTimes(){
        return this.times.longValue();
    }
}
