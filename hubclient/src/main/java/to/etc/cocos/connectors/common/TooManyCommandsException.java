package to.etc.cocos.connectors.common;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 18-12-20.
 */
final public class TooManyCommandsException extends ConnectorException {
	public TooManyCommandsException(String message) {
		super(message);
	}
}
