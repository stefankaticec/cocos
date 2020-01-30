package to.etc.cocos.connectors.client;

import org.eclipse.jdt.annotation.NonNullByDefault;
import to.etc.cocos.connectors.common.CommandContext;
import to.etc.cocos.connectors.common.JsonPacket;
import to.etc.cocos.connectors.packets.CancelPacket;
import to.etc.cocos.connectors.packets.CancelResultPacket;

/**
 * This command tries to cancel some other running command.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 30-01-20.
 */
@NonNullByDefault
final class CancelCommand implements IJsonCommandHandler<CancelPacket> {
	final private HubClient m_daemon;

	public CancelCommand(HubClient daemon) {
		m_daemon = daemon;
	}

	@Override
	public JsonPacket execute(CommandContext ctx, CancelPacket packet) throws Exception {
		try {
			m_daemon.cancelCommand(packet.getCommandId(), packet.getCancelReason());
			return new CancelResultPacket();
		} catch(Exception x) {
			return new CancelResultPacket(x.toString());
		}
	}
}
