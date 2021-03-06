package to.etc.hubserver.protocol;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 13-1-19.
 */
public enum ErrorCode {
	noDataExpected("No data expected in this state"),
	invalidSignature("Invalid server signature"),
	clusterNotFound("Cluster {0} not found"),
	authenticationFailure("Authentication failure"),
	targetNotFound("Target organisation {0} not found"),
	serverDisconnected("This server is no longer active"),
	clientNotFound("Client {0} not found in the client table"),
	commandNotFound("The command {0} is unknown"),
	commandException("Unexpected exception: {0}")
	;

	private String m_text;

	ErrorCode(String text) {
		m_text = text;
	}

	public String getText() {
		return m_text;
	}
}
