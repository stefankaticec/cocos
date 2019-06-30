package to.etc.hubserver;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 23-1-19.
 */
final public class UnknownClusterException extends FatalHubException {
	public UnknownClusterException(String what) {
		super(ErrorCode.clusterNotFound, what);
	}
}
