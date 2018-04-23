package com.alibaba.dubbo.performance.demo.agent;

import com.alibaba.dubbo.performance.demo.agent.registry.Endpoint;
import com.alibaba.dubbo.performance.demo.agent.registry.EtcdRegistry;
import com.alibaba.dubbo.performance.demo.agent.registry.IRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
public class HelloController {

    private Logger logger = LoggerFactory.getLogger(HelloController.class);

    private IRegistry registry = new EtcdRegistry(System.getProperty("etcd.url"));

    private List<Endpoint> endpoints = null;
    private Object lock = new Object();
    private AtomicInteger endPointIndex = new AtomicInteger(0);

    private AtomicInteger activeClient = new AtomicInteger(0);

    @RequestMapping(value = "")
    public Object invoke(@RequestParam("interface") String interfaceName,
                         @RequestParam("method") String method,
                         @RequestParam("parameterTypesString") String parameterTypesString,
                         @RequestParam("parameter") String parameter) throws Exception {
        String type = System.getProperty("type");   // 获取type参数
        if ("consumer".equals(type)) {
            return consumer(interfaceName, method, parameterTypesString, parameter);
        } else {
            return "Environment variable type is needed to set to provider or consumer.";
        }
    }

    public Integer consumer(String interfaceName, String method, String parameterTypesString, String parameter)
        throws Exception {
        if (null == endpoints) {
            synchronized (lock) {
                if (null == endpoints) {
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

        Object result = endpoint.getRpcClient().invoke(interfaceName, method, parameterTypesString, parameter);

        long elapsed = System.nanoTime() - start;
        endpoint.finish(elapsed);

        activeClient.decrementAndGet();
        return Integer.valueOf(new String((byte[])result));
    }
}
