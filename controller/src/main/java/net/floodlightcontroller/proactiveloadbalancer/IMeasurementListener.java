package net.floodlightcontroller.proactiveloadbalancer;

import net.floodlightcontroller.core.module.IFloodlightService;

interface IMeasurementListener extends IFloodlightService {
    void newMeasurement();
}
