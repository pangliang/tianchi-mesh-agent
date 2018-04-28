package com.alibaba.dubbo.performance.demo.agent.dubbo.model;

import java.util.concurrent.ConcurrentHashMap;

public class RpcRequestHolder {

    // key: requestId     value: RpcFuture
    private static ConcurrentHashMap<Long,RpcCallback> processingRpc = new ConcurrentHashMap<>(500000, 1, 256);

    public static void put(Long requestId,RpcCallback callback){
        processingRpc.put(requestId,callback);
    }

    public static RpcCallback get(Long requestId){
        return processingRpc.get(requestId);
    }

    public static void remove(Long requestId){
        processingRpc.remove(requestId);
    }
}
