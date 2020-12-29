package to.etc.cocos.tests;

import org.junit.Assert;
import org.junit.Test;
import to.etc.cocos.hub.Hub;
import to.etc.cocos.hub.Hub.ErrorCounter;
import to.etc.cocos.hub.Hub.ErrorState;

import java.util.Calendar;
import java.util.Collections;
import java.util.Date;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on ${DATE}.
 */
public class TestHubErrorReporting {
	@Test
	public void testReport1() throws Exception {
		Hub h = new Hub(123, "456", false, s -> "aa", null, Collections.emptyList(), false);

		Calendar cal = Calendar.getInstance();
		cal.set(Calendar.MINUTE, 12);						// Make sure we do not wrap the hour during the test
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);

		Date dt = cal.getTime();
		h.registerError(dt);
		h.registerError(dt);
		h.registerError(dt);
		h.registerError(dt);

		ErrorCounter errorCounter = h.getErrorCounterHourList().get(0);
		Assert.assertEquals("Error count for last hour", 4, errorCounter.getCount());

		errorCounter = h.getErrorCounterMinuteList().get(0);
		Assert.assertEquals("Error count for last minute", 4, errorCounter.getCount());

		ErrorState errorState = h.checkErrorCountersForTest(dt, 6, 2);					// We should be in NOT FAILED atte
		Assert.assertEquals("After first 4 errors the state should be not failed", ErrorState.NONE, errorState);
		errorState = h.checkErrorCountersForTest(dt, 6, 2);					// We should be in NOT FAILED atte
		Assert.assertEquals("After first 4 errors the state should be not failed", ErrorState.NONE, errorState);

		//-- Now add a minute further on the line; we should again have only the 4 errors for the last minutes and 8 for the last hour
		cal.add(Calendar.MINUTE, 1);
		dt = cal.getTime();
		h.registerError(dt);
		h.registerError(dt);
		h.registerError(dt);
		h.registerError(dt);
		errorCounter = h.getErrorCounterHourList().get(0);
		Assert.assertEquals("Error count for last hour", 8, errorCounter.getCount());

		errorCounter = h.getErrorCounterMinuteList().get(0);
		Assert.assertEquals("Error count for last minute", 4, errorCounter.getCount());

		errorState = h.checkErrorCountersForTest(dt, 6, 2);					// We should now be FAILING
		Assert.assertEquals("After 8 errors in 3 minutes the state should be failing", ErrorState.FAILING, errorState);

		cal.add(Calendar.MINUTE, 1);
		dt = cal.getTime();
		h.registerError(dt);
		h.registerError(dt);
		errorState = h.checkErrorCountersForTest(dt, 6, 2);					// We should now be FAILING
		Assert.assertEquals("After 8 errors in 3 minutes the state should be failing", ErrorState.FAILED, errorState);



	}

}
