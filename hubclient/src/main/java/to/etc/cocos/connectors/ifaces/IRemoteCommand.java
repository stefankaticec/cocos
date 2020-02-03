package to.etc.cocos.connectors.ifaces;

import io.reactivex.Observable;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 25-6-19.
 */
@NonNullByDefault
public interface IRemoteCommand {
	String getCommandId();

	void addListener(IRemoteCommandListener listener);

	void removeListener(IRemoteCommandListener listener);

	IRemoteClient getClient();

	@Nullable
	String getCommandKey();

	String getDescription();

	<T> void setAttribute(@NonNull T object);

	@Nullable
	<T> T getAttribute(Class<T> clz);

	Observable<ServerCommandEventBase> observeEvents();

	IRemoteCommand cancel(@NonNull String cancelReason) throws Exception;
}
