package bfohh.algorithm;

import bfohh.cloud.PriorityAwareCloudlet;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.Cloudlet;
import java.util.List;

public class FitnessFunction {
    
    private double weightCost = 0.25;
    private double weightMakespan = 0.25;
    private double weightEnergy = 0.25;
    private double weightPriority = 0.25;
    
    public double calculateCost(List<Cloudlet> cloudlets, List<Vm> vms) {
        double totalCost = 0.0;
        for (Cloudlet cloudlet : cloudlets) {
            int vmId = cloudlet.getVmId();
            if (vmId >= 0 && vmId < vms.size()) {
                Vm vm = vms.get(vmId);
                double executionTime = cloudlet.getActualCPUTime();
                double vmPrice = 0.05 + (vm.getMips() / 20000.0);
                totalCost += executionTime * vmPrice;
            }
        }
        return totalCost;
    }
    
    public double calculateMakespan(List<Cloudlet> cloudlets) {
        double maxFinishTime = 0.0;
        for (Cloudlet cloudlet : cloudlets) {
            maxFinishTime = Math.max(maxFinishTime, cloudlet.getFinishTime());
        }
        return maxFinishTime;
    }
    
    public double calculatePriorityPenalty(List<PriorityAwareCloudlet> cloudlets) {
        double totalPenalty = 0.0;
        for (PriorityAwareCloudlet cloudlet : cloudlets) {
            double finishTime = cloudlet.getFinishTime();
            if (finishTime > cloudlet.getDeadline()) {
                double violationTime = finishTime - cloudlet.getDeadline();
                double weight = getPriorityWeight(cloudlet.getPriority());
                totalPenalty += weight * violationTime;
            }
        }
        return totalPenalty;
    }
    
    private double getPriorityWeight(int priority) {
        switch (priority) {
            case 1: return 3.0;
            case 2: return 1.5;
            default: return 1.0;
        }
    }
}