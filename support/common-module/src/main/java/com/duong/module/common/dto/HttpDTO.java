package com.duong.module.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@ToString
public class HttpDTO implements Serializable {

	private static final long serialVersionUID = 3020086581825287207L;
	private String requestMethod;
	private String requestUrl;
	private Map<String, Collection<String>> requestHeaders;
	private String requestBody;
	private Integer responseStatus;
	private Map<String, Collection<String>> responseHeaders;
	private String responseBody;
	private Long elapsedTime;

}