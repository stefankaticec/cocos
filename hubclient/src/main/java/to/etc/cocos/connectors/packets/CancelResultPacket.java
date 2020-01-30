package to.etc.cocos.connectors.packets;

import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.connectors.common.JsonPacket;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 30-01-20.
 */
public class CancelResultPacket extends JsonPacket {
	private boolean m_successful;

	@Nullable
	private String m_failureReason;

	public CancelResultPacket() {
		m_successful = true;
	}

	public CancelResultPacket(@Nullable String failureReason) {
		m_failureReason = failureReason;
		m_successful = false;
	}

	public boolean isSuccessful() {
		return m_successful;
	}

	public void setSuccessful(boolean successful) {
		m_successful = successful;
	}

	@Nullable
	public String getFailureReason() {
		return m_failureReason;
	}

	public void setFailureReason(@Nullable String failureReason) {
		m_failureReason = failureReason;
	}
}
