package com.duong.module.web.exception.handler.resolver;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.duong.module.web.exception.handler.AbstractExceptionHandler;

@Component
public class ExceptionHandlerResolver {

	private final AbstractExceptionHandler<Exception> defaultExceptionHandler;
	private final List<AbstractExceptionHandler<? extends Exception>> sortedExceptionHandlers;

	public ExceptionHandlerResolver(AbstractExceptionHandler<Exception> defaultExceptionHandler,
			List<AbstractExceptionHandler<? extends Exception>> exceptionHandlers) {
		this.defaultExceptionHandler = defaultExceptionHandler;
		this.sortedExceptionHandlers = exceptionHandlers.stream().sorted().collect(Collectors.toList());
	}

	public AbstractExceptionHandler<? extends Exception> resolveExceptionHandler(Exception e) {
		return sortedExceptionHandlers.stream().filter(handler -> handler.getExceptionClass().isInstance(e)).findFirst()
				.orElse(defaultExceptionHandler);
	}

}
