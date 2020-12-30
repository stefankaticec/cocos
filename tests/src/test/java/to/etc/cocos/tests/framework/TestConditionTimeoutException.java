package to.etc.cocos.tests.framework;

import java.util.ArrayList;
import java.util.stream.Collectors;

public class TestConditionTimeoutException extends RuntimeException {
	public TestConditionTimeoutException(ArrayList<TestCondition> unresolved) {
		super(unresolved.stream().map(x-> "'".concat(x.getName()).concat("'")).collect(Collectors.joining(", ")) + " failed to complete.");
	}
}
