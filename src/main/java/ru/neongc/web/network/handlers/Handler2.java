package ru.neongc.web.network.handlers;

import io.netty.handler.codec.http.HttpRequest;
import ru.neongc.web.network.PacketBuffer;
 
public class Handler2 extends UriHandlerBased {
 
    @Override
    public void process(HttpRequest request, PacketBuffer buff) {
        buff.writeString("HELLO WORLD!!");
    }

    @Override
    public String uri() {
        return "/";
    }
}