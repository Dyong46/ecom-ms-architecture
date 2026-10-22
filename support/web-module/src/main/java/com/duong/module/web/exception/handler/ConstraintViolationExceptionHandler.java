package com.duong.module.web.exception.handler;

import static com.duong.module.common.constant.ErrorCode.INVALID_INPUT_DATA;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableMap;

import com.duong.module.web.payload.response.BaseResponse;
import com.duong.module.web.payload.response.ErrorResponse;

@Component
public class ConstraintViolationExceptionHandler implements AbstractExceptionHandler<ConstraintViolationException> {

	@Override
	public ResponseEntity<Object> handle(ConstraintViolationException e, HttpServletRequest request) {

		Set<ConstraintViolation<?>> constraintViolations = e.getConstraintViolations();
		List<Map<String, Object>> violationData = constraintViolations.stream()
				.map(constraintViolation -> ImmutableMap.of("message", constraintViolation.getMessage(),
						"property_path", constraintViolation.getPropertyPath().toString(), "invalid_value",
						ObjectUtils.defaultIfNull(constraintViolation.getInvalidValue(), "")))
				.collect(Collectors.toList());

		ErrorResponse errorResponse = ErrorResponse.create().errorCode(INVALID_INPUT_DATA).extraData(violationData);

		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(BaseResponse.of(errorResponse));

	}

}
