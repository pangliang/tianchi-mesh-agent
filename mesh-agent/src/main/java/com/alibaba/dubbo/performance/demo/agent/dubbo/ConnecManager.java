package com.alibaba.dubbo.performance.demo.agent.dubbo;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

public class ConnecManager {
    private Logger logger = LoggerFactory.getLogger(ConnecManager.class);
    private EventLoopGroup eventLoopGroup = new NioEventLoopGroup(4);

    private int channelSize = 1;
    private Channel[] channels = null;
    private final Object lock = new Object();
    private AtomicInteger count = new AtomicInteger(0);

    public ConnecManager() {
    }

    public Channel getChannel(String host,int port) throws Exception {
        int index = count.getAndAdd(1) % channelSize;
        if (null != channels) {
            return channels[index];
        }

        synchronized (lock){
            if (null == channels){
                logger.info("===============create channels===");
                Bootstrap bootstrap = new Bootstrap()
                    .group(eventLoopGroup)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.ALLOCATOR, UnpooledByteBufAllocator.DEFAULT)
                    .channel(NioSocketChannel.class)
                    .handler(new RpcClientInitializer());
                Channel[] cs = new Channel[channelSize];
                for(int i=0; i<channelSize; i++){
                    cs[i] = bootstrap.connect(host, port).sync().channel();
                }
                this.channels = cs;
            }
        }

        return channels[index];
    }
}
