package com.alibaba.dubbo.performance.demo.agent.dubbo;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.util.concurrent.atomic.AtomicInteger;

public class ConnecManager {
    private EventLoopGroup eventLoopGroup = new NioEventLoopGroup(4);

    private Bootstrap bootstrap;

    private int channelSize = 4;
    private Channel[] channels = null;
    private Object lock = new Object();
    private AtomicInteger count = new AtomicInteger(0);

    public ConnecManager() {
    }

    public Channel getChannel(String host,int port) throws Exception {
        int index = count.getAndAdd(1) % channelSize;
        if (null != channels) {
            return channels[index];
        }

        if (null == bootstrap) {
            synchronized (lock) {
                if (null == bootstrap) {
                    initBootstrap();
                }
            }
        }

        if (null == channels) {
            synchronized (lock){
                if (null == channels){
                    Channel[] channels = new Channel[channelSize];
                    for(int i=0; i<channelSize; i++){
                        channels[i] = bootstrap.connect(host, port).sync().channel();
                    }
                    this.channels = channels;
                }
            }
        }

        return channels[index];
    }

    public void initBootstrap() {

        bootstrap = new Bootstrap()
                .group(eventLoopGroup)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.ALLOCATOR, UnpooledByteBufAllocator.DEFAULT)
                .channel(NioSocketChannel.class)
                .handler(new RpcClientInitializer());
    }
}
