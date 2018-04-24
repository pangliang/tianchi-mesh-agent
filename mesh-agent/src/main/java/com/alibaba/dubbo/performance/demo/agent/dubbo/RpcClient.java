package com.alibaba.dubbo.performance.demo.agent.dubbo;

import com.alibaba.dubbo.performance.demo.agent.dubbo.model.*;
import io.netty.channel.Channel;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

public class RpcClient {
    private ConnecManager connectManager;
    String host;
    int port;
    public RpcClient(String host,int port){
        this.host = host;
        this.port = port;
        this.connectManager = new ConnecManager();
    }

    public void invoke(String interfaceName, String method, String parameterTypesString, String parameter, RpcCallback callback) throws Exception {

        Channel channel = connectManager.getChannel(host, port);

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

        //logger.info("requestId=" + request.getId());

        RpcRequestHolder.put(String.valueOf(request.getId()),callback);

        channel.writeAndFlush(request);
    }
}
