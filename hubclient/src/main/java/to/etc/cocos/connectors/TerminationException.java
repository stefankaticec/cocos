package to.etc.cocos.connectors;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 12-1-19.
 */
public class TerminationException extends RuntimeException {
	public TerminationException() {
		super("Connection terminated");
	}
	public TerminationException(String message) {
		super(message);
	}
}
