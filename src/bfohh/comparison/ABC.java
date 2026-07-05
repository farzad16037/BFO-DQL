package bfohh.comparison;

import bfohh.algorithm.BFO_HH;
import bfohh.cloud.PriorityAwareCloudlet;
import org.cloudbus.cloudsim.Vm;
import java.util.*;


public class ABC implements SchedulingAlgorithm {
    
    private int colonySize = 50;
    private int maxIterations = 100;
    private int limit = 20;
    private Random random = new Random();
    
    private class FoodSource {
        int[] position;
        double fitness;
        int trial;
        
        FoodSource(int numTasks, int numVms) {
            position = new int[numTasks];
            for (int i = 0; i < numTasks; i++) {
                position[i] = (numVms > 0) ? random.nextInt(numVms) : 0;
            }
            trial = 0;
        }
        
        FoodSource copy() {
            FoodSource copy = new FoodSource(position.length, 0);
            copy.position = this.position.clone();
            copy.fitness = this.fitness;
            copy.trial = this.trial;
            return copy;
        }
    }
    
    @Override
    public BFO_HH.Schedule schedule(List<PriorityAwareCloudlet> cloudlets, List<Vm> vms) {
        int numTasks = cloudlets.size();
        int numVms = vms.size();
        
        if (numVms == 0) {
            return new BFO_HH.Schedule();
        }
        
        List<FoodSource> foodSources = new ArrayList<>();
        for (int i = 0; i < colonySize / 2; i++) {
            FoodSource fs = new FoodSource(numTasks, numVms);
            fs.fitness = calculateFitness(fs.position, cloudlets, vms);
            foodSources.add(fs);
        }
        
        FoodSource bestSource = null;
        double bestFitness = Double.MAX_VALUE;
        
        for (int iter = 0; iter < maxIterations; iter++) {
            
            // Employed Bees Phase
            for (int i = 0; i < foodSources.size(); i++) {
                FoodSource fs = foodSources.get(i);
                FoodSource newFs = exploreNeighbor(fs, numTasks, numVms, cloudlets, vms);
                
                if (newFs.fitness < fs.fitness) {
                    foodSources.set(i, newFs);
                } else {
                    fs.trial++;
                }
            }
            
            // Onlooker Bees Phase
            double totalFitness = 0;
            for (FoodSource fs : foodSources) {
                totalFitness += 1.0 / (fs.fitness + 1);
            }
            
            List<FoodSource> newFoodSources = new ArrayList<>();
            for (int i = 0; i < foodSources.size(); i++) {
                double r = random.nextDouble() * totalFitness;
                double cum = 0;
                FoodSource selected = null;
                
                for (FoodSource fs : foodSources) {
                    cum += 1.0 / (fs.fitness + 1);
                    if (r <= cum) {
                        selected = fs;
                        break;
                    }
                }
                
                if (selected != null) {
                    FoodSource newFs = exploreNeighbor(selected, numTasks, numVms, cloudlets, vms);
                    if (newFs.fitness < selected.fitness) {
                        newFoodSources.add(newFs);
                    } else {
                        FoodSource copy = selected.copy();
                        copy.trial++;
                        newFoodSources.add(copy);
                    }
                }
            }
            foodSources = newFoodSources;
            
            // Scout Bees Phase
            for (int i = 0; i < foodSources.size(); i++) {
                FoodSource fs = foodSources.get(i);
                if (fs.trial > limit) {
                    FoodSource newFs = new FoodSource(numTasks, numVms);
                    newFs.fitness = calculateFitness(newFs.position, cloudlets, vms);
                    foodSources.set(i, newFs);
                }
            }
            
            // Update best
            for (FoodSource fs : foodSources) {
                if (fs.fitness < bestFitness) {
                    bestFitness = fs.fitness;
                    bestSource = fs.copy();
                }
            }
        }
        
        // ساخت Schedule
        BFO_HH.Schedule schedule = new BFO_HH.Schedule();
        if (bestSource != null) {
            for (int i = 0; i < numTasks; i++) {
                schedule.assign(cloudlets.get(i).getCloudletId(), bestSource.position[i]);
            }
        }
        return schedule;
    }
    
    private FoodSource exploreNeighbor(FoodSource fs, int numTasks, int numVms,
                                        List<PriorityAwareCloudlet> cloudlets, List<Vm> vms) {
        FoodSource newFs = fs.copy();
        int dim = random.nextInt(numTasks);
        newFs.position[dim] = (numVms > 0) ? random.nextInt(numVms) : 0;
        newFs.fitness = calculateFitness(newFs.position, cloudlets, vms);
        newFs.trial = 0;
        return newFs;
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
        return "ABC";
    }
}