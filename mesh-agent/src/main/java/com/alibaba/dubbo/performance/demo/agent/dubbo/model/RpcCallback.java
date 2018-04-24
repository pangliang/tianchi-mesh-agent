package com.alibaba.dubbo.performance.demo.agent.dubbo.model;

public interface RpcCallback  {
    public void handler(RpcResponse response);
}
