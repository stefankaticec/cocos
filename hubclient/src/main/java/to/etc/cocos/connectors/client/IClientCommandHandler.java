package to.etc.cocos.connectors.client;

import org.eclipse.jdt.annotation.NonNullByDefault;
import to.etc.cocos.connectors.common.CommandContext;

import java.util.List;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 12-09-19.
 */
@NonNullByDefault
public interface IClientCommandHandler {
	void execute(CommandContext ctx, List<byte[]> data) throws Exception;
}
