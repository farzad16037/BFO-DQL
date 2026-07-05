package bfohh.cloud;

import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.CloudletSchedulerTimeShared;
import java.util.*;

public class VmBuilder {
    
    public static List<Vm> createVms(int brokerId, int vmCount) {
        List<Vm> vmList = new ArrayList<>();
        
        for (int i = 0; i < vmCount; i++) {
            int mips = 500 + (int) (Math.random() * 2500);
            long size = 10000;
            int ram = 2048 + (int) (Math.random() * 4096);
            long bw = 1000;
            int pesNumber = 1 + (int) (Math.random() * 2);
            String vmm = "Xen";
            
            Vm vm = new Vm(
                i, brokerId, mips, pesNumber, ram, bw, size, vmm,
                new CloudletSchedulerTimeShared()
            );
            vmList.add(vm);
        }
        return vmList;
    }
}