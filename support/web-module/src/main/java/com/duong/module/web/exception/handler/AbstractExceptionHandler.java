package com.duong.module.web.exception.handler;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import com.duong.module.common.util.GenericUtils;

public interface AbstractExceptionHandler<E extends Exception> extends Comparable<AbstractExceptionHandler<E>> {

	default Class<E> getExceptionClass() {
		return GenericUtils.getTypeParameter(getClass());
	}

	ResponseEntity<Object> handle(E e, HttpServletRequest request);

	default ResponseEntity<Object> handleException(Exception e, HttpServletRequest request) {
		return handle(getExceptionClass().cast(e), request);
	}

	@Override
	default int compareTo(@NonNull AbstractExceptionHandler<E> o) {
		Class<E> c1 = this.getExceptionClass();
		Class<E> c2 = o.getExceptionClass();
		return c1.isAssignableFrom(c2) ? 1 : c2.isAssignableFrom(c1) ? -1 : 0;
	}

}
