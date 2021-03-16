package to.etc.cocos.connectors.ifaces;

import org.eclipse.jdt.annotation.NonNullByDefault;
import to.etc.cocos.connectors.server.RemoteCommand;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 15-5-19.
 */
@NonNullByDefault
public interface IRemoteCommandListener {
	default void errorEvent(EvCommandError errorEvent) throws Exception {}

	default void completedEvent(EvCommandFinished ev) throws Exception {}

	default void stdoutEvent(EvCommandOutput ev) throws Exception {}

	default void progressEvent(RemoteCommand command, int progressPercentage, String progressMessage) throws Exception {}
}
