package to.etc.cocos.connectors;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 24-6-19.
 */
public class SocketEofException extends RuntimeException {
	public SocketEofException(String message) {
		super(message);
	}
}
