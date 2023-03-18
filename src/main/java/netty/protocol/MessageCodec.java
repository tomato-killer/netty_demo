package netty.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;
import netty.config.Config;
import netty.message.Message;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

public class MessageCodec extends ByteToMessageCodec<Message> {
    @Override
    protected void encode(ChannelHandlerContext ctx, Message msg, ByteBuf out) throws Exception {
        // 写入魔数 4字节
        out.writeBytes(new byte[]{1,2,3,4});
        // 写入版本 1字节
        out.writeByte(1);
        // 写入字节的序列化方式 jdk 0 , json 1  1字节
        out.writeByte(Config.getSerializerAlgorithm().ordinal());
        // 字节的指令类型 1字节
        out.writeByte(msg.getMessageType());
        // 4个字节
        out.writeInt(msg.getSequenceId());

        // 一个字节对齐,凑齐16字节(2的N次方)，无意义（待扩展）
        out.writeByte(0xff);

        byte[] bytes = Config.getSerializerAlgorithm().serialize(msg);

        // 写入长度 4字节
        out.writeInt(bytes.length);
        out.writeBytes(bytes);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        int magicNum = in.readInt();
        byte version = in.readByte();
        byte serializerType = in.readByte();
        byte messageType = in.readByte();
        int sequenceId = in.readInt();
        in.readByte();
        int objLen = in.readInt();
        byte[] bytes = new byte[objLen];
        in.readBytes(bytes, 0, objLen);
        Serializer.Algorithm serializer = Serializer.Algorithm.values()[serializerType];
        Class<? extends Message> messageClass = Message.getMessageClass(messageType);
        Message msg = serializer.deserialize(messageClass, bytes);
//        Message msg = null;
//        if (serializerType == 0){
//            ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes));
//            msg = (Message) ois.readObject();
//            System.out.println(magicNum + ", " + version  + ", " + serializerType + ", "
//                    + messageType + ", " + sequenceId);
//        }
        out.add(msg);
    }

    public void encodeTest(ChannelHandlerContext ctx, Message msg, ByteBuf out) throws Exception {
        encode(ctx, msg, out);
    }
}