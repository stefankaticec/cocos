package to.etc.cocos.connectors.ifaces;

import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.connectors.common.JsonPacket;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 17-5-19.
 */
final public class JsonCommandEvent {
	final private boolean m_finished;

	@Nullable
	final private Exception m_exception;

	@Nullable
	final private JsonPacket m_packet;

	public JsonCommandEvent(boolean finished, @Nullable Exception exception, @Nullable JsonPacket packet) {
		m_finished = finished;
		m_exception = exception;
		m_packet = packet;
	}

	public boolean isFinished() {
		return m_finished;
	}

	@Nullable public Exception getException() {
		return m_exception;
	}

	@Nullable public JsonPacket getPacket() {
		return m_packet;
	}

	@Override public String toString() {
		return "JsonCommandEvent: finished=" + m_finished + ", exception=" + m_exception + ", packet=" + m_packet;
	}
}
