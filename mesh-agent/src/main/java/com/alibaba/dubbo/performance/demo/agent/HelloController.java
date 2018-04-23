package com.alibaba.dubbo.performance.demo.agent;

import com.alibaba.dubbo.performance.demo.agent.dubbo.RpcClient;
import com.alibaba.dubbo.performance.demo.agent.registry.Endpoint;
import com.alibaba.dubbo.performance.demo.agent.registry.EtcdRegistry;
import com.alibaba.dubbo.performance.demo.agent.registry.IRegistry;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
public class HelloController {

    private Logger logger = LoggerFactory.getLogger(HelloController.class);
    
    private IRegistry registry = new EtcdRegistry(System.getProperty("etcd.url"));

    private List<Endpoint> endpoints = null;
    private Object lock = new Object();
    private AtomicInteger endPointIndex = new AtomicInteger(0);

    @RequestMapping(value = "")
    public Object invoke(@RequestParam("interface") String interfaceName,
                         @RequestParam("method") String method,
                         @RequestParam("parameterTypesString") String parameterTypesString,
                         @RequestParam("parameter") String parameter) throws Exception {
        String type = System.getProperty("type");   // 获取type参数
        if ("consumer".equals(type)){
            return consumer(interfaceName,method,parameterTypesString,parameter);
        }else {
            return "Environment variable type is needed to set to provider or consumer.";
        }
    }


    public Integer consumer(String interfaceName,String method,String parameterTypesString,String parameter) throws Exception {
        if (null == endpoints){
            synchronized (lock){
                if (null == endpoints){
                    endpoints = registry.find("com.alibaba.dubbo.performance.demo.provider.IHelloService");
                    logger.info("endpoints: {}", endpoints);

                    for(Endpoint e : endpoints){
                        endpoints.set(e.getPort()%endpoints.size(), e);
                    }
                }
            }
        }

        // 按1:2:3
        int index = endPointIndex.addAndGet(1) % 6;
        Endpoint endpoint = null;
        switch (index){
            case 1:
                endpoint = endpoints.get(0);
                break;
            case 2:
            case 4:
                endpoint = endpoints.get(1);
                break;
            default:
                endpoint = endpoints.get(2);
                break;
        }
        Object result = endpoint.getRpcClient().invoke(interfaceName,method,parameterTypesString,parameter);
        return Integer.valueOf(new String((byte[]) result));
    }
}
