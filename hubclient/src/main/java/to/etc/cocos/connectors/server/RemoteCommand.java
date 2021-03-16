package to.etc.cocos.connectors.server;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.connectors.ifaces.EvCommandOutput;
import to.etc.cocos.connectors.ifaces.IRemoteCommand;
import to.etc.cocos.connectors.ifaces.IRemoteCommandListener;
import to.etc.cocos.connectors.ifaces.RemoteCommandStatus;
import to.etc.cocos.connectors.packets.CancelReasonCode;
import to.etc.cocos.messages.Hubcore.CommandError;
import to.etc.function.ConsumerEx;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 22-07-19.
 */
@NonNullByDefault
final public class RemoteCommand implements IRemoteCommand {
	private final String m_commandId;

	private final RemoteClient m_client;

	final private Duration m_commandTimeout;

	@Nullable
	private final String m_commandKey;

	private final String m_description;

	private long m_startedAt;

	private long m_finishedAt;

	private RemoteCommandStatus m_status = RemoteCommandStatus.SCHEDULED;

	private final Map<String, Object> m_attributeMap = new HashMap<>();

	private CopyOnWriteArrayList<IRemoteCommandListener> m_listeners = new CopyOnWriteArrayList<>();

	@Nullable
	private CommandError m_commandError;

	//private final PublishSubject<ServerCommandEventBase> m_eventPublisher = PublishSubject.<ServerCommandEventBase>create();

	/** Decodes the output stream bytes to a string */
	@Nullable
	private CharsetDecoder m_decoder;

	final private CharBuffer m_outBuffer = CharBuffer.allocate(8192*4);

	final private ByteBuffer m_inBuffer = ByteBuffer.allocate(8192);

	private long m_cancelTime;

	enum RemoteCommandType {
		Command, Cancel
	}

	private final RemoteCommandType m_commandType;

	static private final Pattern PROGRESS_PATTERN = Pattern.compile("\\[PROGRESS\\] (\\d[\\d]?)% (.*)\n");

	public RemoteCommand(RemoteClient client, String commandId, Duration commandTimeout, @Nullable String commandKey, String description, RemoteCommandType type) {
		m_commandId = commandId;
		m_client = client;
		m_commandTimeout = commandTimeout;
		m_commandKey = commandKey;
		m_description = description;
		m_startedAt = System.currentTimeMillis();
		m_commandType = type;
		//addListener(new IRemoteCommandListener() {
		//	@Override
		//	public void errorEvent(EvCommandError errorEvent) throws Exception {
		//		//m_eventPublisher.onNext(errorEvent);
		//		//m_eventPublisher.onComplete();
		//	}
		//
		//	@Override
		//	public void completedEvent(EvCommandFinished ev) throws Exception {
		//		//m_eventPublisher.onNext(ev);
		//		//m_eventPublisher.onComplete();
		//	}
		//
		//	@Override
		//	public void stdoutEvent(EvCommandOutput ev) throws Exception {
		//		//m_eventPublisher.onNext(ev);
		//	}
		//});
	}

	public RemoteCommandType getCommandType() {
		return m_commandType;
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

	public void callCommandListeners(ConsumerEx<IRemoteCommandListener> l) {
		for(IRemoteCommandListener listener : getListeners()) {
			try {
				l.accept(listener);
			} catch(Exception x) {
				System.err.println("Command " + this + " commandListener failed: " + x);
				x.printStackTrace();
			}
		}
		for(IRemoteCommandListener listener : getClient().getListeners()) {
			try {
				l.accept(listener);
			} catch(Exception x) {
				System.err.println("Command " + this + " clientListener failed: " + x);
				x.printStackTrace();
			}
		}
	}

	@Override
	public String getCommandId() {
		return m_commandId;
	}

	@Override
	public RemoteClient getClient() {
		return m_client;
	}

	public Duration getCommandTimeout() {
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
	public <T> void setAttribute(@NonNull T object) {
		m_attributeMap.put(object.getClass().getName(), object);
	}

	@Override
	@Nullable
	public <T> T getAttribute(Class<T> clz) {
		return (T) m_attributeMap.get(clz.getName());
	}

	@Override
	public synchronized RemoteCommandStatus getStatus() {
		return m_status;
	}

	public synchronized void setStatus(RemoteCommandStatus status) {
		m_status = status;
		if(status == RemoteCommandStatus.CANCELED)
			m_cancelTime = System.currentTimeMillis();
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

	public long getStartedAt() {
		return m_startedAt;
	}

	public synchronized void appendOutput(List<byte[]> data, String code) {
		CharsetDecoder decoder = m_decoder;
		if(null == decoder) {
			Charset charset = Charset.forName("utf-8");
			decoder = m_decoder = charset.newDecoder();
		}

		StringBuilder sb = new StringBuilder();
		for(byte[] datum : data) {
			if(datum.length > 0)
				pushData(sb, datum, decoder);
		}

		if(sb.length() > 0) {
			EvCommandOutput eco = new EvCommandOutput(this, code, sb.toString());
			callCommandListeners(l -> l.stdoutEvent(eco));
			progressScanner(sb);
		}
	}

	private void progressScanner(StringBuilder sb) {
		Matcher m = PROGRESS_PATTERN.matcher(sb);
		int lastProgress = -1;
		String lastMessage = null;
		while(m.find()) {
			lastProgress = Integer.parseInt(m.group(1));
			lastMessage = m.group(2);
		}

		if(lastProgress != -1) {
			int javaSucksLastProgress = lastProgress;				// You must be a real idiot to think this is a good idea.
			String javaSucksLastMessage = lastMessage == null ? "" : lastMessage;
			callCommandListeners(l -> l.progressEvent(this, javaSucksLastProgress, javaSucksLastMessage));
		}
	}


	private void pushData(StringBuilder sb, byte[] buffer, CharsetDecoder decoder) {
		int off = 0;
		while(off < buffer.length) {
			int todoBytes = m_inBuffer.capacity();
			int len = buffer.length - off;
			if(todoBytes > len) {
				todoBytes = len;
			}

			//-- Put in buffer, then advance
			m_inBuffer.put(buffer, off, todoBytes);
			off += todoBytes;

			//-- Convert to the correct encoding
			m_inBuffer.flip();
			decoder.decode(m_inBuffer, m_outBuffer, false);
			m_inBuffer.clear();
			m_outBuffer.flip();
			sb.append(m_outBuffer);
			m_outBuffer.clear();
		}
	}

	/**
	 * Send a CANCEL request for this command.
	 */
	@Override
	public void cancel(@NonNull CancelReasonCode code, @NonNull String cancelReason) throws Exception {
		if(!getStatus().isCancellable()) {
			throw new IllegalStateException("Cant cancel a command with status "+ getStatus());
		}
		setStatus(RemoteCommandStatus.CANCELED);
		if(getCommandType() == RemoteCommandType.Cancel)						// Do not cancel cancels.
			return;
		m_client.sendCancel(getCommandId(), code, cancelReason);
	}

	@Override
	public String toString() {
		return m_commandId + ":" + m_description;
	}

	boolean hasTimedOut() {
		return m_startedAt + getCommandTimeout().toMillis() < System.currentTimeMillis();
	}

	public synchronized long getCancelTime() {
		return m_cancelTime;
	}



	static public void main(String[] args) throws Exception {
		String s = "\nThe hills are alive\n"
			+ "[PROGRESS] 15% This is a message\n"
			+ "But that is all, folks\n"
			+"[PROGRESS] 16% Perhaps not, though\n"
			;

		Matcher m = PROGRESS_PATTERN.matcher(s);
		while(m.find()) {
			System.out.println("found: progress=" + m.group(1) + ", message=" + m.group(2) );
		}
	}
}
