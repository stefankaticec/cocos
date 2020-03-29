package to.etc.cocos.connectors.common;

import org.eclipse.jdt.annotation.NonNull;

/**
 * Transmits the packet's payload body just after the envelope has been sent.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 29-03-20.
 */
public interface IBodyTransmitter {
	void sendBody(@NonNull PacketWriter os) throws Exception;
}
