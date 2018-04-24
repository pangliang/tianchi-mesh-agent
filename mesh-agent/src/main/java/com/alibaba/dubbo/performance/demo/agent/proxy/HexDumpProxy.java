package com.alibaba.dubbo.performance.demo.agent.proxy;

import com.alibaba.dubbo.performance.demo.agent.registry.EtcdRegistry;
import com.alibaba.dubbo.performance.demo.agent.registry.IRegistry;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.springframework.beans.factory.annotation.Value;
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
    @Value("${server.port}")
    int LOCAL_PORT;

    @Value("${dubbo.protocol.port}")
    int REMOTE_PORT;

    String REMOTE_HOST = "127.0.0.1";

    IRegistry registry = new EtcdRegistry(System.getProperty("etcd.url"));

    @Override
    public void run(String... strings) throws Exception {

        registry.register("com.alibaba.dubbo.performance.demo.provider.IHelloService",LOCAL_PORT);

        EventLoopGroup bossGroup = new NioEventLoopGroup(4);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new HexDumpProxyInitializer(REMOTE_HOST, REMOTE_PORT))
                .childOption(ChannelOption.AUTO_READ, false)
                .bind(LOCAL_PORT).sync().channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
