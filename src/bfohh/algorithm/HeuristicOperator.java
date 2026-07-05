package bfohh.algorithm;

import bfohh.cloud.PriorityAwareCloudlet;
import org.cloudbus.cloudsim.Vm;
import java.util.*;

public class HeuristicOperator {
    
    private Random random = new Random();
    
    public int select(PriorityAwareCloudlet cloudlet, List<Vm> vms) {
        int bestVmId = -1;
        double bestScore = Double.MAX_VALUE;
        
        for (Vm vm : vms) {
            double score = (double) cloudlet.getCloudletLength() / vm.getMips();
            if (score < bestScore) {
                bestScore = score;
                bestVmId = vm.getId();
            }
        }
        return bestVmId;
    }
    
    public Map<Integer, Integer> move(Map<Integer, Integer> assignments, 
                                      List<PriorityAwareCloudlet> cloudlets, 
                                      List<Vm> vms) {
        Map<Integer, Integer> newAssignments = new HashMap<>(assignments);
        
        int busiestVm = -1, maxLoad = -1;
        int freestVm = -1, minLoad = Integer.MAX_VALUE;
        
        for (Vm vm : vms) {
            int load = countCloudletsInVm(assignments, vm.getId());
            if (load > maxLoad) { maxLoad = load; busiestVm = vm.getId(); }
            if (load < minLoad) { minLoad = load; freestVm = vm.getId(); }
        }
        
        if (busiestVm != -1 && freestVm != -1 && busiestVm != freestVm) {
            List<Integer> cloudletsInBusyVm = findCloudletsInVm(assignments, busiestVm);
            if (!cloudletsInBusyVm.isEmpty()) {
                int cloudletId = cloudletsInBusyVm.get(random.nextInt(cloudletsInBusyVm.size()));
                newAssignments.put(cloudletId, freestVm);
            }
        }
        return newAssignments;
    }
    
    public Map<Integer, Integer> swap(Map<Integer, Integer> assignments,
                                       List<PriorityAwareCloudlet> cloudlets,
                                       List<Vm> vms) {
        Map<Integer, Integer> newAssignments = new HashMap<>(assignments);
        if (vms.size() < 2) return newAssignments;
        
        int vm1 = vms.get(random.nextInt(vms.size())).getId();
        int vm2 = vms.get(random.nextInt(vms.size())).getId();
        if (vm1 == vm2) return newAssignments;
        
        List<Integer> cloudletsInVm1 = findCloudletsInVm(assignments, vm1);
        List<Integer> cloudletsInVm2 = findCloudletsInVm(assignments, vm2);
        
        if (!cloudletsInVm1.isEmpty() && !cloudletsInVm2.isEmpty()) {
            int c1 = cloudletsInVm1.get(random.nextInt(cloudletsInVm1.size()));
            int c2 = cloudletsInVm2.get(random.nextInt(cloudletsInVm2.size()));
            newAssignments.put(c1, vm2);
            newAssignments.put(c2, vm1);
        }
        return newAssignments;
    }
    
    public Map<Integer, Integer> drop(Map<Integer, Integer> assignments,
                                       List<PriorityAwareCloudlet> cloudlets,
                                       List<Vm> vms) {
        Map<Integer, Integer> newAssignments = new HashMap<>(assignments);
        
        int busiestVm = -1, maxLoad = -1;
        for (Vm vm : vms) {
            int load = countCloudletsInVm(assignments, vm.getId());
            if (load > maxLoad) { maxLoad = load; busiestVm = vm.getId(); }
        }
        
        if (busiestVm != -1) {
            List<Integer> cloudletsInBusyVm = findCloudletsInVm(assignments, busiestVm);
            if (!cloudletsInBusyVm.isEmpty()) {
                int cloudletId = cloudletsInBusyVm.get(random.nextInt(cloudletsInBusyVm.size()));
                newAssignments.remove(cloudletId);
            }
        }
        return newAssignments;
    }
    
    private int countCloudletsInVm(Map<Integer, Integer> assignments, int vmId) {
        int count = 0;
        for (int assignedVm : assignments.values()) if (assignedVm == vmId) count++;
        return count;
    }
    
    private List<Integer> findCloudletsInVm(Map<Integer, Integer> assignments, int vmId) {
        List<Integer> cloudlets = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : assignments.entrySet()) {
            if (entry.getValue() == vmId) cloudlets.add(entry.getKey());
        }
        return cloudlets;
    }
}