package netty.plugin;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.logging.LoggingHandler;
import netty.message.LoginRequestMessage;
import netty.protocal.MessageCodec;

public class TestMessageCodec {

    public static void main(String[] args) throws Exception {
        EmbeddedChannel embeddedChannel = new EmbeddedChannel(
                new LengthFieldBasedFrameDecoder(1024, 12, 4, 0, 0),
                new LoggingHandler(),
                new MessageCodec()
        );

        // encode
        LoginRequestMessage message = new LoginRequestMessage("zhangsan", "123", "张三");
        embeddedChannel.writeOutbound(message);

        // decode
        ByteBuf buf = ByteBufAllocator.DEFAULT.buffer();
        new MessageCodec().encodeTest(null, message, buf);

        embeddedChannel.writeInbound(buf);
    }
}