package com.alibaba.dubbo.performance.demo.agent.http;

import com.alibaba.dubbo.performance.demo.agent.registry.Endpoint;
import com.alibaba.dubbo.performance.demo.agent.registry.EtcdRegistry;
import com.alibaba.dubbo.performance.demo.agent.registry.IRegistry;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnNotWebApplication;

import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author wei.liang
 * @date 2018/4/24
 */
@SpringBootApplication
@ConditionalOnNotWebApplication
public class HttpAgent implements CommandLineRunner {
    @Value("${server.port}")
    int LOCAL_PORT;

    private static String interfaceName = "com.alibaba.dubbo.performance.demo.provider.IHelloService";
    private static String method = "hash";
    private static String parameterTypesString = "Ljava/lang/String;";

    private static IRegistry registry = new EtcdRegistry(System.getProperty("etcd.url"));
    private static List<Endpoint> endpoints = null;
    private static Object lock = new Object();
    private static AtomicInteger endPointIndex = new AtomicInteger(0);
    private static AtomicInteger activeClient = new AtomicInteger(0);

    @Override
    public void run(String... strings) throws Exception {

        EventLoopGroup bossGroup = new NioEventLoopGroup(4);
        EventLoopGroup workerGroup = new NioEventLoopGroup(512);
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                //.handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(
                            //new LoggingHandler(LogLevel.INFO),
                            new HttpRequestDecoder(),
                            new HttpObjectAggregator(4096),
                            new HttpResponseEncoder(),
                            new HttpHandler()
                        );
                    }
                })
                .bind(LOCAL_PORT).sync().channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    class HttpHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
        private Logger logger = LoggerFactory.getLogger(HttpHandler.class);
        String paramName = "&parameter=";
        int paramLen = paramName.length();

        @Override
        public void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) throws Exception {

            if (null == endpoints) {
                synchronized (lock) {
                    if (null == endpoints) {
                        endpoints = registry.find("com.alibaba.dubbo.performance.demo.provider.IHelloService");
                        logger.info("endpoints: {}", endpoints);
                    }
                }
            }

            String body = msg.content().toString(Charset.defaultCharset());

            int pos = body.indexOf(paramName);
            if(pos < 0){
                return;
            }

            String parameter = body.substring(pos + paramLen);

            // 轮询
            int index = endPointIndex.addAndGet(1);
            Endpoint endpoint = endpoints.get(index % endpoints.size());

            int clients = activeClient.addAndGet(1);

            // qps 能力
            double qps = 0;
            for (Endpoint e : endpoints) {
                qps += e.qps();
            }
            for (Endpoint e : endpoints) {
                //logger.info("clients:{}, allqps:{}, endpoint:{}:{}, active:{}, qps:{}, times:{}", clients, qps, e.getHost(), e.getPort(), e.getActive(), e.qps(), e.getTimes());
                if (e.getActive() < (clients * (e.qps() / qps))) {
                    endpoint = e;
                    break;
                }
            }

            endpoint.start();
            long start = System.nanoTime();

            byte[] result = (byte[])endpoint.getRpcClient().invoke(interfaceName, method, parameterTypesString, parameter);

            long elapsed = System.nanoTime() - start;
            endpoint.finish(elapsed);
            activeClient.decrementAndGet();

            FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                Unpooled.wrappedBuffer(result));
            response.headers().add(HttpHeaderNames.CONTENT_LENGTH, result.length);


            ctx.channel().writeAndFlush(response);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            cause.printStackTrace();

            FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR);

            ctx.channel().writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
