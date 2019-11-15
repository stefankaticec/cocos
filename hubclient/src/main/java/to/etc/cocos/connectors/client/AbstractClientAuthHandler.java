package to.etc.cocos.connectors.client;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 15-11-19.
 */
abstract public class AbstractClientAuthHandler implements IClientAuthenticationHandler {
	private final String m_clientPassword;

	public AbstractClientAuthHandler(String clientPassword) {
		m_clientPassword = clientPassword;
	}

	@Override
	public byte[] createAuthenticationResponse(String clientId, byte[] challenge) throws Exception {
		String ref = m_clientPassword + ":" + clientId;
		MessageDigest md = MessageDigest.getInstance("SHA-256");
		md.update(ref.getBytes(StandardCharsets.UTF_8));
		md.update(challenge);
		byte[] digest = md.digest();
		return digest;
	}
}
