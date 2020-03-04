package to.etc.cocos.connectors.common;

import org.eclipse.jdt.annotation.NonNull;

/**
 * This interface encapsulates a thing that can write a packet when it is time
 * to do so. When the transmitter is available then the method will be called
 * with the writer to use to generate the packet data. The instance of this
 * interface should itself have the data available that is to be marshalled into
 * a packet.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 25-6-19.
 */
@FunctionalInterface
public interface IPacketTransmitter {
	void send(@NonNull PacketWriter os) throws Exception;
}
