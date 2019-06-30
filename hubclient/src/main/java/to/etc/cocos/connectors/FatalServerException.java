package to.etc.cocos.connectors;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 26-1-19.
 */
public class FatalServerException extends RuntimeException {
	public FatalServerException(String message) {
		super(message);
	}
}
