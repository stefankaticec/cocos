package to.etc.hubserver;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 12-2-19.
 */
@NonNullByDefault
public interface ISystemContext {
	ConnectionDirectory getDirectory();
}
