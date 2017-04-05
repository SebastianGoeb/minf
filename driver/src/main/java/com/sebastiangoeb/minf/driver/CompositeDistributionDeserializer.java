package com.sebastiangoeb.minf.driver;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;

public class CompositeDistributionDeserializer implements JsonDeserializer<CompositeDistribution> {

    @Override
    public CompositeDistribution deserialize(JsonElement json, Type typeOfT,
            JsonDeserializationContext context) throws JsonParseException {
        return new CompositeDistribution(json.getAsString());
    }
}
