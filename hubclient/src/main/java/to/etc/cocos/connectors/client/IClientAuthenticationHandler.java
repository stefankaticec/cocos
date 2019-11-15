package to.etc.cocos.connectors.client;

import org.eclipse.jdt.annotation.NonNullByDefault;
import to.etc.cocos.connectors.common.JsonPacket;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 07-07-19.
 */
@NonNullByDefault
public interface IClientAuthenticationHandler {
	JsonPacket getInventory();

	byte[] createAuthenticationResponse(String clientId, byte[] challenge) throws Exception;
}
