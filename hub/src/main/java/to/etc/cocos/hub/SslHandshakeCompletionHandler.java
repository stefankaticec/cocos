package to.etc.cocos.hub;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.util.concurrent.Promise;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 11-01-21.
 */
final public class SslHandshakeCompletionHandler extends ChannelInboundHandlerAdapter {
	private final Promise<Void> m_promise;

	public SslHandshakeCompletionHandler(Promise<Void> promise) {
		m_promise = promise;
	}

	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
		if(evt instanceof SslHandshakeCompletionEvent) {
			SslHandshakeCompletionEvent completion = (SslHandshakeCompletionEvent) evt;
			if(completion.isSuccess()) {
				ctx.pipeline().remove(this);
				m_promise.setSuccess(null);
			} else{
				m_promise.tryFailure(completion.cause());
			}
		} else{
			ctx.fireUserEventTriggered(evt);
		}
	}
}
