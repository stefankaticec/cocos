package to.etc.cocos.connectors;

import to.etc.puzzler.daemon.rpc.messages.Hubcore;
import to.etc.puzzler.daemon.rpc.messages.Hubcore.Envelope;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 30-6-19.
 */
public class CommandContext {
	private final HubConnector m_connector;

	private final Hubcore.Envelope m_envelope;

	public CommandContext(HubConnector connector, Envelope envelope) {
		m_connector = connector;
		m_envelope = envelope;

		Hubcore.Envelope.newBuilder()
			.setChallenge()
	}




}
