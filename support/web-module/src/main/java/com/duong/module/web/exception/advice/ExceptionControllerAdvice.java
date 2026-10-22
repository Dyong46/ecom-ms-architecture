package com.duong.module.web.exception.advice;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.web.servlet.error.AbstractErrorController;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import com.duong.module.web.exception.handler.resolver.ExceptionHandlerResolver;

@Slf4j
@RestController
@RestControllerAdvice
public class ExceptionControllerAdvice extends AbstractErrorController {

	private final ErrorAttributes errorAttributes;
	private final ExceptionHandlerResolver exceptionHandlerResolver;

	public ExceptionControllerAdvice(ErrorAttributes errorAttributes,
			ExceptionHandlerResolver exceptionHandlerResolver) {
		super(errorAttributes);
		this.errorAttributes = errorAttributes;
		this.exceptionHandlerResolver = exceptionHandlerResolver;
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<Object> handleException(Exception e, HttpServletRequest request) {

		log.error("Caught exception {} on request {}", e.getClass().getSimpleName(),
				request.getRequestURI(), e);

		return exceptionHandlerResolver.resolveExceptionHandler(e).handleException(e, request);

	}

	@RequestMapping("${error.path:/error}")
	public ResponseEntity<Object> error(HttpServletRequest request) {
		WebRequest webRequest = new ServletWebRequest(request);
		Exception e = (Exception) errorAttributes.getError(webRequest);
		if (e != null) {
			return handleException(e, request);
		} else {
			return null;
		}
	}

}
