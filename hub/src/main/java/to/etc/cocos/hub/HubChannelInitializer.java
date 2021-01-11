package to.etc.cocos.hub;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleUserEventChannelHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.SslCloseCompletionEvent;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;

/**
 * Initializer for a new channel.
 */
class HubChannelInitializer extends ChannelInitializer<SocketChannel> {
	private static final String PING = "ping\r\n";

	private Hub m_server;

	private final SslContext m_sslContext;

	public HubChannelInitializer(Hub server, SslContext sslContext) {
		m_server = server;
		m_sslContext = sslContext;
	}

	@Override
	protected void initChannel(SocketChannel socketChannel) throws Exception {
		SslHandler sslHandler = m_sslContext.newHandler(socketChannel.alloc());
		sslHandler.engine().setEnabledProtocols(new String[]{"TLSv1.2"});
		ChannelPipeline pipeline = socketChannel.pipeline();

		pipeline.addLast("idleStateHandler", new IdleStateHandler(m_server.getPingInterval() * 2, m_server.getPingInterval(), 0));

		pipeline.addLast("ssl", sslHandler);

		CentralSocketHandler mainHandler = new CentralSocketHandler(m_server, socketChannel);

		ChannelPromise sslDonePromise = socketChannel.newPromise();
		pipeline.addLast("sslhslistener", new SslHandshakeCompletionHandler(sslDonePromise));
		sslDonePromise.addListener(future -> {
			if(future.isSuccess()) {
				mainHandler.sslHandshakeCompleted();
			} else {
				mainHandler.log("ssl handshake failed: " + future.cause());
			}
		});

		pipeline.addLast("timeoutHandler", new SimpleUserEventChannelHandler<Object>() {
			@Override
			protected void eventReceived(ChannelHandlerContext ctx, Object evt) throws Exception {
				if(evt instanceof IdleStateEvent) {
					IdleStateEvent ie = (IdleStateEvent) evt;
					if(ie.state() == IdleState.READER_IDLE) {
						ctx.close();
						m_server.log("Channel " + socketChannel.id() + " idle: disconnect requested ");
					} else if(ie.state() == IdleState.WRITER_IDLE) {
						mainHandler.sendPing();
					}
				} else if(evt instanceof SslCloseCompletionEvent) {
					mainHandler.remoteDisconnected(ctx, "SslCloseCompletionEvent received");
				} else
					if(m_server.getState() == HubState.STARTED)
						m_server.log("Event: " + evt.getClass() + " on channel " + socketChannel.id());
			}
		});
		pipeline.addLast(mainHandler);
	}

}
