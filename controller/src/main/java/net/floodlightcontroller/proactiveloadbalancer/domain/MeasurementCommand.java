package net.floodlightcontroller.proactiveloadbalancer.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public class MeasurementCommand {

    @JsonProperty
    private String endpoint;

    @JsonProperty
    private String command;

    public MeasurementCommand() {}

    public String getEndpoint() {

        return endpoint;
    }

    public MeasurementCommand setEndpoint(String endpoint) {
        this.endpoint = endpoint;
        return this;
    }

    public String getCommand() {
        return command;
    }

    public MeasurementCommand setCommand(String command) {
        this.command = command;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MeasurementCommand that = (MeasurementCommand) o;
        return Objects.equals(endpoint, that.endpoint) &&
                Objects.equals(command, that.command);
    }

    @Override
    public int hashCode() {
        return Objects.hash(endpoint, command);
    }
}
