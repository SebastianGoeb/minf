package net.floodlightcontroller.proactiveloadbalancer.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdKeySerializer;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import net.floodlightcontroller.proactiveloadbalancer.*;
import net.floodlightcontroller.proactiveloadbalancer.domain.Config;
import net.floodlightcontroller.proactiveloadbalancer.serializer.*;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;
import org.restlet.data.Status;
import org.restlet.resource.Put;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class ConfigResource extends ServerResource {
    protected static Logger log = LoggerFactory.getLogger(ConfigResource.class);

    @Put("json")
    public Response<?> set(String json) throws IOException {
        IProactiveLoadBalancerService service = ((IProactiveLoadBalancerService) getContext()
                .getAttributes()
                .get(IProactiveLoadBalancerService.class.getCanonicalName()));

        SimpleModule module = new SimpleModule()
                .addKeyDeserializer(IPv4Address.class, new IPv4AddressKeyDeserializer())
                .addKeySerializer(IPv4Address.class, new StdKeySerializer())
                .addDeserializer(IPv4Address.class, new IPv4AddressDeserializer())
                .addSerializer(IPv4Address.class, new ToStringSerializer())

                .addKeyDeserializer(IPv4AddressWithMask.class, new IPv4AddressWithMaskKeyDeserializer())
                .addKeySerializer(IPv4AddressWithMask.class, new StdKeySerializer())
                .addDeserializer(IPv4AddressWithMask.class, new IPv4AddressWithMaskDeserializer())
                .addSerializer(IPv4AddressWithMask.class, new ToStringSerializer())

                .addKeyDeserializer(DatapathId.class, new DatapathIdKeyDeserializer())
                .addKeySerializer(DatapathId.class, new StdKeySerializer())
                .addDeserializer(DatapathId.class, new DatapathIdDeserializer())
                .addSerializer(DatapathId.class, new ToStringSerializer());
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(module);

        // Parse JSON
        Config config;
        try {
            config = mapper.readValue(json, Config.class);
        } catch (IOException e) {
            log.info("Invalid json", e);
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return new Response<Void>()
                    .addError("Invalid json");
        }

        // Apply configuration
        service.setConfig(config);

        // Construct response
        setStatus(Status.SUCCESS_OK);
        return new Response<Config>()
                .setData(config);
    }
}
