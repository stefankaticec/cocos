package to.etc.cocos.connectors;

import java.util.List;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 13-1-19.
 */
public interface IHubResponder {
	//void onHelloPacket(HubConnector connector, Hubcore.Envelope envelope, List<byte[]> payload) throws Exception;
	//
	//void onAuth(HubConnector connector, Hubcore.Envelope envelope, List<byte[]> payload) throws Exception;
	//
	void acceptPacket(CommandContext ctx, List<byte[]> payload) throws Exception;
}
