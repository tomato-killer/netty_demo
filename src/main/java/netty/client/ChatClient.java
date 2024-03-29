package netty.client;


import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;

import netty.message.*;
import netty.protocol.MessageCodecSharable;
import netty.protocol.ProtocolFrameDecoder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;


public class ChatClient {

    private static final Logger log = LoggerFactory.getLogger(ChatClient.class);

    public static void main(String[] args) {
        NioEventLoopGroup group = new NioEventLoopGroup();
        LoggingHandler LOGGING_HANDLER = new LoggingHandler(LogLevel.DEBUG);
        MessageCodecSharable MESSAGE_CODEC = new MessageCodecSharable();
        CountDownLatch WAIT_FOR_LOGIN = new CountDownLatch(1);
        AtomicBoolean LOGIN = new AtomicBoolean(false);
        AtomicBoolean EXIT = new AtomicBoolean(true);
        Scanner scanner = new Scanner(System.in);
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.channel(NioSocketChannel.class);
                    // ServerSocketChannel.option 给 ServerSocketChannel 配置参数
                    // ChannelOption.CONNECT_TIMEOUT_MILLIS 用在客户端连接时，如果在制定毫秒内无法连接会抛出异常
            bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000);
                    // ServerSocketChannel.childOption 给 SocketChannel 配置参数
            bootstrap.group(group);
            bootstrap.handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) throws Exception {
                    ch.pipeline().addLast(new ProtocolFrameDecoder());
                    ch.pipeline().addLast(LOGGING_HANDLER);
                    ch.pipeline().addLast(MESSAGE_CODEC);
                    // 如果 3S 内没有向服务器写数据， 会触发一个 IdleState#WRITER_IDLE 事件
                    ch.pipeline().addLast(new IdleStateHandler(0, 3, 0));
                    ch.pipeline().addLast(new ChannelDuplexHandler(){
                        @Override
                        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                            IdleStateEvent event = (IdleStateEvent) evt;
                            // 触发了读空闲事件
                            if (event.state() == IdleState.WRITER_IDLE){
                                log.debug("已经 3S 没有写数据了，发送心跳给服务器");
                                ctx.writeAndFlush(new PingMessage());
                            }
                        }
                    });
                    ch.pipeline().addLast("client handler", new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelActive(ChannelHandlerContext ctx) throws Exception {

                            new Thread(() -> {
                                System.out.println("请输入用户名： ");
                                String username = scanner.nextLine();
                                System.out.println("请输入密码： ");
                                String password = scanner.nextLine();
                                LoginRequestMessage message = new LoginRequestMessage(username, password);
                                ctx.writeAndFlush(message);

                                try {
                                    WAIT_FOR_LOGIN.await();
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }

                                if (!LOGIN.get()) {
                                    ctx.channel().close();
                                    return;
                                }

                                while (EXIT.get()) {
                                    System.out.println("==================================");
                                    System.out.println("send [username] [content]");
                                    System.out.println("gsend [group name] [content]");
                                    System.out.println("gcreate [group name] [m1,m2,m3...]");
                                    System.out.println("gmembers [group name]");
                                    System.out.println("gjoin [group name]");
                                    System.out.println("gquit [group name]");
                                    System.out.println("quit");
                                    System.out.println("==================================");
                                    String command = scanner.nextLine();
                                    String[] s = command.split(" ");
                                    switch (s[0]) {
                                        case "send":
                                            ctx.writeAndFlush(new ChatRequestMessage(username, s[1], s[2]));
                                            break;
                                        case "gsend":
                                            ctx.writeAndFlush(new GroupChatRequestMessage(username, s[1], s[2]));
                                            break;
                                        case "gcreate":
                                            Set<String> members = new HashSet<>(Arrays.asList(s[2].split(",")));
                                            members.add(username);
                                            ctx.writeAndFlush(new GroupCreateRequestMessage(s[1], members));
                                            break;
                                        case "gmembers":
                                            ctx.writeAndFlush(new GroupMembersRequestMessage(s[1]));
                                            break;
                                        case "gjoin":
                                            ctx.writeAndFlush(new GroupJoinRequestMessage(username, s[1]));
                                            break;
                                        case "gquit":
                                            ctx.writeAndFlush(new GroupQuitRequestMessage(username, s[1]));
                                            break;
                                        case "quit":
                                            EXIT.set(false);
                                            ctx.channel().close();
                                            break;
                                    }
                                }
                            }, "system in").start();
                        }


                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                            log.debug("msg {}", msg);
                            if (msg instanceof LoginResponseMessage) {
                                LoginResponseMessage response = (LoginResponseMessage) msg;
                                LOGIN.set(response.isSuccess() ? true : false);
                            }
                            WAIT_FOR_LOGIN.countDown();
                        }
                    });

                }
            });
            ChannelFuture future = bootstrap.connect("localhost", 8080);
            Channel channel = future.sync().channel();
            channel.closeFuture().sync();
        } catch (Exception e) {
            log.error("client error", e);
        } finally {
            group.shutdownGracefully();
        }
    }
}
