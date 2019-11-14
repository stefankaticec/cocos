package to.etc.cocos.tests;

import to.etc.cocos.connectors.common.JsonPacket;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 22-07-19.
 */
public class UnknownCommandTestPacket extends JsonPacket {
	private String m_parameters;

	public String getParameters() {
		return m_parameters;
	}

	public void setParameters(String parameters) {
		m_parameters = parameters;
	}
}
