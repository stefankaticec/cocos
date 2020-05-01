package to.etc.cocos.connectors.ifaces;

import io.reactivex.rxjava3.core.Observable;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.connectors.common.JsonPacket;

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

	//@NonNull
	//InventoryPacket getInventory();

	IRemoteCommand sendJsonCommand(String uniqueCommandId, JsonPacket packet, Duration commandTimeout, @Nullable String commandKey, String description, @Nullable IRemoteCommandListener l) throws Exception;

	Observable<ServerCommandEventBase> getEventPublisher();

	boolean isConnected();

	LocalDateTime getLastPresent();
}
