package to.etc.cocos.connectors.ifaces;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.connectors.common.JsonPacket;
import to.etc.cocos.connectors.packets.CancelReasonCode;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 25-6-19.
 */
@NonNullByDefault
public interface IRemoteClient {
	String getClientID();

	@Nullable
	<T extends JsonPacket> T getInventory(Class<T> clz);

	IRemoteCommand sendJsonCommand(String uniqueCommandId, JsonPacket packet, Duration commandTimeout, @Nullable String commandKey, String description, @Nullable IRemoteCommandListener l) throws Exception;

	IRemoteCommand sendCancel(String commandId, CancelReasonCode code, String reason) throws Exception;

	boolean isConnected();

	LocalDateTime getLastPresent();
}
