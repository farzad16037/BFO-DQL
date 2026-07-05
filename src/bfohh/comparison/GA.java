package bfohh.comparison;

import bfohh.algorithm.BFO_HH;
import bfohh.cloud.PriorityAwareCloudlet;
import org.cloudbus.cloudsim.Vm;
import java.util.*;

public class GA implements SchedulingAlgorithm {
    
    private int populationSize = 50;
    private int maxGenerations = 100;
    private double crossoverRate = 0.8;
    private double mutationRate = 0.1;
    private Random random = new Random();
    
    private class Chromosome {
        int[] genes;
        double fitness;
        
        Chromosome(int numTasks, int numVms) {
            genes = new int[numTasks];
            for (int i = 0; i < numTasks; i++) {
                if (numVms > 0) {
                    genes[i] = random.nextInt(numVms);
                } else {
                    genes[i] = 0;
                }
            }
            fitness = Double.MAX_VALUE;
        }
        
        Chromosome copy() {
            Chromosome copy = new Chromosome(genes.length, 0);
            copy.genes = this.genes.clone();
            copy.fitness = this.fitness;
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
        
        List<Chromosome> population = new ArrayList<>();
        for (int i = 0; i < populationSize; i++) {
            population.add(new Chromosome(numTasks, numVms));
        }
        
        Chromosome bestChromosome = null;
        double bestFitness = Double.MAX_VALUE;
        
        for (int gen = 0; gen < maxGenerations; gen++) {
            for (Chromosome c : population) {
                c.fitness = calculateFitness(c.genes, cloudlets, vms);
                
                if (c.fitness < bestFitness) {
                    bestFitness = c.fitness;
                    bestChromosome = c.copy();
                }
            }
            
            List<Chromosome> newPopulation = new ArrayList<>();
            newPopulation.add(bestChromosome.copy());
            
            while (newPopulation.size() < populationSize) {
                Chromosome parent1 = tournamentSelection(population);
                Chromosome parent2 = tournamentSelection(population);
                Chromosome offspring = crossover(parent1, parent2);
                mutate(offspring, numVms);
                newPopulation.add(offspring);
            }
            
            population = newPopulation;
        }
        
        BFO_HH.Schedule schedule = new BFO_HH.Schedule();
        if (bestChromosome != null) {
            for (int i = 0; i < numTasks; i++) {
                schedule.assign(cloudlets.get(i).getCloudletId(), bestChromosome.genes[i]);
            }
        }
        return schedule;
    }
    
    private Chromosome tournamentSelection(List<Chromosome> population) {
        int tournamentSize = 5;
        Chromosome best = null;
        double bestFitness = Double.MAX_VALUE;
        
        for (int i = 0; i < tournamentSize; i++) {
            Chromosome c = population.get(random.nextInt(population.size()));
            if (c.fitness < bestFitness) {
                bestFitness = c.fitness;
                best = c;
            }
        }
        return best.copy();
    }
    
    private Chromosome crossover(Chromosome p1, Chromosome p2) {
        Chromosome offspring = new Chromosome(p1.genes.length, 0);
        
        if (random.nextDouble() < crossoverRate) {
            int point = random.nextInt(p1.genes.length);
            for (int i = 0; i < p1.genes.length; i++) {
                offspring.genes[i] = (i < point) ? p1.genes[i] : p2.genes[i];
            }
        } else {
            offspring.genes = p1.genes.clone();
        }
        return offspring;
    }
    
    private void mutate(Chromosome c, int numVms) {
        for (int i = 0; i < c.genes.length; i++) {
            if (random.nextDouble() < mutationRate) {
                c.genes[i] = (numVms > 0) ? random.nextInt(numVms) : 0;
            }
        }
    }
    
    private double calculateFitness(int[] genes, List<PriorityAwareCloudlet> cloudlets, List<Vm> vms) {
        double totalCost = 0, maxFinishTime = 0, totalPenalty = 0;
        
        for (int i = 0; i < cloudlets.size(); i++) {
            PriorityAwareCloudlet cloudlet = cloudlets.get(i);
            Vm vm = vms.get(genes[i]);
            
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
        return "GA";
    }
}