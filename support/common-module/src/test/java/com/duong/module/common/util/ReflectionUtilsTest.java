package com.duong.module.common.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class ReflectionUtilsTest {

	@Test
	void excludesObjectMethodsFromSuperclassTraversal() {
		var methods = ReflectionUtils.getAllMethods(Child.class);

		assertTrue(methods.stream().map(Method::getName).anyMatch("parentMethod"::equals));
		assertTrue(methods.stream().map(Method::getName).anyMatch("childMethod"::equals));
		assertFalse(methods.stream().anyMatch(method -> method.getDeclaringClass() == Object.class));
	}

	@Test
	void excludesObjectFromFieldSuperclassTraversal() {
		var fields = ReflectionUtils.getAllFields(Child.class);

		assertTrue(fields.stream().map(Field::getName).anyMatch("parentField"::equals));
		assertTrue(fields.stream().map(Field::getName).anyMatch("childField"::equals));
		assertFalse(fields.stream().anyMatch(field -> field.getDeclaringClass() == Object.class));
	}

	@Test
	void inaccessibleJdkMethodsDoNotFailCollection() {
		assertDoesNotThrow(() -> ReflectionUtils.getAllMethods(String.class));
	}

	private static class Parent {
		private String parentField;

		private void parentMethod() {
		}
	}

	private static class Child extends Parent {
		private String childField;

		private void childMethod() {
		}
	}
}
