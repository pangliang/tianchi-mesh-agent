package com.alibaba.dubbo.performance.demo.agent;

import com.alibaba.dubbo.performance.demo.agent.dubbo.RpcClient;
import com.alibaba.dubbo.performance.demo.agent.registry.EtcdRegistry;
import com.alibaba.dubbo.performance.demo.agent.registry.IRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;

@RestController
public class HelloController {

    @Value("${server.port}")
    int localPort;

    @Value("${dubbo.protocol.port}")
    int dubboPort;

    private Logger logger = LoggerFactory.getLogger(HelloController.class);

    private RpcClient rpcClient = new RpcClient("127.0.0.1", Integer.valueOf(System.getProperty("dubbo.protocol.port")));
    private IRegistry registry = new EtcdRegistry(System.getProperty("etcd.url"));

    @PostConstruct
    public void init() throws Exception {
        registry.register("com.alibaba.dubbo.performance.demo.provider.IHelloService", localPort);
    }

    @RequestMapping(value = "")
    public Object invoke(@RequestParam("interface") String interfaceName,
                         @RequestParam("method") String method,
                         @RequestParam("parameterTypesString") String parameterTypesString,
                         @RequestParam("parameter") String parameter) throws Exception {

        Object result = rpcClient.invoke(interfaceName, method, parameterTypesString, parameter);
        return Integer.valueOf(new String((byte[])result));
    }
}
