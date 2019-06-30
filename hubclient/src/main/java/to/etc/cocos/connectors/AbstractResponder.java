package to.etc.cocos.connectors;

import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import to.etc.hubserver.protocol.CommandNames;
import to.etc.puzzler.daemon.rpc.messages.Hubcore;
import to.etc.util.ByteBufferInputStream;
import to.etc.util.ClassUtil;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 10-2-19.
 */
abstract public class AbstractResponder implements IHubResponder {
	@Override public void acceptPacket(HubConnector connector, Hubcore.Envelope envelope, List<byte[]> data) throws Exception {
		Method m = findHandlerMethod(envelope.getCommand());
		if(null == m) {
			throw new ProtocolViolationException("No handler for packet command " + envelope.getCommand());
		}
		Object body = decodeBody(connector, envelope.getDataFormat(), data);

		//Class<?> bufferClass = m.getParameterTypes()[2];
		//if(! MessageOrBuilder.class.isAssignableFrom(bufferClass))
		//	throw new ProtocolViolationException(m.getName() + " has an unknown packet buffer parameter " + bufferClass.getName());
		//Class<Message> mbc = (Class<Message>) bufferClass;
		//Message message = parseBuffer(mbc, );

		try {
			m.invoke(this, connector, packet, message);
		} catch(InvocationTargetException itx) {
			Throwable tx = itx.getTargetException();
			if(tx instanceof RuntimeException) {
				throw (RuntimeException) tx;
			} else if(tx instanceof Error) {
				throw (Error) tx;
			} else if(tx instanceof Exception) {
				throw (Exception) tx;
			} else {
				throw itx;
			}
		}
	}

	private Object decodeBody(HubConnector connector,String bodyType, List<byte[]> data) throws IOException {
		switch(bodyType) {
			case CommandNames.BODY_BYTES:
				return data;
		}

		int pos = bodyType.indexOf(':');
		if(pos == -1)
			throw new ProtocolViolationException("Unknown body type " + bodyType);
		String clzz = bodyType.substring(pos + 1);
		String sub = bodyType.substring(0, pos);

		switch(sub) {
			default:
				throw new ProtocolViolationException("Unknown body type " + bodyType);

			case CommandNames.BODY_JSON:
				Class<?> bodyClass = ClassUtil.loadClass(getClass().getClassLoader(), clzz);
				return connector.getMapper().readerFor(bodyClass).readValue(new ByteBufferInputStream(data.toArray(new byte[data.size()][])));
		}
	}

	//
	//private <T extends Message> T parseBuffer(Class<T> clz, List<byte[]> packet) throws Exception {
	//	T instance = (T) clz.getMethod("getDefaultInstance").invoke(null);
	//	return (T) instance.getParserForType().parseFrom(packet.getRemainingInput());
	//}

	private Method findHandlerMethod(String command) {
		String name = "handle" + command;
		for(Method method : getClass().getMethods()) {
			if(method.getName().equals(name)) {
				if(Modifier.isPublic(method.getModifiers())) {
					if(method.getParameterCount() == 3) {
						Class<?>[] pt = method.getParameterTypes();
						if(pt[0] == HubConnector.class && pt[1] == BytePacket.class) {
							return method;
						}
					}
				}
			}
		}
		return null;
	}
}
