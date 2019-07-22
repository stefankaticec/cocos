package to.etc.cocos.connectors;

import org.eclipse.jdt.annotation.NonNull;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 25-6-19.
 */
public interface ISendPacket {
	void send(@NonNull PacketWriter os) throws Exception;
}
