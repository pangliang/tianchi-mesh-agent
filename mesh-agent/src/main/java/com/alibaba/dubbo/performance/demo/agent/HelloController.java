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

    private RpcClient rpcClient = new RpcClient(registry);
    private List<Endpoint> endpoints = null;
    private Object lock = new Object();
    private OkHttpClient httpClient = new OkHttpClient();
    private AtomicInteger endPointIndex = new AtomicInteger(0);

    private AtomicInteger activeClient = new AtomicInteger(0);

    @RequestMapping(value = "")
    public Object invoke(@RequestParam("interface") String interfaceName,
                         @RequestParam("method") String method,
                         @RequestParam("parameterTypesString") String parameterTypesString,
                         @RequestParam("parameter") String parameter) throws Exception {
        String type = System.getProperty("type");   // 获取type参数
        if ("consumer".equals(type)){
            return consumer(interfaceName,method,parameterTypesString,parameter);
        }
        else if ("provider".equals(type)){
            return provider(interfaceName,method,parameterTypesString,parameter);
        }else {
            return "Environment variable type is needed to set to provider or consumer.";
        }
    }

    public byte[] provider(String interfaceName,String method,String parameterTypesString,String parameter) throws Exception {

        Object result = rpcClient.invoke(interfaceName,method,parameterTypesString,parameter);
        return (byte[]) result;
    }

    public Integer consumer(String interfaceName,String method,String parameterTypesString,String parameter) throws Exception {
        //logger.info("consumer:{}, {}, {}, {}", interfaceName, method, parameterTypesString, parameter);
        if (null == endpoints){
            synchronized (lock){
                if (null == endpoints){
                    endpoints = registry.find("com.alibaba.dubbo.performance.demo.provider.IHelloService");
                    logger.info("endpoints: {}", endpoints);
                }
            }
        }

        //// 轮询
        int index = endPointIndex.addAndGet(1);
        Endpoint endpoint = endpoints.get(index % endpoints.size());

        int clients = activeClient.addAndGet(1);

        // qps 能力
        double qps = 0;
        for(Endpoint e: endpoints){
            qps += e.qps();
        }
        for(Endpoint e: endpoints){
            logger.info("clients:{}, allqps:{}, endpoint:{}:{}, active:{}, qps:{}, times:{}", clients, qps, e.getHost(), e.getPort(), e.getActive(), e.qps(), e.getTimes());
            if(e.getActive() < (clients * (e.qps()/qps))){
                endpoint = e;
                break;
            }
        }

        endpoint.start();
        long start = System.nanoTime();

        String url =  "http://" + endpoint.getHost() + ":" + endpoint.getPort();

        RequestBody requestBody = new FormBody.Builder()
                .add("interface",interfaceName)
                .add("method",method)
                .add("parameterTypesString",parameterTypesString)
                .add("parameter",parameter)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            long elapsed = System.nanoTime() - start;
            endpoint.finish(elapsed);

            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
            byte[] bytes = response.body().bytes();
            String s = new String(bytes);
            return Integer.valueOf(s);
        }finally {
            activeClient.decrementAndGet();
        }
    }
}
