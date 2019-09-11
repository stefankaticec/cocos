package to.etc.cocos.connectors.common;

/**
 * Moronic interface because Java's lambda's are not lambda's at all.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 25-6-19.
 */
@FunctionalInterface
public interface IIsRunning {
	boolean isRunning();
}
