package to.etc.cocos.hub.problems;

import to.etc.hubserver.protocol.ErrorCode;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 26-1-19.
 */
public class FatalHubException extends HubException {
	public FatalHubException(ErrorCode code, Object... parameters) {
		super(code, parameters);
	}
}
