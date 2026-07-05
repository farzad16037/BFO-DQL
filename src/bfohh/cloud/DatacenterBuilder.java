package bfohh.cloud;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import java.util.*;

public class DatacenterBuilder {
    
    public static final double IDLE_POWER_RATIO = 0.7;
    public static final double MAX_POWER = 250;
    
    public static Datacenter createDatacenter(String name, int hostCount) {
        List<Host> hostList = new ArrayList<>();
        
        for (int i = 0; i < hostCount; i++) {
            int peCount = 1 + (int)(Math.random() * 4);
            List<Pe> peList = new ArrayList<>();
            
            for (int j = 0; j < peCount; j++) {
                int mips = 1000 + (int)(Math.random() * 5000);
                peList.add(new Pe(j, new PeProvisionerSimple(mips)));
            }
            
            Host host = new Host(
                i,
                new RamProvisionerSimple(16384),
                new BwProvisionerSimple(10000),
                1000000,
                peList,
                new VmSchedulerTimeShared(peList)
            );
            hostList.add(host);
        }
        
        DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
            "x86", "Linux", "Xen", hostList, 10.0, 3.0, 0.05, 0.1, 0.1
        );
        
        try {
            return new Datacenter(
                name,
                characteristics,
                new VmAllocationPolicySimple(hostList),
                new LinkedList<Storage>(),
                0
            );
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    public static double calculatePower(double utilization) {
        return IDLE_POWER_RATIO * MAX_POWER + (1 - IDLE_POWER_RATIO) * MAX_POWER * utilization;
    }
}