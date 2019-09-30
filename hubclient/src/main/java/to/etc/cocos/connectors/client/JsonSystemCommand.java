package to.etc.cocos.connectors.client;

import to.etc.cocos.connectors.common.CommandContext;
import to.etc.cocos.connectors.common.JsonPacket;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This JSON command handler is supposed to start some external process of which we will
 * gobble stdout and stderr and send it as output to the peer.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 24-09-19.
 */
abstract public class JsonSystemCommand<T extends JsonPacket> implements IJsonCommandHandler<T> {
	protected int runRemoteCommand(CommandContext ctx, List<String> args) throws Exception {
		System.out.println(">> run: " + args.stream().collect(Collectors.joining(" ")));

		Process process = new ProcessBuilder()
			.command(args)
			.redirectErrorStream(true)							// merge stdout and stderr
			.start();

		//-- Start the stream reader for this process,
		int rc = -1;
		try(StdoutPacketThread pt = new StdoutPacketThread(ctx, process.getInputStream(), StandardCharsets.UTF_8)) {
			pt.start();
			rc = process.waitFor();
			System.err.println(">> run: exit code " + rc);
		}
		System.err.println(">> run: packet reader completed");
		return rc;
	}
}
