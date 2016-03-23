package net.floodlightcontroller.serverloadbalancer;

import net.floodlightcontroller.serverloadbalancer.assignment.AssignmentTree;
import net.floodlightcontroller.serverloadbalancer.network.Server;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

public class ServerLoadBalancerUtilTest {

    private static double MACHINE_EPS;

    @Before
    public void setUp() throws Exception {
        while (1.0 + 0.5 * MACHINE_EPS != 1.0) {
            MACHINE_EPS *= 0.5;
        }
    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void testGenerateIPv4AssignmentTree() throws Exception {
        Server s1 = Server.create("10.0.0.1", "00:00:00:00:00:01", 10);
        Server s2 = Server.create("10.0.0.2", "00:00:00:00:00:02", 1);

        AssignmentTree t = ServerLoadBalancerUtil.generateIPv4AssignmentTree(Arrays.asList(s1, s2), 3);
        System.out.println(t);
    }

    @Test
    public void testGenerateIPv4AssignmentTreeFromOldTree() throws Exception {
        Server s1 = Server.create("10.0.0.1", "00:00:00:00:00:01", 6);
        Server s2 = Server.create("10.0.0.2", "00:00:00:00:00:02", 1);

        AssignmentTree t1 = ServerLoadBalancerUtil.generateIPv4AssignmentTree(Arrays.asList(s1, s2), 3);
        System.out.println(t1);

        s1.setWeight(3);
        Server s3 = Server.create("10.0.0.3", "00:00:00:00:00:03", 3);

        AssignmentTree t2 = ServerLoadBalancerUtil.generateIPv4AssignmentTree(Arrays.asList(s1, s2, s3), 3, t1);
        System.out.println(t2);
    }
}