package com.alibaba.dubbo.performance.demo.agent.http;

import com.alibaba.dubbo.performance.demo.agent.dubbo.DubboRpcDecoder;
import com.alibaba.dubbo.performance.demo.agent.dubbo.DubboRpcEncoder;
import com.alibaba.dubbo.performance.demo.agent.dubbo.RpcClientHandler;
import com.alibaba.dubbo.performance.demo.agent.dubbo.model.Request;
import com.alibaba.dubbo.performance.demo.agent.dubbo.model.RpcCallback;
import com.alibaba.dubbo.performance.demo.agent.dubbo.model.RpcRequestHolder;
import com.alibaba.dubbo.performance.demo.agent.dubbo.model.RpcResponse;
import com.alibaba.dubbo.performance.demo.agent.registry.Endpoint;
import com.alibaba.dubbo.performance.demo.agent.registry.EtcdRegistry;
import com.alibaba.dubbo.performance.demo.agent.registry.IRegistry;
import com.alibaba.dubbo.performance.demo.agent.utils.NettyUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnNotWebApplication;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author wei.liang
 * @date 2018/4/24
 */
@SpringBootApplication
@ConditionalOnNotWebApplication
public class HttpAgent implements CommandLineRunner {
    private Logger logger = LoggerFactory.getLogger(HttpAgent.class);
    private int localPort = Integer.valueOf(System.getProperty("server.port"));

    private static IRegistry registry = new EtcdRegistry(System.getProperty("etcd.url"));
    private static List<Endpoint> endpoints = new ArrayList<>(3);
    private static AtomicInteger counter = new AtomicInteger(0);
    private static AtomicInteger activeClient = new AtomicInteger(0);

    @Override
    public void run(String... strings) throws Exception {
        ServerBootstrap serverBootstrap = NettyUtils.createServerBootstrap(16);
        try {
            List<Endpoint> endpointList = registry.find("com.alibaba.dubbo.performance.demo.provider.IHelloService");
            logger.info("endpointList: {}", endpointList);

            Bootstrap b = NettyUtils.createBootstrap(serverBootstrap.config().childGroup())
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

            for (Endpoint e : endpointList) {
                e.setChannel(b.connect(e.getHost(), e.getPort()).sync().channel());
                endpoints.add(e.getPort() % endpointList.size(), e);
            }

            serverBootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) throws Exception {
                    ch.pipeline().addLast(
                        new HttpRequestDecoder(),
                        new HttpObjectAggregator(4096),
                        new HttpResponseEncoder(),
                        new HttpHandler()
                    );
                }
            }).bind(localPort).sync().channel().closeFuture().sync();
        } finally {
            serverBootstrap.config().group().shutdownGracefully();
            serverBootstrap.config().childGroup().shutdownGracefully();
        }
    }

    static class HttpHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
        private Logger logger = LoggerFactory.getLogger(HttpHandler.class);
        private static String params
            = "interface=com.alibaba.dubbo.performance.demo.provider"
            + ".IHelloService&method=hash&parameterTypesString=Ljava%2Flang%2FString%3B&parameter=";
        private static int paramsLen = params.length();

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            //logger.info("channelActive:{}", ctx.channel().remoteAddress());
        }

        @Override
        public void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) throws Exception {

            int contentLength = msg.headers().getInt("Content-Length");
            int dataLen = contentLength - paramsLen;
            byte[] data = new byte[dataLen];
            msg.content().getBytes(paramsLen, data);

            final Endpoint endpoint = getEndpoint();
            endpoint.start();
            long start = System.nanoTime();

            RpcCallback callback = new RpcCallback() {
                @Override
                public void handler(RpcResponse response) {
                    long elapsed = System.nanoTime() - start;
                    endpoint.finish(elapsed);
                    activeClient.decrementAndGet();

                    byte[] result = response.getBytes();
                    FullHttpResponse httpResponse = new DefaultFullHttpResponse(
                        HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                        Unpooled.wrappedBuffer(result));

                    httpResponse.headers().add("Content-Length", result.length);

                    ctx.channel().writeAndFlush(httpResponse);
                }
            };

            Request request = new Request();
            request.setVersion("2.0.0");
            request.setTwoWay(true);
            request.setData(data);

            RpcRequestHolder.put(String.valueOf(request.getId()), callback);

            endpoint.getChannel().writeAndFlush(request);

        }

        private Endpoint getEndpoint() {
            // 按1:2:3
            int count = counter.addAndGet(1);
            int index = count % 6;
            switch (index) {
                case 0:
                    return endpoints.get(0);
                case 1:
                case 2:
                    return endpoints.get(1);
                default:
                    return endpoints.get(2);
            }

        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            logger.error("error", cause);
            FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR);
            ctx.channel().writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
