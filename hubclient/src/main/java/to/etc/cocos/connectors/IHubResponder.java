package to.etc.cocos.connectors;

import org.eclipse.jdt.annotation.NonNullByDefault;

import java.util.List;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 13-1-19.
 */
@NonNullByDefault
public interface IHubResponder {
	//void onHelloPacket(HubConnector connector, Hubcore.Envelope envelope, List<byte[]> payload) throws Exception;
	//
	//void onAuth(HubConnector connector, Hubcore.Envelope envelope, List<byte[]> payload) throws Exception;
	//
	void acceptPacket(CommandContext ctx, List<byte[]> payload) throws Exception;
}
