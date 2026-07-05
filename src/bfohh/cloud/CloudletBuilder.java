package bfohh.cloud;

import org.cloudbus.cloudsim.UtilizationModelFull;
import java.util.*;

public class CloudletBuilder {
    
    public static List<PriorityAwareCloudlet> createCloudlets(int brokerId, int taskCount) {
        List<PriorityAwareCloudlet> cloudletList = new ArrayList<>();
        
        for (int i = 0; i < taskCount; i++) {
            long length = 1000 + (int) (Math.random() * 5000);
            int priority = 1 + (int)(Math.random() * 3);
            double estimatedTime = length / 1000.0;
            double deadline = estimatedTime * (1.5 + Math.random() * 1.5);
            
            PriorityAwareCloudlet cloudlet = new PriorityAwareCloudlet(
                i, length, 1, 300, 300,
                new UtilizationModelFull(),
                new UtilizationModelFull(),
                new UtilizationModelFull(),
                priority, deadline
            );
            cloudlet.setUserId(brokerId);
            cloudletList.add(cloudlet);
        }
        return cloudletList;
    }
}