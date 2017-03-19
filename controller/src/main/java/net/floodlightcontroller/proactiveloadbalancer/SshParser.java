package net.floodlightcontroller.proactiveloadbalancer;

import net.floodlightcontroller.proactiveloadbalancer.domain.Flow;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;

class SshParser {
    private static final Logger LOG = LoggerFactory.getLogger(SshParser.class);
    private static final String HEADER_START = "OFPST_FLOW";

    static List<Flow> parseResult(String result) {
        try {
            List<Flow> flows = new ArrayList<>();
            String[] lines = result.trim().split("\n");
            // Split line into properties, skipping first line (OFPST_FLOW reply...)
            boolean hasHeaderBeenEncountered = false;
            for (String line : lines) {
                if (!hasHeaderBeenEncountered) {
                    if (line.startsWith(HEADER_START)) {
                        hasHeaderBeenEncountered = true;
                    }
                    continue;
                }
                flows.add(parseLine(line));
            }
            return flows;
        } catch (Exception e) {
            LOG.warn("Unable to parse ssh result: {}", result, e);
            return emptyList();
        }
    }

    private static Flow parseLine(String line) {
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
        long bytes = Long.parseLong(properties.get("n_bytes"));
        // Cookie
        U64 cookie = U64.parseHex(properties.get("cookie").substring(2));
        // Table ID
        short tableId = Short.parseShort(properties.get("table"));
        // Prefix
        IPv4AddressWithMask prefix = nwSrc != null ? nwSrc : nwDst != null ? nwDst : IPv4AddressWithMask.of("0.0.0.0/0");
        // Measurement
        return new Flow(cookie, tableId, prefix, bytes);
    }
}
