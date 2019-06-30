package to.etc.cocos.connectors;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 10-1-19.
 */
public enum ConnectorState {
	/** The connector is stopped (initial state) */
	STOPPED,

	/** A start request is pending */
	STARTING,

	/** Connecting: a connect has been sent and we're awaiting its result */
	CONNECTING,

	/** Connection worked, now waiting for HELO to be received */
	WAIT_HELO,

	/** Connected and live */
	CONNECTED,

	/** Connecting has failed, and we're waiting until it is time to try again */
	RECONNECT_WAIT,

	/** We are disconnecting because of a transport error, and are waiting for it to finish */
	DISCONNECTING,

	/** We are busy with terminating the connector */
	TERMINATING,
}
