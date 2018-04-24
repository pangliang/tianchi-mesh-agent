package com.alibaba.dubbo.performance.demo.agent.proxy;

import com.alibaba.dubbo.performance.demo.agent.registry.Endpoint;
import com.alibaba.dubbo.performance.demo.agent.registry.EtcdRegistry;
import com.alibaba.dubbo.performance.demo.agent.registry.IRegistry;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author wei.liang
 * @date 2018/4/24
 */
public class HexDumpProxyFrontendHandler extends ChannelInboundHandlerAdapter {
    private Logger logger = LoggerFactory.getLogger(HexDumpProxyFrontendHandler.class);

    private static IRegistry registry = new EtcdRegistry(System.getProperty("etcd.url"));
    private static Object lock = new Object();
    private static AtomicInteger endPointIndex = new AtomicInteger(0);
    private static List<Endpoint> endpoints = null;

    Channel outboundChannel = null;

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        if (null == endpoints) {
            synchronized (lock) {
                if (null == endpoints) {
                    List<Endpoint> endpoints = registry.find("com.alibaba.dubbo.performance.demo.provider.IHelloService");
                    logger.info("endpoints: {}", endpoints);
                    HexDumpProxyFrontendHandler.endpoints = endpoints;
                }
            }
        }

        int index = endPointIndex.addAndGet(1);
        Endpoint endpoint = endpoints.get(index % endpoints.size());

        final Channel inboundChannel = ctx.channel();
        Bootstrap b = new Bootstrap();
        b.group(inboundChannel.eventLoop())
            .channel(NioSocketChannel.class)
            .handler(new HexDumpProxyBackendHandler(inboundChannel))
            .option(ChannelOption.SO_KEEPALIVE, true)
            .option(ChannelOption.TCP_NODELAY, true)
            .option(ChannelOption.AUTO_READ, false);

        ChannelFuture f = b.connect(endpoint.getHost(), endpoint.getPort());
        outboundChannel = f.channel();
        f.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                if (future.isSuccess()) {
                    // connection complete start to read first data
                    inboundChannel.read();
                } else {
                    // Close the connection if the connection attempt has failed.
                    inboundChannel.close();
                }
            }
        });
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) {
        if (outboundChannel.isActive()) {
            outboundChannel.writeAndFlush(msg).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) {
                    if (future.isSuccess()) {
                        // was able to flush out data, start to read the next chunk
                        ctx.channel().read();
                    } else {
                        future.channel().close();
                    }
                }
            });
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (outboundChannel != null) {
            closeOnFlush(outboundChannel);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        closeOnFlush(ctx.channel());
    }

    /**
     * Closes the specified channel after all queued write requests are flushed.
     */
    static void closeOnFlush(Channel ch) {
        if (ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
