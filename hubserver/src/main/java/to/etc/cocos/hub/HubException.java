package to.etc.cocos.hub;

import java.text.MessageFormat;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 26-1-19.
 */
public class HubException extends RuntimeException {
	private final ErrorCode m_code;

	private Object[] m_parameters;

	public HubException(ErrorCode code, Object... parameters) {
		m_code = code;
		m_parameters = parameters;
	}

	@Override public String getMessage() {
		return MessageFormat.format(m_code.getText(), m_parameters);
	}

	public ErrorCode getCode() {
		return m_code;
	}

	public Object[] getParameters() {
		return m_parameters;
	}
}
