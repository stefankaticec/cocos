package to.etc.cocos.tests;

import org.eclipse.jdt.annotation.NonNullByDefault;

@NonNullByDefault
public class Condition {

	private final ConditionSet m_scenario;

	private String m_name;

	private ConditionState m_state = ConditionState.UNRESOLVED;

	private Exception m_exception;

	Condition(ConditionSet scenario, String name) {
		m_scenario = scenario;
		m_name = name;
	}

	public ConditionState getState() {
		synchronized(m_scenario) {
			return m_state;
		}
	}

	public void resolved() {
		synchronized(m_scenario) {
			m_state = ConditionState.RESOLVED;
			m_scenario.notify();
		}
	}

	public void failed(Exception e) {
		synchronized(m_scenario) {
			m_state = ConditionState.FAILED;
			m_exception = e;
			m_scenario.notify();
		}
	}

	public Exception getException() {
		return m_exception;
	}

	public String getName() {
		return m_name;
	}
}

