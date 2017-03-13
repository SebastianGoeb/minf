package com.sebastiangoeb.minf.driver;

import java.lang.reflect.Type;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

public class DistributionDeserializer implements JsonDeserializer<Distribution> {

	@Override
	public Distribution deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
			throws JsonParseException {
		return Distribution.fromString(json.getAsString());
	}
}
