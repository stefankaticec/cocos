package to.etc.cocos.tests.framework;

import org.eclipse.jdt.annotation.NonNullByDefault;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@NonNullByDefault
public class TestConditionSet {
	private final List<TestCondition> m_testConditionList = new ArrayList<>();

	private long m_timout = 0;

	public TestConditionSet(Duration timeout) {
		m_timout = timeout.toMillis();
	}

	public void await() throws Exception {
		var endTime = System.currentTimeMillis() + m_timout;
		synchronized(this) {
			while(true) {
				List<TestCondition> testConditions = m_testConditionList;

				var unresolved = new ArrayList<TestCondition>();
				for(TestCondition testCondition : testConditions) {
					if(testCondition.getState() == TestConditionState.FAILED) {
						throw new IllegalStateException(testCondition.getExceptionMessage());
					}
					if(testCondition.getState() == TestConditionState.UNRESOLVED) {
						unresolved.add(testCondition);
					}
				}
				if(unresolved.isEmpty()) {
					return;
				}
				var currentTime = System.currentTimeMillis();
				var remaining = endTime - currentTime;
				if(remaining <= 0) {
					throw new TestConditionTimeoutException(unresolved);
				}
				wait(remaining);
			}
		}
	}

	public TestCondition createCondition(String name) {
		TestCondition c = new TestCondition(this, name);
		synchronized(this) {
			m_testConditionList.add(c);
		}
		return c;
	}
}
