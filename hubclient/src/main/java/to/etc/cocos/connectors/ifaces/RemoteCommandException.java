package to.etc.cocos.connectors.ifaces;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 24-01-20.
 */
final public class RemoteCommandException extends RemoteClientException {
	private final String m_code;

	private final String m_message;

	private final String m_details;

	public RemoteCommandException(EvCommandError packet) {
		super(packet.getMessage() + " (" + packet.getMessage());
		m_code = packet.getCode();
		m_message = packet.getMessage();
		m_details = packet.getDetails();
	}

	public String getErrorMessage() {
		return m_message;
	}

	public String getCode() {
		return m_code;
	}

	public String getDetails() {
		return m_details;
	}
}
