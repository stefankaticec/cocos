package to.etc.cocos.connectors.server;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 03-07-19.
 */
public interface IClientAuthenticator {
	boolean clientAuthenticated(String clientId, byte[] challenge, byte[] challengeResponse, String clientVersion) throws Exception;
}
