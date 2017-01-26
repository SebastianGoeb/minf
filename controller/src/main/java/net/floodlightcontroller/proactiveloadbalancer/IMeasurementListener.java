package net.floodlightcontroller.proactiveloadbalancer;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.proactiveloadbalancer.BinaryTree;

interface IMeasurementListener extends IFloodlightService {
    void newMeasurement(BinaryTree<Long> measurementTree);
}
