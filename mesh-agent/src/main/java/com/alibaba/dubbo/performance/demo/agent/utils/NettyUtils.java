package com.alibaba.dubbo.performance.demo.agent.utils;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

/**
 * @author wei.liang
 * @date 2018/4/24
 */
public class NettyUtils {
    public static final boolean IS_LINUX = System.getProperty("os.name").equals("Linux");

    public static EventLoopGroup createEventLoopGroup(int nThreads){
        return IS_LINUX ? new EpollEventLoopGroup(nThreads) : new NioEventLoopGroup(nThreads);
    }

    public static ServerBootstrap createServerBootstrap(int bossThreads, int workerThreads){
        EventLoopGroup bossGroup = NettyUtils.createEventLoopGroup(bossThreads);
        EventLoopGroup workerGroup = NettyUtils.createEventLoopGroup(workerThreads);
        return new ServerBootstrap()
            .group(bossGroup, workerGroup)
            .channel(IS_LINUX ? EpollServerSocketChannel.class : NioServerSocketChannel.class)
            .option(ChannelOption.SO_BACKLOG, 1024)
            .childOption(ChannelOption.SO_KEEPALIVE, true)
            .childOption(ChannelOption.TCP_NODELAY, true)
            .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
    }

    public static Bootstrap createBootstrap(int nThreads){
        EventLoopGroup eventLoopGroup = NettyUtils.createEventLoopGroup(nThreads);
        return new Bootstrap()
            .group(eventLoopGroup)
            .channel(IS_LINUX ? EpollSocketChannel.class : NioSocketChannel.class)
            .option(ChannelOption.SO_KEEPALIVE, true)
            .option(ChannelOption.TCP_NODELAY, true)
            .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
    }
}
