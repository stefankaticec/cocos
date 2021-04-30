package to.etc.hubserver.protocol;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 13-1-19.
 */
public enum ErrorCode {
	noDataExpected("No data expected in this state"),
	invalidSignature("Invalid server signature"),

	@Deprecated
	clusterNotFound("Cluster {0} not found"),
	authenticationFailure("Authentication failure"),

	@Deprecated
	targetNotFound("Target {0} not found"),

	@Deprecated
	clusterHasNoServers("The cluster {0} has no connected servers"),

	@Deprecated
	serverDisconnected("This server is no longer active"),

	@Deprecated
	clientNotFound("Client {0} not found in the client table"),

	/**
	 * This replaces all the above deprecated error codes that also all indicate some party is not reachable.
	 */
	partyNotFound("Party {0} is not connected, try again later"),

	commandNotFound("The command {0} is unknown"),
	commandException("Unexpected exception: {0}"),
	commandSendError("The command could not be sent"),
	peerRestarted("The peer daemon has restarted, the command has been cancelled because of that"),
	cancelTimeout("The command was cancelled, but no response was received from the cancelled action"),
	packetTooLarge("Packet body too large")
	;

	private String m_text;

	ErrorCode(String text) {
		m_text = text;
	}

	public String getText() {
		return m_text;
	}
}
