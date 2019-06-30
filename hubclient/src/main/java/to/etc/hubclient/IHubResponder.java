package to.etc.hubclient;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 13-1-19.
 */
public interface IHubResponder {
	void onHelloPacket(HubConnector connector, BytePacket input) throws Exception;


	void onAuth(HubConnector connector, BytePacket input) throws Exception;

	void acceptPacket(HubConnector connector, BytePacket packet) throws Exception;
}
