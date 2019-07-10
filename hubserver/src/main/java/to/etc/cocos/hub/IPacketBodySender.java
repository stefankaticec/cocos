package to.etc.cocos.hub;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 10-07-19.
 */
public interface IPacketBodySender {
	void sendBody(BufferWriter handler) throws Exception;
}
