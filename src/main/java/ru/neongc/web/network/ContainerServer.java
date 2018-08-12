package ru.neongc.web.network;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import ru.neongc.web.network.filter.ServerPipeline;

public class ContainerServer {
    
    private EventLoopGroup producer;
    private EventLoopGroup consumer;
    private Class<? extends io.netty.channel.socket.ServerSocketChannel> serverSocketChannel;

    public void init() throws Exception {
        boolean hasEpoll = Epoll.isAvailable();
        if (hasEpoll) {
            producer = new EpollEventLoopGroup();
            consumer = new EpollEventLoopGroup();
            serverSocketChannel = EpollServerSocketChannel.class;
        } else {
            producer = new NioEventLoopGroup();
            consumer = new NioEventLoopGroup();
            serverSocketChannel = NioServerSocketChannel.class;
        }
        try {
            ServerBootstrap bootstrap = new ServerBootstrap()
                .group(this.producer, this.consumer)
                .channel(this.serverSocketChannel)
                .childHandler(new ServerPipeline());
            bootstrap.bind(8979);
            System.out.println("Сервер запущен");
        } catch (Exception ex) {
            ex.printStackTrace();
            this.stop();
        }
    }
    
    public void stop() {
        System.out.println("Остановка сервера");
        producer.shutdownGracefully();
        consumer.shutdownGracefully();
    }
}