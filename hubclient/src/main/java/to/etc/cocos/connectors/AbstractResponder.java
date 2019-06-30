package to.etc.cocos.connectors;

import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 10-2-19.
 */
abstract public class AbstractResponder implements IHubResponder {
	@Override public void acceptPacket(HubConnector connector, BytePacket packet) throws Exception {
		Method m = findHandlerMethod(packet.getCommand());
		if(null == m) {
			throw new ProtocolViolationException("No handler for packet command " + packet.getCommand());
		}

		Class<?> bufferClass = m.getParameterTypes()[2];
		if(! MessageOrBuilder.class.isAssignableFrom(bufferClass))
			throw new ProtocolViolationException(m.getName() + " has an unknown packet buffer parameter " + bufferClass.getName());
		Class<Message> mbc = (Class<Message>) bufferClass;
		Message message = parseBuffer(mbc, packet);

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

	private <T extends Message> T parseBuffer(Class<T> clz, BytePacket packet) throws Exception {
		T instance = (T) clz.getMethod("getDefaultInstance").invoke(null);
		return (T) instance.getParserForType().parseFrom(packet.getRemainingInput());
	}

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
