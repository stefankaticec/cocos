package to.etc.cocos.tests;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.Test;

@NonNullByDefault
public class ResourceClearingTest {

	@Test(timeout = 30_000)
	public void testConnectDisconnect() throws Exception {
		for(var i =0; i< 10; i++) {
			TestAllBase tb = new TestAllBase();
			System.out.println("----Starting test------ "+ (i+1));
			try {
				tb.waitConnected();
			}finally {
				System.out.println("----Tearing down test suite----");
				tb.tearDown();
			}

		}
	}
}
