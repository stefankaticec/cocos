package to.etc.cocos.connectors.server;

import io.reactivex.subjects.PublishSubject;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.connectors.ifaces.EventCommandBase;
import to.etc.cocos.connectors.ifaces.EventCommandError;
import to.etc.cocos.connectors.ifaces.EventCommandFinished;
import to.etc.cocos.connectors.ifaces.IRemoteCommand;
import to.etc.cocos.connectors.ifaces.IRemoteCommandListener;
import to.etc.cocos.connectors.ifaces.RemoteCommandStatus;
import to.etc.cocos.messages.Hubcore.CommandError;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 22-07-19.
 */
@NonNullByDefault
final public class RemoteCommand implements IRemoteCommand {
	private final String m_commandId;

	private final RemoteClient m_client;

	final private long m_commandTimeout;

	@Nullable
	private final String m_commandKey;

	private final String m_description;

	private long m_finishedAt;

	private RemoteCommandStatus m_status = RemoteCommandStatus.SCHEDULED;

	private final Map<String, Object> m_attributeMap = new HashMap<>();

	private CopyOnWriteArrayList<IRemoteCommandListener> m_listeners = new CopyOnWriteArrayList<>();

	@Nullable
	private CommandError m_commandError;

	private final PublishSubject<EventCommandBase> m_eventPublisher = PublishSubject.<EventCommandBase>create();

	public RemoteCommand(RemoteClient client, String commandId, long commandTimeout, @Nullable String commandKey, String description) {
		m_commandId = commandId;
		m_client = client;
		m_commandTimeout = commandTimeout;
		m_commandKey = commandKey;
		m_description = description;
		addListener(new IRemoteCommandListener() {
			@Override
			public void errorEvent(EventCommandError errorEvent) throws Exception {
				m_eventPublisher.onNext(errorEvent);
			}

			@Override
			public void completedEvent(EventCommandFinished ev) throws Exception {
				m_eventPublisher.onNext(ev);
			}
		});
	}

	@Override
	public void addListener(IRemoteCommandListener l) {
		m_listeners.add(l);
	}

	@Override
	public void removeListener(IRemoteCommandListener l) {
		m_listeners.remove(l);
	}

	public List<IRemoteCommandListener> getListeners() {
		return m_listeners;
	}

	@Override
	public String getCommandId() {
		return m_commandId;
	}

	@Override
	public RemoteClient getClient() {
		return m_client;
	}

	public long getCommandTimeout() {
		return m_commandTimeout;
	}

	@Override
	@Nullable
	public String getCommandKey() {
		return m_commandKey;
	}

	@Override
	public String getDescription() {
		return m_description;
	}

	@Override
	public <T> void putAttribute(@NonNull T object) {
		m_attributeMap.put(object.getClass().getName(), object);
	}

	@Override
	@Nullable
	public <T> T getAttribute(Class<T> clz) {
		return (T) m_attributeMap.get(clz.getName());
	}

	public RemoteCommandStatus getStatus() {
		return m_status;
	}

	public void setStatus(RemoteCommandStatus status) {
		m_status = status;
	}

	public void setError(CommandError commandError) {
		m_commandError = commandError;
	}

	public long getFinishedAt() {
		return m_finishedAt;
	}

	public void setFinishedAt(long finishedAt) {
		m_finishedAt = finishedAt;
	}

	@Override
	public PublishSubject<EventCommandBase> getEventPublisher() {
		return m_eventPublisher;
	}
}
