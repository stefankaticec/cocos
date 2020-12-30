package to.etc.cocos.tests.framework;

public enum TestConditionState {
	UNRESOLVED(false), RESOLVED(true), FAILED(true);

	private boolean m_resolved;

	TestConditionState(boolean resolved) {
		m_resolved = resolved;
	}

	public boolean isResolved() {
		return m_resolved;
	}
}
