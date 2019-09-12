package to.etc.hubserver.protocol;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 13-1-19.
 */
final public class CommandNames {
	public final static byte[] HEADER = {(byte)0xde, (byte)0xca, (byte)0xf1, (byte)0x11};

	public final static int MAX_ENVELOPE_LENGTH = 1024*1024;
	public final static int MAX_DATA_LENGTH = 10*1024*1024;

	static public final String BODY_BYTES = "octet-stream";
	static public final String BODY_JSON = "json";

	static public final String getJsonDataFormat(Class<?> clz) {
		return BODY_JSON + ":" + clz.getName();
	}
	static public final String getJsonDataFormat(Object obj) {
		return BODY_JSON + ":" + obj.getClass().getName();
	}

	public static boolean isJsonDataFormat(String dataFormat) {
		return dataFormat.startsWith(BODY_JSON + ":");
	}
}
