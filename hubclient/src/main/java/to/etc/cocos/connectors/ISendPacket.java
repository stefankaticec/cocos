package to.etc.cocos.connectors;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 25-6-19.
 */
public interface ISendPacket {
	void send(PacketWriter os) throws Exception;
}
