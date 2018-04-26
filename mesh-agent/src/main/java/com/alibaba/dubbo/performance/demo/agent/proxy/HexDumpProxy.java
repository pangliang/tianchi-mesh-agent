package com.alibaba.dubbo.performance.demo.agent.proxy;

import com.alibaba.dubbo.performance.demo.agent.registry.EtcdRegistry;
import com.alibaba.dubbo.performance.demo.agent.registry.IRegistry;
import com.alibaba.dubbo.performance.demo.agent.utils.NettyUtils;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnNotWebApplication;

/**
 * @author wei.liang
 * @date 2018/4/24
 */
@SpringBootApplication
@ConditionalOnNotWebApplication
public class HexDumpProxy implements CommandLineRunner {

    private String remoteHost = "127.0.0.1";
    private IRegistry registry = new EtcdRegistry(System.getProperty("etcd.url"));
    private int localPort = Integer.valueOf(System.getProperty("server.port"));
    private int remotePort = Integer.valueOf(System.getProperty("dubbo.protocol.port"));

    @Override
    public void run(String... strings) throws Exception {

        registry.register("com.alibaba.dubbo.performance.demo.provider.IHelloService", localPort);

        ServerBootstrap b = NettyUtils.createServerBootstrap(4);
        try {

            b.childOption(ChannelOption.AUTO_READ, false)
            .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) throws Exception {
                    ch.pipeline().addLast(
                        //new LoggingHandler(LogLevel.INFO),
                        new HexDumpProxyFrontendHandler(remoteHost, remotePort)
                    );
                }
            }).bind(localPort).sync().channel().closeFuture().sync();
        } finally {
            b.config().group().shutdownGracefully();
            b.config().childGroup().shutdownGracefully();
        }
    }
}
