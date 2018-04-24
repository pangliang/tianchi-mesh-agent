package com.alibaba.dubbo.performance.demo.agent.dubbo.model;

import java.util.concurrent.ConcurrentHashMap;

public class RpcRequestHolder {

    // key: requestId     value: RpcFuture
    private static ConcurrentHashMap<String,RpcCallback> processingRpc = new ConcurrentHashMap<>();

    public static void put(String requestId,RpcCallback callback){
        processingRpc.put(requestId,callback);
    }

    public static RpcCallback get(String requestId){
        return processingRpc.get(requestId);
    }

    public static void remove(String requestId){
        processingRpc.remove(requestId);
    }
}
