package com.alibaba.dubbo.performance.demo.agent;

import com.alibaba.dubbo.performance.demo.agent.proxy.HexDumpProxy;
import org.springframework.boot.SpringApplication;

/**
 * @author wei.liang
 * @date 2018/4/23
 */
public class Main {
    public static void main(String[] args) {
        String type = System.getProperty("type");   // 获取type参数
        if ("consumer".equals(type)){
            System.out.println("==================no web =======");
            SpringApplication app = new SpringApplication(HexDumpProxy.class);
            app.setWebEnvironment(false);
            app.run(args);
        }else {
            SpringApplication.run(AgentApp.class,args);
        }

    }
}
