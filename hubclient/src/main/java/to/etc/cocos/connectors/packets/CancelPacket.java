package to.etc.cocos.connectors.packets;

import to.etc.cocos.connectors.common.JsonPacket;

/**
 * Cancel a command packet.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 30-01-20.
 */
public class CancelPacket extends JsonPacket {
	private String m_commandId;

	private String m_cancelReason;

	public String getCommandId() {
		return m_commandId;
	}

	public void setCommandId(String commandId) {
		m_commandId = commandId;
	}

	public String getCancelReason() {
		return m_cancelReason;
	}

	public void setCancelReason(String cancelReason) {
		m_cancelReason = cancelReason;
	}
}
