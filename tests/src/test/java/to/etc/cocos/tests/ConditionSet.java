package to.etc.cocos.tests;

import org.eclipse.jdt.annotation.NonNullByDefault;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@NonNullByDefault
public class ConditionSet {
	private final List<Condition> m_conditionList = new ArrayList<>();

	private final long m_timout;

	public ConditionSet(Duration timout){
		m_timout = timout.toMillis();
	}

	public void await() throws Exception {
		var endTime = System.currentTimeMillis() + m_timout;
		synchronized(this) {
			while(true) {
				List<Condition> conditions = m_conditionList;

				var unreselved = false;
				for(Condition condition : conditions) {
					if(condition.getState() == ConditionState.FAILED) {
						throw new IllegalStateException(condition.getException());
					}
					if(condition.getState() == ConditionState.UNRESOLVED) {
						unreselved = true;
					}
				}
				if(!unreselved) {
					return;
				}
				var currentTime = System.currentTimeMillis();
				var remaining = endTime - currentTime;
				if(remaining <= 0) {
					throw new IllegalStateException("Timeout");
				}
				wait(remaining);
			}
		}
	}

	public Condition createCondition(String name) {
		Condition c = new Condition(this, name);
		synchronized(this) {
			m_conditionList.add(c);
		}
		return c;
	}
}
