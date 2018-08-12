package ru.neongc.web.network.handlers;

import io.netty.handler.codec.http.HttpRequest;
import ru.neongc.web.network.PacketBuffer;

public abstract class UriHandlerBased {

    public abstract void process(HttpRequest request, PacketBuffer buff);
    
    public abstract String uri();

    public String getContentType() {
        return "text/plain; charset=UTF-8";
    }
}
