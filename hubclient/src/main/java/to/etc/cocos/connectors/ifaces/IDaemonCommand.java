package to.etc.cocos.connectors.ifaces;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 25-6-19.
 */
@NonNullByDefault
public interface IDaemonCommand {
	void addListener(IDaemonCommandListener listener);

	void removeListener(IDaemonCommandListener listener);

}
