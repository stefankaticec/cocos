package to.etc.cocos.connectors.common;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 29-03-20.
 */
public class PeerAbsentException extends ConnectorException {
	public PeerAbsentException(String peerId) {
		super("Peer " + peerId + " has not connected for a long time and is assumed unavailable");
	}
}
