package de.ytendx.haproxy;

import com.avaje.ebean.validation.NotNull;
import io.netty.channel.*;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import net.md_5.bungee.api.plugin.Plugin;


import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.InetSocketAddress;
import java.util.logging.Level;

public class HAProxyBungeeImplementor extends Plugin {
    @Override
    public void onEnable() {
        try {
            Field remoteAddressField = AbstractChannel.class.getDeclaredField("remoteAddress");
            remoteAddressField.setAccessible(true);

            Field serverChild = PipelineUtils.class.getField("SERVER_CHILD");
            serverChild.setAccessible(true);

            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(serverChild, serverChild.getModifiers() & ~Modifier.FINAL);

            ChannelInitializer<Channel> bungeeChannelInitializer = PipelineUtils.SERVER_CHILD;

            Method initChannelMethod = ChannelInitializer.class.getDeclaredMethod("initChannel", Channel.class);
            initChannelMethod.setAccessible(true);

            serverChild.set(null, new ChannelInitializer<>() {
                @Override
                protected void initChannel(Channel channel) throws Exception {
                    initChannelMethod.invoke(bungeeChannelInitializer, channel);
                    channel.pipeline().addFirst("haproxy-decoder", new HAProxyMessageDecoder());
                    channel.pipeline().addAfter("haproxy-decoder", "haproxy-handler", new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                            if (msg instanceof HAProxyMessage) {
                                HAProxyMessage message = (HAProxyMessage) msg;
                                remoteAddressField.set(channel, new InetSocketAddress(message.sourceAddress(), message.sourcePort()));
                            } else {
                                super.channelRead(ctx, msg);
                            }
                        }
                    });
                }
            });
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, e.getMessage(), e);
        }
    }
}
