package to.etc.cocos.hub;

import org.eclipse.jdt.annotation.NonNullByDefault;
import to.etc.cocos.hub.parties.ConnectionDirectory;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 12-2-19.
 */
@NonNullByDefault
public interface ISystemContext {
	ConnectionDirectory getDirectory();
}
