package com.alibaba.dubbo.performance.demo.agent.dubbo.model;

public class RpcResponse {

    private Long requestId;
    private byte[] bytes;

    public Long getRequestId() {
        return requestId;
    }

    public void setRequestId(Long requestId) {
        this.requestId = requestId;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public void setBytes(byte[] bytes) {
        this.bytes = bytes;
    }
}
