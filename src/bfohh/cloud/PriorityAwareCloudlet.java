package bfohh.cloud;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.UtilizationModel;

public class PriorityAwareCloudlet extends Cloudlet {
    
    private int priority;
    private double deadline;
    
    public PriorityAwareCloudlet(
            int cloudletId,
            long cloudletLength,
            int pesNumber,
            long cloudletFileSize,
            long cloudletOutputSize,
            UtilizationModel utilizationModelCpu,
            UtilizationModel utilizationModelRam,
            UtilizationModel utilizationModelBw,
            int priority,
            double deadline) {
        
        super(cloudletId, cloudletLength, pesNumber, cloudletFileSize,
              cloudletOutputSize, utilizationModelCpu, utilizationModelRam,
              utilizationModelBw);
        
        this.priority = priority;
        this.deadline = deadline;
    }
    
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public double getDeadline() { return deadline; }
    public void setDeadline(double deadline) { this.deadline = deadline; }
    public boolean isSlaViolated(double finishTime) { return finishTime > deadline; }
    public double getSlaViolationTime(double finishTime) { 
        return isSlaViolated(finishTime) ? finishTime - deadline : 0; 
    }
}