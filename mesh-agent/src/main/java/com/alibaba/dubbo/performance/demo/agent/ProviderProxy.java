package com.alibaba.dubbo.performance.demo.agent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnNotWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * @author wei.liang
 * @date 2018/4/23
 */
@SpringBootApplication
@ConditionalOnNotWebApplication
public class ProviderProxy implements CommandLineRunner{
    public static final int BUF_SIZE = 4096;

    @Value("${server.port}")
    int serverPort;

    @Value("${dubbo.protocol.port}")
    int dubboPort;

    @Override
    public void run(String... strings) throws Exception {
        ServerSocket serverSocket = new ServerSocket(serverPort);
        new AcceptThread(serverSocket).start();
    }

    class AcceptThread extends Thread{
        ServerSocket serverSocket;
        AcceptThread(ServerSocket serverSocket){
            this.serverSocket = serverSocket;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    Socket client = serverSocket.accept();
                    Socket dubbo = new Socket("127.0.0.1", dubboPort);
                    new PumpThread(client.getInputStream(), dubbo.getOutputStream()).start();
                    new PumpThread(dubbo.getInputStream(), client.getOutputStream()).start();
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    class PumpThread extends Thread{
        InputStream in ;
        OutputStream out;
        PumpThread(InputStream in, OutputStream out){
            this.in = in;
            this.out = out;
        }

        @Override
        public void run() {
            byte[] buf = new byte[BUF_SIZE];
            try {
                while (true){
                    int size = in.read(buf);
                    if(size <=0 ){
                        return ;
                    }

                    out.write(buf, 0 , size);
                }

            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }
}
