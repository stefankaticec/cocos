package to.etc.cocos.connectors.ifaces;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 19-09-19.
 */
public enum RemoteCommandStatus {
	SCHEDULED(true),
	RUNNING(true),
	FAILED(false),
	FINISHED(false),
	CANCELED(false),
	UNKNOWN(false);

	private boolean m_cancellable;

	RemoteCommandStatus(boolean cancellable) {
		m_cancellable = cancellable;
	}

	public boolean isCancellable() {
		return m_cancellable;
	}
}
