package com.duong.module.common.model;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;
import lombok.ToString;

@Data
@ToString(onlyExplicitlyIncluded = true)
public class GrantTypeForm {
	@ToString.Include
	private Map<String, String> params = new HashMap<>();

	@ToString.Include
	private final String GRANT_TYPE = "grant_type";

	@ToString.Include
	private final String CLIENT_CREDENTITALS = "client_credentials";

	public GrantTypeForm() {
		this.params.put(GRANT_TYPE, CLIENT_CREDENTITALS);
	}

	public Map<String, String> getParams() {
		return this.params;
	}

	public void setParams(final Map<String, String> params) {
		this.params = params;
	}

}
