package com.alibaba.dubbo.performance.demo.agent.dubbo;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

public class ConnecManager {
    private Logger logger = LoggerFactory.getLogger(ConnecManager.class);
    private EventLoopGroup eventLoopGroup = new NioEventLoopGroup(4);

    private Bootstrap bootstrap;

    private int channelSize = 4;
    private Channel[] channels = null;
    private Object lock = new Object();
    private AtomicInteger count = new AtomicInteger(0);

    public ConnecManager() {
    }

    public Channel getChannel(String host, int port) throws Exception {
        int index = count.getAndAdd(1) % channelSize;
        if (null != channels) {
            return channels[index];
        }

        if (null == bootstrap) {
            synchronized (lock) {
                if (null == bootstrap) {
                    bootstrap = new Bootstrap()
                        .group(eventLoopGroup)
                        .option(ChannelOption.SO_KEEPALIVE, true)
                        .option(ChannelOption.TCP_NODELAY, true)
                        .option(ChannelOption.ALLOCATOR, UnpooledByteBufAllocator.DEFAULT)
                        .channel(NioSocketChannel.class)
                        .handler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel socketChannel) {
                                ChannelPipeline pipeline = socketChannel.pipeline();
                                pipeline.addLast(new DubboRpcEncoder());
                                pipeline.addLast(new DubboRpcDecoder());
                                pipeline.addLast(new RpcClientHandler());
                            }
                        });
                }
            }
        }

        if (null == channels) {
            synchronized (lock) {
                if (null == channels) {
                    logger.info("===============create channels===");
                    Channel[] cs = new Channel[channelSize];
                    for (int i = 0; i < channelSize; i++) {
                        cs[i] = bootstrap.connect(host, port).sync().channel();
                    }
                    this.channels = cs;
                }
            }
        }

        return channels[index];
    }
}
