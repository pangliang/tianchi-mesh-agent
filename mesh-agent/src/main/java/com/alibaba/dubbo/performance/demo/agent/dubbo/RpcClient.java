package com.alibaba.dubbo.performance.demo.agent.dubbo;

import com.alibaba.dubbo.performance.demo.agent.dubbo.model.*;
import com.alibaba.dubbo.performance.demo.agent.utils.NettyUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicInteger;

public class RpcClient {
    private String host;
    private int port;
    private Object lock = new Object();
    private int channelSize = 4;
    private Channel[] channels;
    private AtomicInteger count = new AtomicInteger(0);

    public RpcClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void invoke(String interfaceName, String method, String parameterTypesString, String parameter,
                       RpcCallback callback) throws Exception {

        Channel channel = getChannel();

        RpcInvocation invocation = new RpcInvocation();
        invocation.setMethodName(method);
        invocation.setAttachment("path", interfaceName);
        invocation.setParameterTypes(parameterTypesString);    // Dubbo内部用"Ljava/lang/String"来表示参数类型是String

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(out));
        JsonUtils.writeObject(parameter, writer);
        invocation.setArguments(out.toByteArray());

        Request request = new Request();
        request.setVersion("2.0.0");
        request.setTwoWay(true);
        request.setData(invocation);

        RpcRequestHolder.put(String.valueOf(request.getId()), callback);

        channel.writeAndFlush(request);
    }

    private Channel getChannel() throws InterruptedException {
        if (null == channels) {
            synchronized (lock) {
                if (null == channels) {
                    Bootstrap bootstrap = NettyUtils.createBootstrap(4)
                        .handler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel socketChannel) {
                                ChannelPipeline pipeline = socketChannel.pipeline();
                                pipeline.addLast(new DubboRpcEncoder());
                                pipeline.addLast(new DubboRpcDecoder());
                                pipeline.addLast(new RpcClientHandler());
                            }
                        });
                    Channel[] cs = new Channel[channelSize];
                    for (int i = 0; i < channelSize; i++) {
                        cs[i] = bootstrap.connect(host, port).sync().channel();
                    }
                    this.channels = cs;
                }
            }
        }

        int index = count.getAndAdd(1) % channelSize;
        return channels[index];
    }
}
