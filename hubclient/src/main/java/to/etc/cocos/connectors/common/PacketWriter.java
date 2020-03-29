package to.etc.cocos.connectors.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.messages.Hubcore.Envelope;
import to.etc.hubserver.protocol.CommandNames;
import to.etc.util.ByteBufferOutputStream;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Sends packets in the format required between repeater and clients.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 29-6-19.
 */
final public class PacketWriter {
	private OutputStream m_os;

	private final HubConnectorBase m_connector;

	final private ObjectMapper m_mapper;

	public PacketWriter(HubConnectorBase connector, ObjectMapper mapper) {
		m_connector = connector;
		m_mapper = mapper;
	}

	public void setOs(OutputStream os) {
		m_os = os;
	}

	//public void send(Envelope envelope, Object jsonBody) throws Exception {
	//	m_connector.log("sending " + envelope.getPayloadCase());
	//	sendEnvelope(envelope);
	//	if(null == jsonBody) {
	//		writeInt(0);								// Send an empty body.
	//	} else {
	//		writeJsonObject(jsonBody);
	//	}
	//}
	//
	//public void sendString(Envelope envelope, String text) throws Exception {
	//	m_connector.log("sending string " + envelope.getPayloadCase());
	//	sendEnvelope(envelope);
	//	if(null == text) {
	//		writeInt(0);								// Send an empty body.
	//	} else {
	//		byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
	//		writeInt(bytes.length);
	//		m_os.write(bytes);
	//	}
	//}

	/**
	 * Send the packet header, then marshal the packet envelope.
	 */
	void sendEnvelope(Envelope envelope) throws IOException {
		m_os.write(CommandNames.HEADER);
		byte[] bytes = envelope.toByteArray();
		writeInt(bytes.length);
		m_os.write(bytes);
	}

	private void writeJsonObject(Object jsonObject) throws IOException {
		ByteBufferOutputStream bbos = new ByteBufferOutputStream();
		m_mapper.writeValue(bbos, jsonObject);
		bbos.close();
		writeInt(bbos.getSize());
		for(byte[] buffer : bbos.getBuffers()) {
			m_os.write(buffer);
		}
	}

	//public void exception(@NonNull Envelope envelope, @NonNull Throwable exception) throws Exception {
	//}
	//
	private void writeInt(int len) throws IOException {
		m_os.write((len >> 24) & 0xff);
		m_os.write((len >> 16) & 0xff);
		m_os.write((len >> 8) & 0xff);
		m_os.write(len & 0xff);
	}

	public void sendBody(@Nullable IBodyTransmitter bodyTransmitter) throws Exception{
		if(null == bodyTransmitter) {
			writeInt(0);								// Send an empty body.
		} else {
			bodyTransmitter.sendBody(this);
		}
	}
}
