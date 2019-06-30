package to.etc.cocos.hub;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 23-1-19.
 */
final public class UnreachableOrganisationException extends FatalHubException {
	public UnreachableOrganisationException(String message) {
		super(ErrorCode.targetNotFound, message);
	}
}
