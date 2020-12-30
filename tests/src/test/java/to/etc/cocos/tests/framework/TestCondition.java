package to.etc.cocos.tests.framework;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

@NonNullByDefault
public class TestCondition {

	private final TestConditionSet m_scenario;

	private String m_name;

	private TestConditionState m_state = TestConditionState.UNRESOLVED;

	@Nullable
	private String m_exceptionMessage;

	TestCondition(TestConditionSet scenario, String name) {
		m_scenario = scenario;
		m_name = name;
	}

	public TestConditionState getState() {
		synchronized(m_scenario) {
			return m_state;
		}
	}

	public void resolved() {
		synchronized(m_scenario) {
			ensureUnresolved();
			m_state = TestConditionState.RESOLVED;
			m_scenario.notify();
		}
	}

	public void failed(Exception e) {
		failed(e.getMessage());
	}

	public void failed(String message) {
		synchronized(m_scenario) {
			ensureUnresolved();
			m_state = TestConditionState.FAILED;
			m_exceptionMessage = message;
			m_scenario.notify();
		}
	}

	private void ensureUnresolved() {
		synchronized(m_scenario) {
			if(m_state != TestConditionState.UNRESOLVED) {
				throw new IllegalStateException("State is unresolved");
			}
		}
	}

	@Nullable
	public String getExceptionMessage() {
		return m_exceptionMessage;
	}

	public String getName() {
		return m_name;
	}
}

