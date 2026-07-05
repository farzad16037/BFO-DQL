package bfohh.comparison;

import bfohh.algorithm.BFO_HH;
import bfohh.cloud.PriorityAwareCloudlet;
import org.cloudbus.cloudsim.Vm;
import java.util.*;

public class PSO implements SchedulingAlgorithm {
    
    private int numParticles = 30;
    private int maxIterations = 100;
    private double w = 0.7;
    private double c1 = 1.5;
    private double c2 = 1.5;
    private Random random = new Random();
    
    private class Particle {
        int[] position;
        int[] bestPosition;
        double[] velocity;
        double fitness;
        double bestFitness;
        
        Particle(int numTasks, int numVms) {
            position = new int[numTasks];
            bestPosition = new int[numTasks];
            velocity = new double[numTasks];
            
            for (int i = 0; i < numTasks; i++) {
                position[i] = (numVms > 0) ? random.nextInt(numVms) : 0;
                velocity[i] = random.nextDouble() * 2 - 1;
            }
            bestPosition = position.clone();
            fitness = Double.MAX_VALUE;
            bestFitness = Double.MAX_VALUE;
        }
    }
    
    @Override
    public BFO_HH.Schedule schedule(List<PriorityAwareCloudlet> cloudlets, List<Vm> vms) {
        int numTasks = cloudlets.size();
        int numVms = vms.size();
        
        if (numVms == 0) {
            return new BFO_HH.Schedule();
        }
        
        List<Particle> particles = new ArrayList<>();
        for (int i = 0; i < numParticles; i++) {
            particles.add(new Particle(numTasks, numVms));
        }
        
        int[] globalBestPosition = null;
        double globalBestFitness = Double.MAX_VALUE;
        
        for (int iter = 0; iter < maxIterations; iter++) {
            for (Particle p : particles) {
                p.fitness = calculateFitness(p.position, cloudlets, vms);
                
                if (p.fitness < p.bestFitness) {
                    p.bestFitness = p.fitness;
                    p.bestPosition = p.position.clone();
                }
                
                if (p.fitness < globalBestFitness) {
                    globalBestFitness = p.fitness;
                    globalBestPosition = p.position.clone();
                }
            }
            
            for (Particle p : particles) {
                for (int d = 0; d < numTasks; d++) {
                    double r1 = random.nextDouble();
                    double r2 = random.nextDouble();
                    
                    p.velocity[d] = w * p.velocity[d] +
                                    c1 * r1 * (p.bestPosition[d] - p.position[d]) +
                                    c2 * r2 * (globalBestPosition[d] - p.position[d]);
                    
                    int newPos = (int)(p.position[d] + p.velocity[d]);
                    if (newPos < 0) newPos = 0;
                    if (newPos >= numVms) newPos = numVms - 1;
                    p.position[d] = newPos;
                }
            }
        }
        
        BFO_HH.Schedule schedule = new BFO_HH.Schedule();
        if (globalBestPosition != null) {
            for (int i = 0; i < numTasks; i++) {
                schedule.assign(cloudlets.get(i).getCloudletId(), globalBestPosition[i]);
            }
        }
        return schedule;
    }
    
    private double calculateFitness(int[] position, List<PriorityAwareCloudlet> cloudlets, List<Vm> vms) {
        double totalCost = 0, maxFinishTime = 0, totalPenalty = 0;
        
        for (int i = 0; i < cloudlets.size(); i++) {
            PriorityAwareCloudlet cloudlet = cloudlets.get(i);
            Vm vm = vms.get(position[i]);
            
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
        return "PSO";
    }
}