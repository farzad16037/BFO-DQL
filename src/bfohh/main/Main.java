package bfohh.main;

import bfohh.algorithm.BFO_HH;
import bfohh.algorithm.FitnessFunction;
import bfohh.cloud.*;
import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import java.util.*;

public class Main {
    public static void main(String[] args) {
        System.out.println("=====================================");
        System.out.println("شروع تولید transitions واقعی با BFO-HH");
        System.out.println("=====================================");
        
        try {
            CloudSim.init(1, Calendar.getInstance(), false);
            
            Datacenter datacenter = DatacenterBuilder.createDatacenter("Datacenter_1", 150);
            DatacenterBroker broker = new DatacenterBroker("Broker");
            int brokerId = broker.getId();
            
            List<Vm> vms = VmBuilder.createVms(brokerId, 100);
            
            int taskCount = 200;  // یا هر تعداد دلخواه
            List<PriorityAwareCloudlet> priorityCloudlets = new ArrayList<>();
            for (int i = 0; i < taskCount; i++) {
                int priority = 1 + (int)(Math.random() * 3);
                long length = 4000 + (int)(Math.random() * 2000);
                double deadline = 100 + Math.random() * 200;
                
                PriorityAwareCloudlet pCloudlet = new PriorityAwareCloudlet(
                    i, length, 1, 300, 300,
                    new UtilizationModelFull(),
                    new UtilizationModelFull(),
                    new UtilizationModelFull(),
                    priority, deadline
                );
                pCloudlet.setUserId(brokerId);
                priorityCloudlets.add(pCloudlet);
            }
            
            List<Cloudlet> cloudletsForBroker = new ArrayList<>(priorityCloudlets);
            broker.submitVmList(vms);
            broker.submitCloudletList(cloudletsForBroker);
            
            FitnessFunction fitnessFunction = new FitnessFunction();
            BFO_HH bfo = new BFO_HH(10, fitnessFunction);
            
            // تولید transitions واقعی
            bfo.collectRealTransitions(priorityCloudlets, vms, 5000);
            
            // (اختیاری) شبیه‌سازی معمولی را هم می‌توانید اجرا کنید
            CloudSim.startSimulation();
            List<Cloudlet> resultList = broker.getCloudletReceivedList();
            CloudSim.stopSimulation();
            
            System.out.println("=====================================");
            System.out.println("تولید transitions با موفقیت به پایان رسید!");
            System.out.println("=====================================");
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}