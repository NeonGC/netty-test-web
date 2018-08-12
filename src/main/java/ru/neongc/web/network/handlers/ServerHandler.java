package ru.neongc.web.network.handlers;

import static com.google.common.net.HttpHeaders.X_POWERED_BY;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpHeaders.Values.CLOSE;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import io.netty.handler.codec.http.*;
import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import java.util.HashMap;
import java.util.Map;
import ru.neongc.web.network.PacketBuffer;

public class ServerHandler extends SimpleChannelInboundHandler<Object> {

    private PacketBuffer buf;
    private final Map<String, UriHandlerBased> handlers = new HashMap<>();

    public ServerHandler() {
        if (handlers.isEmpty()) {
            Handler1 h1 = new Handler1();
            handlers.put(h1.uri(), h1);
            Handler2 h2 = new Handler2();
            handlers.put(h2.uri(), h2);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        buf = new PacketBuffer(Unpooled.buffer());
        if (msg instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) msg;
            // Handle a bad request.
            if (!request.getDecoderResult().isSuccess()) {
                System.out.println("Isn't success");
                sendHttpResponse(ctx, new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST));
                return;
            }
            // Allow only GET methods.
            if (request.getMethod() != HttpMethod.GET) {
                System.out.println("Method != get");
                sendHttpResponse(ctx, new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FORBIDDEN));
                return;
            }
            QueryStringDecoder queryStringDecoder = new QueryStringDecoder(request.uri());
            System.out.println("request:"+request.uri());
            String context = queryStringDecoder.path();
            System.out.println("context:"+context);
            if (context.equalsIgnoreCase("/favicon.ico")) {
                FullHttpResponse notFoundResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
                sendHttpResponse(ctx, notFoundResponse);
                return;
            }
            UriHandlerBased handler = handlers.get(context);
            if (handler != null) {
                handler.process(request, buf);
            }
            FullHttpResponse response = new DefaultFullHttpResponse(
                    HTTP_1_1,
                    ((HttpRequest) msg).getDecoderResult().isSuccess() ? HttpResponseStatus.OK : HttpResponseStatus.BAD_REQUEST
                    //, buf
            );
            response.headers().set(CONTENT_TYPE, handler != null ? handler.getContentType() : "text/plain; charset=UTF-8");
            HttpHeaders.setContentLength(response, buf.readableBytes());
            sendHttpResponse(ctx, response);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }

    private void sendHttpResponse(ChannelHandlerContext ctx, FullHttpResponse res) {
        // Generate an error page if response getStatus code is not OK (200).
        if (res.getStatus().code() != 200) {
            res.content().writeBytes(buf);
            buf.release();
            res.headers().set(CONTENT_LENGTH, res.content().readableBytes());
        } else {
            PacketBuffer buff = new PacketBuffer(Unpooled.buffer());
            buff.writeString("Error 200");
            res.content().writeBytes(buff);
            buff.release();
            res.headers().set(CONTENT_LENGTH, res.content().readableBytes());
        }

        res.headers().set(CONNECTION, CLOSE);
        res.headers().add(X_POWERED_BY, "NeonGC test site");

        // Send the response and close the connection.
        ctx.channel().writeAndFlush(res).addListener((channelFuture) -> {
            if (channelFuture.isSuccess()) {
                System.out.println("server write finished successfully");
            } else {
                System.out.println(("server write failed: " + channelFuture.cause()) + "\n" + channelFuture.cause().getStackTrace());
            }
        });

        //ctx.disconnect();
        //ctx.close();
    }
}
