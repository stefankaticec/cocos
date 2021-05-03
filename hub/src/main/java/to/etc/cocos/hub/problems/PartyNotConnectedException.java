package to.etc.cocos.hub.problems;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 30-04-21.
 */
final public class PartyNotConnectedException extends RuntimeException {
	public PartyNotConnectedException(String party) {
		super(party);
	}
}
