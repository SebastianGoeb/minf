package net.floodlightcontroller.proactiveloadbalancer;

import net.floodlightcontroller.proactiveloadbalancer.domain.Measurement;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.*;

class SshParser {
    private static final Logger LOG = LoggerFactory.getLogger(SshParser.class);
    private static final String HEADER_START = "OFPST_FLOW";

    static List<Measurement> parseResult(String result, int tableId) {
        try {
            List<Measurement> parsedMeasurements = new ArrayList<>();
            String[] lines = result.trim().split("\n");
            // Split line into proprties, skipping first line (OFPST_FLOW reply...)
            boolean hasHeaderBeenEncountered = false;
            for (String line : lines) {
                if (!hasHeaderBeenEncountered) {
                    if (line.startsWith(HEADER_START)) {
                        hasHeaderBeenEncountered = true;
                    }
                    continue;
                }
                Measurement measurement = parseLine(tableId, line);
                if (measurement != null) {
                    parsedMeasurements.add(measurement);
                }
            }
            return parsedMeasurements.stream()
                    .collect(groupingBy(
                            Measurement::getPrefix,
                            reducing((m0, m1) -> new Measurement(m0.getPrefix(), m0.getBytes() + m1.getBytes()))))
                    .values().stream()
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(toList());
        } catch (Exception e) {
            LOG.warn("Unable to parse ssh result: {}", result, e);
            return emptyList();
        }
    }

    private static Measurement parseLine(int tableId, String line) {
        String[] parts = line.trim().split("(, )| ");
        Map<String, String> properties = new HashMap<>();
        for (String part : parts) {
            if (part.startsWith("cookie")) {
                properties.put("cookie", part.split("=", 2)[1]);
            } else if (part.startsWith("duration")) {
                properties.put("duration", part.split("=", 2)[1]);
            } else if (part.startsWith("table")) {
                properties.put("table", part.split("=", 2)[1]);
            } else if (part.startsWith("n_packets")) {
                properties.put("n_packets", part.split("=", 2)[1]);
            } else if (part.startsWith("n_bytes")) {
                properties.put("n_bytes", part.split("=", 2)[1]);
            } else if (part.startsWith("actions")) {
                properties.put("actions", part.split("=", 2)[1]);
            } else {
                properties.put("match", part);
            }
        }
        if (Integer.parseInt(properties.get("table")) == tableId) {
            // Build match
            String[] matchParts = properties.get("match").split(",");
            HashMap<String, String> matchProperties = new HashMap<>();
            for (String matchPart : matchParts) {
                if (matchPart.contains("=")) {
                    String[] matchPartComponents = matchPart.split("=", 2);
                    matchProperties.put(matchPartComponents[0], matchPartComponents[1]);
                }
            }
            // Source
            IPv4AddressWithMask nwDst = matchProperties.containsKey("nw_dst")
                    ? IPv4AddressWithMask.of(matchProperties.get("nw_dst")) : null;
            // Destination
            IPv4AddressWithMask nwSrc = matchProperties.containsKey("nw_src")
                    ? IPv4AddressWithMask.of(matchProperties.get("nw_src")) : null;
            // Bytes
            int bytes = Integer.parseInt(properties.get("n_bytes"));
            // Measurement
            return new Measurement(nwSrc != null ? nwSrc : nwDst, bytes);
        }
        return null;
    }
}
