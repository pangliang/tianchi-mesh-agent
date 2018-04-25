package com.alibaba.dubbo.performance.demo.agent.http;

import com.alibaba.dubbo.performance.demo.agent.dubbo.model.RpcCallback;
import com.alibaba.dubbo.performance.demo.agent.dubbo.model.RpcResponse;
import com.alibaba.dubbo.performance.demo.agent.registry.Endpoint;
import com.alibaba.dubbo.performance.demo.agent.registry.EtcdRegistry;
import com.alibaba.dubbo.performance.demo.agent.registry.IRegistry;
import com.alibaba.dubbo.performance.demo.agent.utils.NettyUtils;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private Logger logger = LoggerFactory.getLogger(HttpAgent.class);

    private int localPort = Integer.valueOf(System.getProperty("server.port"));

    private static String interfaceName = "com.alibaba.dubbo.performance.demo.provider.IHelloService";
    private static String method = "hash";
    private static String parameterTypesString = "Ljava/lang/String;";

    private static IRegistry registry = new EtcdRegistry(System.getProperty("etcd.url"));
    private static List<Endpoint> endpoints = null;
    private static Object lock = new Object();
    private static AtomicInteger counter = new AtomicInteger(0);
    private static AtomicInteger activeClient = new AtomicInteger(0);

    @Override
    public void run(String... strings) throws Exception {
        ServerBootstrap b = NettyUtils.createServerBootstrap(32);
        try {

            new Thread(){
                @Override
                public void run()  {
                    while(true){
                        try {
                            Thread.sleep(1000 * 3);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        if(endpoints == null){
                            continue ;
                        }
                        for (Endpoint e : endpoints) {
                            long avgLatency = e.avgLatency();
                            logger.info("clients:{}, endpoint:{}:{}, active:{}, avgLatency:{}, times:{}", activeClient.get(), e.getHost(), e.getPort(), e.getActive(), avgLatency, e.getTimes());
                        }
                    }

                }
            }.start();

            b.childHandler(new ChannelInitializer<SocketChannel>() {
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
            b.config().group().shutdownGracefully();
            b.config().childGroup().shutdownGracefully();
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
            if (pos < 0) {
                return;
            }

            String parameter = body.substring(pos + paramLen);

            // 轮询
            int count = counter.addAndGet(1);
            Endpoint endpoint = endpoints.get(count % endpoints.size());

            int clients = activeClient.addAndGet(1);

            if(count % 10 >= 5){
                // 最低 延迟
                long minAvgLatency = Long.MAX_VALUE;
                for (Endpoint e : endpoints) {
                    long avgLatency = e.avgLatency();
                    if(avgLatency < minAvgLatency){
                        minAvgLatency = avgLatency;
                        endpoint = e;
                    }
                }
            }

            endpoint.start();
            long start = System.nanoTime();
            Endpoint finalEndpoint = endpoint;

            RpcCallback callback = new RpcCallback() {
                @Override
                public void handler(RpcResponse response) {
                    long elapsed = System.nanoTime() - start;
                    finalEndpoint.finish(elapsed);
                    activeClient.decrementAndGet();

                    byte[] result = response.getBytes();
                    FullHttpResponse httpResponse = new DefaultFullHttpResponse(
                        HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                        Unpooled.wrappedBuffer(result));

                    httpResponse.headers().add("Content-Length", result.length);

                    ctx.channel().writeAndFlush(httpResponse);
                }
            };

            endpoint.getRpcClient().invoke(interfaceName, method, parameterTypesString, parameter, callback);

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
