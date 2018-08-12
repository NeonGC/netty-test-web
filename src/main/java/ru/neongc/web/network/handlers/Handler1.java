package ru.neongc.web.network.handlers;

import io.netty.handler.codec.http.HttpRequest;
import ru.neongc.web.network.PacketBuffer;
 
public class Handler1 extends UriHandlerBased {
 
    @Override
    public void process(HttpRequest request, PacketBuffer buff) {
        buff.writeString("HELLO HANDLER1!");
    }

    @Override
    public String uri() {
        return "/h1";
    }
}