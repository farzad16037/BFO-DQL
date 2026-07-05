package bfohh.comparison;

import bfohh.algorithm.BFO_HH;
import bfohh.cloud.PriorityAwareCloudlet;
import org.cloudbus.cloudsim.Vm;
import java.util.*;

public class ACO implements SchedulingAlgorithm {
    
    private int numAnts = 30;
    private int maxIterations = 100;
    private double alpha = 1.0;
    private double beta = 2.0;
    private double rho = 0.1;
    private double Q = 100;
    private Random random = new Random();
    private double[][] pheromone;
    
    @Override
    public BFO_HH.Schedule schedule(List<PriorityAwareCloudlet> cloudlets, List<Vm> vms) {
        int numTasks = cloudlets.size();
        int numVms = vms.size();
        
        if (numVms == 0) {
            return new BFO_HH.Schedule();
        }
        
        pheromone = new double[numTasks][numVms];
        for (int i = 0; i < numTasks; i++) {
            Arrays.fill(pheromone[i], 1.0);
        }
        
        int[] bestAssignment = null;
        double bestFitness = Double.MAX_VALUE;
        
        for (int iter = 0; iter < maxIterations; iter++) {
            List<int[]> antAssignments = new ArrayList<>();
            List<Double> antFitnesses = new ArrayList<>();
            
            for (int ant = 0; ant < numAnts; ant++) {
                int[] assignment = new int[numTasks];
                
                for (int i = 0; i < numTasks; i++) {
                    double[] probs = new double[numVms];
                    double sum = 0;
                    
                    for (int v = 0; v < numVms; v++) {
                        double tau = pheromone[i][v];
                        double eta = 1.0 / (cloudlets.get(i).getCloudletLength() / vms.get(v).getMips());
                        probs[v] = Math.pow(tau, alpha) * Math.pow(eta, beta);
                        sum += probs[v];
                    }
                    
                    if (sum > 0) {
                        double r = random.nextDouble() * sum;
                        double cum = 0;
                        for (int v = 0; v < numVms; v++) {
                            cum += probs[v];
                            if (r <= cum) {
                                assignment[i] = v;
                                break;
                            }
                        }
                    } else {
                        assignment[i] = (numVms > 0) ? random.nextInt(numVms) : 0;
                    }
                }
                
                double fitness = calculateFitness(assignment, cloudlets, vms);
                antAssignments.add(assignment);
                antFitnesses.add(fitness);
                
                if (fitness < bestFitness) {
                    bestFitness = fitness;
                    bestAssignment = assignment.clone();
                }
            }
            
            // به‌روزرسانی فرومون
            for (int i = 0; i < numTasks; i++) {
                for (int v = 0; v < numVms; v++) {
                    pheromone[i][v] *= (1 - rho);
                }
            }
            
            for (int a = 0; a < antAssignments.size(); a++) {
                int[] assignment = antAssignments.get(a);
                double fitness = antFitnesses.get(a);
                
                for (int i = 0; i < numTasks; i++) {
                    pheromone[i][assignment[i]] += Q / fitness;
                }
            }
        }
        
        BFO_HH.Schedule schedule = new BFO_HH.Schedule();
        if (bestAssignment != null) {
            for (int i = 0; i < numTasks; i++) {
                schedule.assign(cloudlets.get(i).getCloudletId(), bestAssignment[i]);
            }
        }
        return schedule;
    }
    
    private double calculateFitness(int[] assignment, List<PriorityAwareCloudlet> cloudlets, List<Vm> vms) {
        double totalCost = 0, maxFinishTime = 0, totalPenalty = 0;
        
        for (int i = 0; i < cloudlets.size(); i++) {
            PriorityAwareCloudlet cloudlet = cloudlets.get(i);
            Vm vm = vms.get(assignment[i]);
            
            double execTime = (double) cloudlet.getCloudletLength() / vm.getMips();
            totalCost += execTime * (0.05 + vm.getMips() / 20000.0);
            maxFinishTime = Math.max(maxFinishTime, execTime);
            
            if (execTime > cloudlet.getDeadline()) {
                double violationTime = execTime - cloudlet.getDeadline();
                double weight = getPriorityWeight(cloudlet.getPriority());
                totalPenalty += weight * violationTime;
            }
        }
        return totalCost * 0.3 + maxFinishTime * 0.3 + totalPenalty * 0.4;
    }
    
    private double getPriorityWeight(int priority) {
        switch (priority) {
            case 1: return 3.0;
            case 2: return 1.5;
            default: return 1.0;
        }
    }
    
    @Override
    public String getName() {
        return "ACO";
    }
}