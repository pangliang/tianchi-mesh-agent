package com.alibaba.dubbo.performance.demo.agent.http;

import com.alibaba.dubbo.performance.demo.agent.dubbo.model.*;
import com.alibaba.dubbo.performance.demo.agent.registry.Endpoint;
import com.alibaba.dubbo.performance.demo.agent.registry.EtcdRegistry;
import com.alibaba.dubbo.performance.demo.agent.registry.IRegistry;
import com.alibaba.dubbo.performance.demo.agent.utils.NettyUtils;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnNotWebApplication;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
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

    private static IRegistry registry = new EtcdRegistry(System.getProperty("etcd.url"));
    private static List<Endpoint> endpoints = null;
    private static Object lock = new Object();
    private static AtomicInteger counter = new AtomicInteger(0);
    private static AtomicInteger activeClient = new AtomicInteger(0);

    @Override
    public void run(String... strings) throws Exception {
        ServerBootstrap b = NettyUtils.createServerBootstrap(16);
        try {
            if (null == endpoints) {
                synchronized (lock) {
                    if (null == endpoints) {
                        endpoints = registry.find("com.alibaba.dubbo.performance.demo.provider.IHelloService");
                        logger.info("endpoints: {}", endpoints);
                    }
                }
            }

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

    static class HttpHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
        private Logger logger = LoggerFactory.getLogger(HttpHandler.class);
        private static String params = "interface=com.alibaba.dubbo.performance.demo.provider.IHelloService&method=hash&parameterTypesString=Ljava%2Flang%2FString%3B&parameter=";
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

        private Endpoint getEndpoint(){
            // 轮询
            int count = counter.addAndGet(1);
            Endpoint endpoint = endpoints.get(count % endpoints.size());

            int clients = activeClient.addAndGet(1);

            if(count % (endpoints.size()*2) == endpoints.size()){
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
            return endpoint;
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
