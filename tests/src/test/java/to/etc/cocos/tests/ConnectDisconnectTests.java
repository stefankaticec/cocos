package to.etc.cocos.tests;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.Test;
import to.etc.cocos.tests.framework.TestAllBase;

@NonNullByDefault
public class ConnectDisconnectTests {

	@Test(timeout = 120_000)
	public void testConnectDisconnect() throws Exception{
		for(var i = 0; i < 10; i++) {
			var tb = new TestAllBase();
			try {
				tb.startAllAndAwaitSequential();
			}finally {
				tb.tearDown();
			}

		}
	}
}
