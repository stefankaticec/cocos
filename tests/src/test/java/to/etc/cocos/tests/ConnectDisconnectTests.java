package to.etc.cocos.tests;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.Test;
import to.etc.cocos.tests.framework.TestAllBaseNew;

@NonNullByDefault
public class ConnectDisconnectTests {

	@Test(timeout = 60_000)
	public void testConnectDisconnect() throws Exception{
		for(var i = 0; i < 10; i++) {
			var tb = new TestAllBaseNew();
			try {
				tb.startAllAndAwaitSequential();
			}finally {
				tb.tearDown();
			}

		}
	}
}
