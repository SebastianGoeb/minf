package net.floodlightcontroller.serverloadbalancer;

import net.floodlightcontroller.core.module.IFloodlightService;
import org.projectfloodlight.openflow.types.DatapathId;

import java.util.List;
import java.util.Map;

public interface IServerLoadBalancerService extends IFloodlightService {
    public void requestTransition();
    public List<DatapathId> getDpids();

    // Servers
    public void addServer(Server server);
    public Server getServer(int id);
    public void removeServers(List<Integer> ids);
    public void removeAllServers();

    //Stats
    public Map<Server, Long> getStats(DatapathId dpid);
    public int numRules(DatapathId dpid);

    public void autoSetMaxPrefixLength();
}
