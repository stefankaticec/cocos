package to.etc.cocos.connectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.hubserver.protocol.CommandNames;
import to.etc.hubserver.protocol.HubException;
import to.etc.util.ByteBufferInputStream;
import to.etc.util.ClassUtil;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 10-2-19.
 */
@NonNullByDefault
abstract public class AbstractResponder implements IHubResponder {
	static private final byte[] NULLBODY = new byte[0];

	@Override public void acceptPacket(CommandContext ctx, List<byte[]> data) throws Exception {
		Object body = decodeBody(ctx.getConnector(), ctx.getSourceEnvelope().getDataFormat(), data);
		Method m = findHandlerMethod(ctx.getSourceEnvelope().getCommand(), body);
		if(null == m) {
			throw new ProtocolViolationException("No handler for packet command " + ctx.getSourceEnvelope().getCommand() + " with body type " + bodyType(body));
		}

		if(m.getAnnotation(Synchronous.class) != null) {
			invokeCall(ctx, body, m);
		} else {
			invokeCallAsync(ctx, body, m);
		}
	}

	private String bodyType(@Nullable Object body) {
		return null == body ? "(void)" : body.getClass().getName();
	}

	private void invokeCallAsync(CommandContext ctx, @Nullable Object body, Method m) {
		ctx.getConnector().getExecutor().execute(() -> {
			try {
				invokeCall(ctx, body, m);
			} catch(Exception x) {
				ctx.log("Failed to execute " + m.getName() + ": " + x);
				try {
					handleException(ctx, x);
				} catch(Exception xx) {
					ctx.log("Could not return protocol error: " + xx);
				}
			}
		});
	}

	private void invokeCall(CommandContext ctx, @Nullable Object body, Method m) throws Exception {
		try {
			if(null == body)
				m.invoke(this, ctx);
			else
				m.invoke(this, ctx, body);
		} catch(InvocationTargetException itx) {
			handleException(ctx, itx);
		}
	}

	private void handleException(CommandContext cc, Throwable t) throws Exception {
		while(t instanceof InvocationTargetException) {
			t = ((InvocationTargetException)t).getTargetException();
		}

		if(t instanceof HubException) {
			cc.respondHubException((HubException) t);
		}  if(t instanceof RuntimeException) {
			throw (RuntimeException) t;
		} else if(t instanceof Error) {
			throw (Error) t;
		} else if(t instanceof Exception) {
			throw (Exception) t;
		} else {
			throw new RuntimeException(t);
		}
	}

	@Nullable
	private Object decodeBody(HubConnector connector,String bodyType, List<byte[]> data) throws IOException {
		switch(bodyType) {
			case CommandNames.BODY_BYTES:
				return data;

			case "":
				return null;
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

	@Nullable
	private Method findHandlerMethod(String command, @Nullable Object body) {
		String name = "handle" + command;
		try {
			return body == null
				? getClass().getMethod(name, CommandContext.class)
				: getClass().getMethod(name, CommandContext.class, body.getClass());
		} catch(Exception x) {
		}
		if(null == body)
			return null;

		//slow method to get overrides
		for(Method method : getClass().getMethods()) {
			if(method.getName().equals(name)) {
				if(method.getReturnType() == Void.class && method.getParameterCount() == 2) {
					if(method.getParameterTypes()[0] == CommandContext.class && method.getParameterTypes()[1].isAssignableFrom(body.getClass())) {
						return method;
					}
				}
			}
		}
		return null;
	}
}
