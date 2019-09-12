package to.etc.cocos.hub;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 10-07-19.
 */
@NonNullByDefault
public interface IPacketBodySender {
	void sendBody(BufferWriter handler) throws Exception;
}
