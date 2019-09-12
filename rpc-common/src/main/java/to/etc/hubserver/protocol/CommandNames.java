package to.etc.hubserver.protocol;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 13-1-19.
 */
final public class CommandNames {
	public final static byte[] HEADER = {(byte)0xde, (byte)0xca, (byte)0xf1, (byte)0x11};

	public final static int MAX_ENVELOPE_LENGTH = 1024*1024;
	public final static int MAX_DATA_LENGTH = 10*1024*1024;

	//public static final String PING_CMD = "$PING";
	//public static final String PONG_CMD = "$PONG";
	//
	//static public final String HELO_CMD = "HELO";
	//static public final String CLNT_CMD = "CLNT";
	//static public final String SRVR_CMD = "SRVR";
	//static public final String AUTH_CMD = "AUTH";
	//static public final String CLAUTH_CMD = "CLAUTH";

	static public final String BODY_BYTES = "octet-stream";
	static public final String BODY_JSON = "json";


	//public static final String CLIENT_CONNECTED = "CLCONN";
	//public static final String CLIENT_DISCONNECTED = "CLDISC";
	//
	//public static final String INVENTORY_CMD = "CLINVE";

}
