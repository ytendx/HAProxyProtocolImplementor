package de.ytendx.haproxy.tinyprotocol;

import io.netty.channel.*;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import com.google.common.collect.Lists;
/**
 * Represents a very tiny alternative to ProtocolLib.
 * <p>
 * It now supports intercepting packets during login and status ping (such as OUT_SERVER_PING)!
 * 
 * @author Kristian // Love ya <3 ~ytendx
 */
public class TinyProtocol {
	private static final AtomicInteger ID = new AtomicInteger(0);

	// Looking up ServerConnection
	private static final Class<Object> minecraftServerClass = Reflection.getUntypedClass("{nms}.MinecraftServer");
	private static final Class<Object> serverConnectionClass = Reflection.getUntypedClass("{nms}.ServerConnection");
	private static final Reflection.FieldAccessor<Object> getMinecraftServer = Reflection.getField("{obc}.CraftServer", minecraftServerClass, 0);
	private static final Reflection.FieldAccessor<Object> getServerConnection = Reflection.getField(minecraftServerClass, serverConnectionClass, 0);
	private static final Reflection.MethodInvoker getNetworkMarkers = Reflection.getTypedMethod(serverConnectionClass, null, List.class, serverConnectionClass);

	private static final Class<Object> networkManager = Reflection.getUntypedClass("{nms}.NetworkManager");
	private static final Reflection.FieldAccessor<SocketAddress> socketAddressFieldAccessor = Reflection.getField(networkManager, SocketAddress.class, 0);
	// List of network markers
	private List<Object> networkManagers;

	// Injected channel handlers
	private List<Channel> serverChannels = Lists.newArrayList();
	private ChannelInboundHandlerAdapter serverChannelHandler;
	private ChannelInitializer<Channel> beginInitProtocol;
	private ChannelInitializer<Channel> endInitProtocol;

	// Current handler name
	private String handlerName;

	protected volatile boolean closed;
	protected Plugin plugin;

	/**
	 * Construct a new instance of TinyProtocol, and start intercepting packets for all connected clients and future clients.
	 * <p>
	 * You can construct multiple instances per plugin.
	 * 
	 * @param plugin - the plugin.
	 */
	public TinyProtocol(final Plugin plugin) {
		this.plugin = plugin;

		// Compute handler name
		this.handlerName = getHandlerName();


		try {
			plugin.getLogger().info("Proceeding with the server channel injection...");
			registerChannelHandler();
		} catch (IllegalArgumentException ex) {
			// Damn you, late bind
			plugin.getLogger().info("Delaying server channel injection due to late bind.");

			new BukkitRunnable() {
				@Override
				public void run() {
					registerChannelHandler();
					plugin.getLogger().info("Late bind injection successful.");
				}
			}.runTask(plugin);
		}
	}

	private void createServerChannelHandler() {
		// Handle connected channels
		endInitProtocol = new ChannelInitializer<Channel>() {

			@Override
			protected void initChannel(Channel channel) throws Exception {
				try {
					// Adding the decoder to the pipeline
					channel.pipeline().addAfter("timeout", "haproxy-decoder", new HAProxyMessageDecoder());
					// Adding the proxy message handler to the pipeline too
					channel.pipeline().addAfter("haproxy-decoder", "haproxy-handler", HAPROXY_MESSAGE_HANDLER);
				} catch (Exception e) {
					plugin.getLogger().log(Level.SEVERE, "Cannot inject incomming channel " + channel, e);
				}
			}

		};

		// This is executed before Minecraft's channel handler
		beginInitProtocol = new ChannelInitializer<Channel>() {

			@Override
			protected void initChannel(Channel channel) throws Exception {
				channel.pipeline().addLast(endInitProtocol);
			}

		};

		serverChannelHandler = new ChannelInboundHandlerAdapter() {
	
			@Override
			public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
				Channel channel = (Channel) msg;

				// Prepare to initialize ths channel
				channel.pipeline().addFirst(beginInitProtocol);
				ctx.fireChannelRead(msg);
			}

		};
	}

	@SuppressWarnings("unchecked")
	private void registerChannelHandler() {
		Object mcServer = getMinecraftServer.get(Bukkit.getServer());
		Object serverConnection = getServerConnection.get(mcServer);
		boolean looking = true;

		// We need to synchronize against this list
		networkManagers = (List<Object>) getNetworkMarkers.invoke(null, serverConnection);
		createServerChannelHandler();

		// Find the correct list, or implicitly throw an exception
		for (int i = 0; looking; i++) {
			List<Object> list = Reflection.getField(serverConnection.getClass(), List.class, i).get(serverConnection);

			for (Object item : list) {
				if (!ChannelFuture.class.isInstance(item))
					break;

				// Channel future that contains the server connection
				Channel serverChannel = ((ChannelFuture) item).channel();

				serverChannels.add(serverChannel);
				serverChannel.pipeline().addFirst(serverChannelHandler);
				looking = false;

				plugin.getLogger().info("Found the server channel and added the handler. Injection successfully!");
			}
		}
	}

	/**
	 * Retrieve the name of the channel injector, default implementation is "tiny-" + plugin name + "-" + a unique ID.
	 * <p>
	 * Note that this method will only be invoked once. It is no longer necessary to override this to support multiple instances.
	 * 
	 * @return A unique channel handler name.
	 */
	protected String getHandlerName() {
		return "haproxy-implementor-tiny-" + plugin.getName() + "-" + ID.incrementAndGet();
	}

	private final HAProxyMessageHandler HAPROXY_MESSAGE_HANDLER = new HAProxyMessageHandler();

	@ChannelHandler.Sharable
	 public class HAProxyMessageHandler extends ChannelInboundHandlerAdapter {
		@Override
		public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
			if(!(msg instanceof HAProxyMessage)){
				super.channelRead(ctx, msg);
				return;
			}

			try {
				final HAProxyMessage message = (HAProxyMessage) msg;

				// Set the SocketAddress field of the NetworkManager ("packet_handler" handler) to the client address
				socketAddressFieldAccessor.set(ctx.channel().pipeline().get("packet_handler"), new InetSocketAddress(message.sourceAddress(), message.sourcePort()));
			}catch (Exception exception){
				// Closing the channel because we do not want people on the server with a proxy ip
				ctx.channel().close();

				// Logging for the lovely server admins :)
				plugin.getLogger().warning("Error: The server was unable to set the IP address from the 'HAProxyMessage'. Therefore we closed the channel.");
				exception.printStackTrace();
			}
		}
	};
}
