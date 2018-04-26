package com.alibaba.dubbo.performance.demo.agent.dubbo;

import com.alibaba.dubbo.performance.demo.agent.dubbo.model.Bytes;
import com.alibaba.dubbo.performance.demo.agent.dubbo.model.Request;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 *         +-------------------------------------------------+
           |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
 +--------+-------------------------------------------------+----------------+
 |00000000| da bb c6 00 00 00 00 00 00 00 00 00 00 00 03 99 |................|
 |00000010| 22 32 2e 30 2e 31 22 0a 22 63 6f 6d 2e 61 6c 69 |"2.0.1"."com.ali|
 |00000020| 62 61 62 61 2e 64 75 62 62 6f 2e 70 65 72 66 6f |baba.dubbo.perfo|
 |00000030| 72 6d 61 6e 63 65 2e 64 65 6d 6f 2e 70 72 6f 76 |rmance.demo.prov|
 |00000040| 69 64 65 72 2e 49 48 65 6c 6c 6f 53 65 72 76 69 |ider.IHelloServi|
 |00000050| 63 65 22 0a 6e 75 6c 6c 0a 22 68 61 73 68 22 0a |ce".null."hash".|
 |00000060| 22 4c 6a 61 76 61 2f 6c 61 6e 67 2f 53 74 72 69 |"Ljava/lang/Stri|
 |00000070| 6e 67 3b 22 0a 22 62 59 7a 45 43 78 68 38 43 79 |ng;"."bYzECxh8Cy|
 |00000080| 70 72 74 77 79 62 52 53 4a 4d 64 7a 76 31 52 73 |prtwybRSJMdzv1Rs|
 |00000090| 31 63 77 75 5a 57 70 32 74 4a 36 62 51 5a 4d 72 |1cwuZWp2tJ6bQZMr|
 |000000a0| 70 75 6c 59 34 65 4c 57 59 51 6f 70 78 39 66 6a |pulY4eLWYQopx9fj|
 |000000b0| 4c 41 34 67 48 49 6f 70 67 5a 68 56 37 4a 69 58 |LA4gHIopgZhV7JiX|
 |000000c0| 66 36 32 32 45 76 4c 51 57 6e 5a 73 6e 53 52 62 |f622EvLQWnZsnSRb|
 |000000d0| 61 30 55 75 6a 47 49 6a 79 51 4a 68 6d 61 6b 35 |a0UujGIjyQJhmak5|
 |000000e0| 4a 79 61 34 4e 62 37 56 48 54 62 78 79 56 6d 48 |Jya4Nb7VHTbxyVmH|
 |000000f0| 51 73 38 59 75 52 4e 37 56 43 36 70 46 6c 6e 69 |Qs8YuRN7VC6pFlni|
 |00000100| 49 73 4c 4b 45 4a 74 61 53 75 59 36 66 30 59 74 |IsLKEJtaSuY6f0Yt|
 |00000110| 79 44 68 68 37 31 79 61 47 64 31 31 6e 73 51 55 |yDhh71yaGd11nsQU|
 |00000120| 4a 4c 54 74 73 79 53 37 47 38 68 51 54 71 46 68 |JLTtsyS7G8hQTqFh|
 |00000130| 65 43 54 64 46 43 71 59 6a 5a 37 31 76 6c 4b 7a |eCTdFCqYjZ71vlKz|
 |00000140| 4a 37 75 4b 64 49 68 51 31 5a 63 71 74 65 61 69 |J7uKdIhQ1Zcqteai|
 |00000150| 71 52 5a 6b 4c 51 79 67 41 55 37 4d 4e 77 45 4e |qRZkLQygAU7MNwEN|
 |00000160| 41 51 77 39 6a 68 79 41 4e 74 68 49 36 50 4b 74 |AQw9jhyANthI6PKt|
 |00000170| 68 71 39 61 32 42 74 6d 79 6e 32 4a 43 5a 35 64 |hq9a2Btmyn2JCZ5d|
 |00000180| 35 72 67 62 79 36 78 76 48 5a 34 42 73 4b 6a 56 |5rgby6xvHZ4BsKjV|
 |00000190| 65 41 34 6e 42 4b 4e 38 67 44 37 46 35 76 62 7a |eA4nBKN8gD7F5vbz|
 |000001a0| 64 44 55 73 69 39 4d 79 4d 5a 65 36 38 52 79 48 |dDUsi9MyMZe68RyH|
 |000001b0| 4e 75 38 59 76 32 6a 32 6c 66 4e 4d 73 57 76 54 |Nu8Yv2j2lfNMsWvT|
 |000001c0| 33 61 59 69 75 73 67 57 51 58 77 4e 46 63 4b 6d |3aYiusgWQXwNFcKm|
 |000001d0| 42 6d 32 4f 74 59 62 71 7a 32 68 50 45 62 5a 49 |Bm2OtYbqz2hPEbZI|
 |000001e0| 79 4f 64 7a 6a 41 71 69 35 70 4e 36 47 30 37 67 |yOdzjAqi5pN6G07g|
 |000001f0| 33 34 70 74 30 52 59 6e 59 6b 72 65 5a 78 72 72 |34pt0RYnYkreZxrr|
 |00000200| 32 48 43 32 53 78 6a 5a 45 73 69 35 50 36 77 41 |2HC2SxjZEsi5P6wA|
 |00000210| 41 37 78 4e 58 47 5a 4a 6e 63 72 50 44 67 69 43 |A7xNXGZJncrPDgiC|
 |00000220| 68 36 4c 4f 45 33 6d 62 43 5a 51 70 71 6b 4c 76 |h6LOE3mbCZQpqkLv|
 |00000230| 31 47 45 51 55 6b 6f 44 56 6a 6e 4c 4c 66 4a 72 |1GEQUkoDVjnLLfJr|
 |00000240| 77 52 69 39 5a 57 38 51 6b 30 38 79 71 52 36 4d |wRi9ZW8Qk08yqR6M|
 |00000250| 6c 30 79 4a 38 73 75 79 61 77 72 4c 62 64 49 37 |l0yJ8suyawrLbdI7|
 |00000260| 55 65 71 6d 71 35 4a 32 65 39 35 72 4a 39 55 68 |Ueqmq5J2e95rJ9Uh|
 |00000270| 4d 33 6b 45 37 35 61 70 42 4b 77 4a 79 45 67 78 |M3kE75apBKwJyEgx|
 |00000280| 4b 6c 37 30 4e 74 4c 6a 79 32 53 30 77 79 61 54 |Kl70NtLjy2S0wyaT|
 |00000290| 7a 33 54 41 58 6d 45 37 32 58 62 45 76 53 55 37 |z3TAXmE72XbEvSU7|
 |000002a0| 6d 76 79 77 33 45 64 6c 4b 62 74 32 53 6c 39 73 |mvyw3EdlKbt2Sl9s|
 |000002b0| 37 75 51 68 37 39 65 43 61 51 74 64 4e 62 6a 55 |7uQh79eCaQtdNbjU|
 |000002c0| 68 48 41 30 6f 47 6f 51 44 71 44 47 74 49 46 62 |hHA0oGoQDqDGtIFb|
 |000002d0| 4c 75 67 45 4d 52 6f 35 39 68 73 47 69 68 57 43 |LugEMRo59hsGihWC|
 |000002e0| 41 77 46 32 67 4a 6c 6c 63 77 5a 36 65 59 79 70 |AwF2gJllcwZ6eYyp|
 |000002f0| 63 72 56 51 32 55 32 59 79 6e 4b 6e 4e 31 43 30 |crVQ2U2YynKnN1C0|
 |00000300| 46 72 7a 6a 53 6c 69 6e 49 73 76 63 41 73 56 73 |FrzjSlinIsvcAsVs|
 |00000310| 59 6b 37 4c 72 37 4b 6b 35 47 76 71 65 51 38 6c |Yk7Lr7Kk5GvqeQ8l|
 |00000320| 73 58 45 78 52 47 53 6c 57 4a 67 39 37 58 50 7a |sXExRGSlWJg97XPz|
 |00000330| 41 73 69 37 50 76 4d 37 69 68 76 51 51 6d 49 35 |Asi7PvM7ihvQQmI5|
 |00000340| 4b 4a 66 54 6c 4c 65 73 46 55 46 52 30 47 67 70 |KJfTlLesFUFR0Ggp|
 |00000350| 33 36 6c 56 61 5a 61 37 41 4a 35 38 54 49 75 79 |36lVaZa7AJ58TIuy|
 |00000360| 64 31 22 0a 7b 22 70 61 74 68 22 3a 22 63 6f 6d |d1".{"path":"com|
 |00000370| 2e 61 6c 69 62 61 62 61 2e 64 75 62 62 6f 2e 70 |.alibaba.dubbo.p|
 |00000380| 65 72 66 6f 72 6d 61 6e 63 65 2e 64 65 6d 6f 2e |erformance.demo.|
 |00000390| 70 72 6f 76 69 64 65 72 2e 49 48 65 6c 6c 6f 53 |provider.IHelloS|
 |000003a0| 65 72 76 69 63 65 22 7d 0a                      |ervice"}.       |
 +--------+-------------------------------------------------+----------------+
 */
public class DubboRpcEncoder extends MessageToByteEncoder{
    private static final String interfaceName = "com.alibaba.dubbo.performance.demo.provider.IHelloService";
    private static final String method = "hash";
    private static final String parameterTypesString = "Ljava/lang/String;";
    private static final String dubboVersion = "2.0.1";

    private static final byte[] part1 = String.format("\"%s\"\n\"%s\"\n%s\n\"%s\"\n\"%s\"\n\"",
                                        dubboVersion, interfaceName, "null", method, parameterTypesString).getBytes();
    private static final byte[] part2 = String.format("\"\n{\"path\":\"%s\"}\n", interfaceName).getBytes();
    private static final int len = part1.length + part2.length;

    // header length.
    protected static final int HEADER_LENGTH = 16;
    // magic header.
    protected static final short MAGIC = (short) 0xdabb;
    // message flag.
    protected static final byte FLAG_REQUEST = (byte) 0x80;
    protected static final byte FLAG_TWOWAY = (byte) 0x40;
    protected static final byte FLAG_EVENT = (byte) 0x20;

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf buffer) throws Exception {
        Request req = (Request)msg;

        buffer.writeShort(MAGIC);

        // set request and serialization flag.
        byte flag = (byte) (FLAG_REQUEST | 6);
        if (req.isTwoWay()) flag |= FLAG_TWOWAY;
        if (req.isEvent()) flag |= FLAG_EVENT;
        buffer.writeByte(flag);

        buffer.writeByte(0);

        // set request id.
        buffer.writeLong(req.getId());

        byte[] data = (byte[])req.getData();

        // data len.
        buffer.writeInt(len + data.length);

        buffer.writeBytes(part1);
        buffer.writeBytes(data);
        buffer.writeBytes(part2);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
    }
}
