package to.etc.cocos.hub.parties;

import to.etc.cocos.hub.ISystemContext;

/**
 * Represents a client (daemon).
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 13-1-19.
 */
final public class Client extends AbstractConnection {
	public Client(ISystemContext context, String id) {
		super(context, id);
	}
}
