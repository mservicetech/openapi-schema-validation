package com.mservicetech.openapi.common;

import com.networknt.utility.StringUtils;

import java.util.HashMap;
import java.util.Map;

public enum ParameterType {
	PATH,
	QUERY,
	HEADER,
	COOKIE;
	
	private static Map<String, ParameterType> lookup = new HashMap<>();
	
	static {
		for (ParameterType type: ParameterType.values()) {
			lookup.put(type.name(), type);
		}
	}
	
	public static ParameterType of(String typeStr) {
		return lookup.get(StringUtils.trimToEmpty(typeStr).toUpperCase());
	}
	
	public static boolean is(String typeStr, ParameterType type) {
		return type == of(typeStr);
	}
}
