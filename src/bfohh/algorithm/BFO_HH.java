package bfohh.algorithm;

import bfohh.cloud.PriorityAwareCloudlet;
import bfohh.comparison.SchedulingAlgorithm;
import bfohh.cloud.DatacenterBuilder;
import org.cloudbus.cloudsim.Vm;
import java.io.PrintWriter;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class BFO_HH implements SchedulingAlgorithm {
    
    private Random random = new Random();
    private int dimension;
    private FitnessFunction fitnessFunction;
    private HeuristicOperator heuristicOperator = new HeuristicOperator();
    
    // پارامترهای BFO (مطابق مقاله قبلی)
    private int populationSize = 50;
    private int swimLength = 4;
    private int chemotaxisSteps = 100;
    private int reproductionSteps = 4;
    private int eliminationDispersalEvents = 2;
    private double eliminationProbability = 0.25;
    private double stepSize = 0.1;
    
    public enum Heuristic { SELECT, MOVE, SWAP, DROP }
    
    public static class Schedule {
        private Map<Integer, Integer> assignments = new HashMap<>();
        public void assign(int cloudletId, int vmId) { assignments.put(cloudletId, vmId); }
        public int getVmForCloudlet(int cloudletId) { return assignments.getOrDefault(cloudletId, -1); }
        public Map<Integer, Integer> getAllAssignments() { return assignments; }
        public Schedule copy() {
            Schedule copy = new Schedule();
            copy.assignments.putAll(this.assignments);
            return copy;
        }
    }
    
    public static class Bacterium {
        public Schedule schedule;
        public double fitness;
        public double[] position;          // موقعیت در فضای جستجو (برای BFO)
        public double[] bestPosition;
        public double bestFitness;
        public List<Heuristic> heuristicSequence;
        
        public Bacterium(int dim) {
            position = new double[dim];
            bestPosition = new double[dim];
            heuristicSequence = new ArrayList<>();
            schedule = new Schedule();
            fitness = Double.MAX_VALUE;
            bestFitness = Double.MAX_VALUE;
        }
        
        public Bacterium copy() {
            Bacterium copy = new Bacterium(position.length);
            copy.position = this.position.clone();
            copy.bestPosition = this.bestPosition.clone();
            copy.fitness = this.fitness;
            copy.bestFitness = this.bestFitness;
            copy.schedule = this.schedule.copy();
            copy.heuristicSequence = new ArrayList<>(this.heuristicSequence);
            return copy;
        }
    }
    
    public BFO_HH(int dimension, FitnessFunction fitnessFunction) {
        this.dimension = dimension;
        this.fitnessFunction = fitnessFunction;
    }
    
    @Override
    public Schedule schedule(List<PriorityAwareCloudlet> cloudlets, List<Vm> vms) {
        // زمان‌بندی با BFO-HH کامل
        Bacterium best = optimize(cloudlets, vms);
        return (best != null) ? best.schedule : new Schedule();
    }
    
    // ======================== پیاده‌سازی کامل BFO ========================
    public Bacterium optimize(List<PriorityAwareCloudlet> cloudlets, List<Vm> vms) {
        int numTasks = cloudlets.size();
        int numVms = vms.size();
        if (numVms == 0) return null;
        
        // 1. مقداردهی اولیه جمعیت
        List<Bacterium> population = new ArrayList<>();
        for (int i = 0; i < populationSize; i++) {
            Bacterium b = new Bacterium(dimension);
            // موقعیت تصادفی در فضای [0,1]^dimension
            for (int d = 0; d < dimension; d++) {
                b.position[d] = random.nextDouble();
            }
            // ساخت schedule از روی موقعیت (نگاشت به تخصیص تسک به VM)
            b.schedule = decodeSchedule(b.position, cloudlets, vms);
            b.fitness = calculateScheduleFitness(b.schedule, cloudlets, vms);
            b.bestPosition = b.position.clone();
            b.bestFitness = b.fitness;
            population.add(b);
        }
        
        Bacterium globalBest = population.get(0).copy();
        for (Bacterium b : population) {
            if (b.fitness < globalBest.fitness) {
                globalBest = b.copy();
            }
        }
        
        // 2. حلقه‌های اصلی BFO
        for (int l = 0; l < eliminationDispersalEvents; l++) {
            for (int k = 0; k < reproductionSteps; k++) {
                for (int j = 0; j < chemotaxisSteps; j++) {
                    // مرحله Chemotaxis
                    for (int i = 0; i < populationSize; i++) {
                        Bacterium b = population.get(i);
                        // Tumble: تولید جهت تصادفی
                        double[] delta = new double[dimension];
                        double norm = 0;
                        for (int d = 0; d < dimension; d++) {
                            delta[d] = 2 * random.nextDouble() - 1;
                            norm += delta[d] * delta[d];
                        }
                        norm = Math.sqrt(norm);
                        for (int d = 0; d < dimension; d++) {
                            delta[d] /= norm;
                        }
                        // حرکت در جهت تصادفی
                        double[] newPos = b.position.clone();
                        for (int d = 0; d < dimension; d++) {
                            newPos[d] += stepSize * delta[d];
                            newPos[d] = Math.max(0, Math.min(1, newPos[d])); // گیره در [0,1]
                        }
                        
                        // ارزیابی موقعیت جدید
                        Schedule newSchedule = decodeSchedule(newPos, cloudlets, vms);
                        double newFitness = calculateScheduleFitness(newSchedule, cloudlets, vms);
                        
                        // Swim (تا زمانی که بهبود یابد یا به Ns برسد)
                        int swimCount = 0;
                        while (newFitness < b.fitness && swimCount < swimLength) {
                            // ادامه در همان جهت
                            for (int d = 0; d < dimension; d++) {
                                newPos[d] += stepSize * delta[d];
                                newPos[d] = Math.max(0, Math.min(1, newPos[d]));
                            }
                            newSchedule = decodeSchedule(newPos, cloudlets, vms);
                            newFitness = calculateScheduleFitness(newSchedule, cloudlets, vms);
                            swimCount++;
                        }
                        
                        // به‌روزرسانی باکتری
                        if (newFitness < b.fitness) {
                            b.position = newPos.clone();
                            b.schedule = newSchedule.copy();
                            b.fitness = newFitness;
                            if (b.fitness < b.bestFitness) {
                                b.bestPosition = b.position.clone();
                                b.bestFitness = b.fitness;
                            }
                        }
                    }
                    
                    // به‌روزرسانی بهترین سراسری پس از هر chemotaxis step
                    for (Bacterium b : population) {
                        if (b.fitness < globalBest.fitness) {
                            globalBest = b.copy();
                        }
                    }
                }
                
                // مرحله Reproduction
                Collections.sort(population, Comparator.comparingDouble(b -> b.fitness));
                int half = populationSize / 2;
                // نیمی از باکتری‌های ضعیف‌تر حذف و با کپی از نیمی قوی‌تر جایگزین می‌شوند
                for (int i = half; i < populationSize; i++) {
                    Bacterium parent = population.get(i - half);
                    Bacterium child = parent.copy();
                    // جهش کوچک برای تنوع
                    for (int d = 0; d < dimension; d++) {
                        if (random.nextDouble() < 0.1) {
                            child.position[d] = Math.random();
                        }
                    }
                    child.schedule = decodeSchedule(child.position, cloudlets, vms);
                    child.fitness = calculateScheduleFitness(child.schedule, cloudlets, vms);
                    population.set(i, child);
                }
                
                // به‌روزرسانی بهترین سراسری پس از reproduction
                for (Bacterium b : population) {
                    if (b.fitness < globalBest.fitness) {
                        globalBest = b.copy();
                    }
                }
            }
            
            // مرحله Elimination-Dispersal
            for (int i = 0; i < populationSize; i++) {
                if (random.nextDouble() < eliminationProbability) {
                    Bacterium b = population.get(i);
                    // بازنشانی تصادفی
                    for (int d = 0; d < dimension; d++) {
                        b.position[d] = random.nextDouble();
                    }
                    b.schedule = decodeSchedule(b.position, cloudlets, vms);
                    b.fitness = calculateScheduleFitness(b.schedule, cloudlets, vms);
                    if (b.fitness < b.bestFitness) {
                        b.bestPosition = b.position.clone();
                        b.bestFitness = b.fitness;
                    }
                }
            }
            
            // به‌روزرسانی بهترین سراسری پس از elimination-dispersal
            for (Bacterium b : population) {
                if (b.fitness < globalBest.fitness) {
                    globalBest = b.copy();
                }
            }
        }
        
        // استخراج توالی هیوریستیک‌ها از best position
        globalBest.heuristicSequence = decodeHeuristicSequence(globalBest.position);
        return globalBest;
    }
    
    // ======================== دیکود کردن موقعیت به Schedule ========================
    private Schedule decodeSchedule(double[] position, List<PriorityAwareCloudlet> cloudlets, List<Vm> vms) {
        Schedule schedule = new Schedule();
        int numTasks = cloudlets.size();
        int numVms = vms.size();
        for (int i = 0; i < numTasks; i++) {
            int vmIdx = (int) (position[i % dimension] * numVms);
            if (vmIdx >= numVms) vmIdx = numVms - 1;
            PriorityAwareCloudlet cl = cloudlets.get(i);
            schedule.assign(cl.getCloudletId(), vms.get(vmIdx).getId());
        }
        return schedule;
    }
    
    // ======================== دیکود کردن موقعیت به توالی هیوریستیک ========================
    private List<Heuristic> decodeHeuristicSequence(double[] position) {
        List<Heuristic> seq = new ArrayList<>();
        int numSteps = 2 + (int)(position[0] * 4); // 2 تا 5 مرحله
        for (int i = 0; i < numSteps && i < dimension; i++) {
            int idx = (int)(position[i] * 4);
            if (idx >= 4) idx = 3;
            seq.add(Heuristic.values()[idx]);
        }
        return seq;
    }
    
    // ======================== محاسبه Fitness (با انرژی و نرمال‌سازی) ========================
    private double calculateScheduleFitness(Schedule schedule, List<PriorityAwareCloudlet> cloudlets, List<Vm> vms) {
        double totalCost = 0, maxFinishTime = 0, totalPenalty = 0, totalEnergy = 0;
        int valid = 0;
        
        for (Map.Entry<Integer, Integer> entry : schedule.getAllAssignments().entrySet()) {
            PriorityAwareCloudlet cl = findCloudletById(cloudlets, entry.getKey());
            Vm vm = findVmById(vms, entry.getValue());
            if (cl != null && vm != null) {
                double execTime = (double) cl.getCloudletLength() / vm.getMips();
                totalCost += execTime * (0.05 + vm.getMips() / 20000.0);
                maxFinishTime = Math.max(maxFinishTime, execTime);
                if (execTime > cl.getDeadline()) {
                    totalPenalty += (execTime - cl.getDeadline()) * getPriorityWeight(cl.getPriority());
                }
                // محاسبه انرژی با مدل خطی
                double utilization = 0.8; // می‌توان دقیق‌تر از workload تخمین زد
                double power = DatacenterBuilder.calculatePower(utilization);
                totalEnergy += power * execTime;
                valid++;
            }
        }
        if (valid == 0) return Double.MAX_VALUE;
        
        // نرمال‌سازی ساده با استفاده از ماکزیمم‌های تخمینی (در عمل از min-max استفاده کنید)
        double normCost = totalCost / 1000.0;
        double normMakespan = maxFinishTime / 1000.0;
        double normEnergy = totalEnergy / 100000.0;
        double normPenalty = totalPenalty / 500.0;
        
        return 0.25 * normCost + 0.25 * normMakespan + 0.25 * normEnergy + 0.25 * normPenalty;
    }
    
    private double getPriorityWeight(int priority) {
        switch (priority) {
            case 1: return 3.0;
            case 2: return 1.5;
            default: return 1.0;
        }
    }
    
    private PriorityAwareCloudlet findCloudletById(List<PriorityAwareCloudlet> cloudlets, int id) {
        for (PriorityAwareCloudlet c : cloudlets) if (c.getCloudletId() == id) return c;
        return null;
    }
    
    private Vm findVmById(List<Vm> vms, int id) {
        for (Vm v : vms) if (v.getId() == id) return v;
        return null;
    }
    
    // ======================== استخراج state (برای DQN) ========================
    private double[] extractState(List<PriorityAwareCloudlet> cloudlets, List<Vm> vms) {
        int m = vms.size();
        int q = cloudlets.size();
        double[] state = new double[3*m + 3*q];
        int idx = 0;
        for (Vm vm : vms) {
            double util = (double) vm.getCurrentRequestedTotalMips() / vm.getMips();
            state[idx++] = Math.min(1.0, Math.max(0.0, util));
        }
        for (Vm vm : vms) {
            int workload = 1 + (int)(Math.random() * 10); // تخمینی
            state[idx++] = Math.min(1.0, workload / 100.0);
        }
        for (Vm vm : vms) {
            double util = (double) vm.getCurrentRequestedTotalMips() / vm.getMips();
            double power = DatacenterBuilder.calculatePower(util);
            state[idx++] = power / DatacenterBuilder.MAX_POWER;
        }
        for (PriorityAwareCloudlet cl : cloudlets) {
            double estTime = (double) cl.getCloudletLength() / 1000.0;
            double deadline = cl.getDeadline();
            double proximity = deadline > 0 ? (deadline - estTime) / deadline : 0;
            state[idx++] = Math.min(1.0, Math.max(0.0, proximity));
        }
        for (PriorityAwareCloudlet cl : cloudlets) {
            state[idx++] = cl.getPriority() / 3.0;
        }
        for (PriorityAwareCloudlet cl : cloudlets) {
            double remaining = (double) cl.getCloudletLength() / 1000.0;
            state[idx++] = Math.min(1.0, remaining / 10000.0);
        }
        return state;
    }
    
    // ======================== جمع‌آوری transitions برای DQN ========================
    public void collectRealTransitions(List<PriorityAwareCloudlet> cloudlets, List<Vm> vms, int numIterations) {
        System.out.println("🔄 Collecting real transitions from BFO-HH...");
        List<double[]> allStates = new ArrayList<>();
        List<Integer> allActions = new ArrayList<>();
        List<Double> allRewards = new ArrayList<>();
        List<double[]> allNextStates = new ArrayList<>();
        List<Integer> allDones = new ArrayList<>();
        
        for (int iter = 0; iter < numIterations; iter++) {
            if (iter % 500 == 0) System.out.println("  Iteration " + iter + "/" + numIterations);
            
            // اجرای BFO-HW کامل برای یافتن بهترین schedule و توالی هیوریستیک‌ها
            Bacterium best = optimize(cloudlets, vms);
            if (best == null || best.schedule == null) continue;
            
            List<Heuristic> heuristics = best.heuristicSequence;
            if (heuristics.isEmpty()) {
                // اگر توالی خالی بود، یک توالی تصادفی تولید کن
                int numSteps = 2 + random.nextInt(4);
                for (int i = 0; i < numSteps; i++) {
                    heuristics.add(Heuristic.values()[random.nextInt(4)]);
                }
            }
            
            double[] prevState = extractState(cloudlets, vms);
            double prevFitness = calculateScheduleFitness(best.schedule, cloudlets, vms);
            Schedule currentSchedule = best.schedule.copy();
            
            // برای هر مرحله از توالی هیوریستیک، یک transition ذخیره کن
            for (int step = 0; step < heuristics.size(); step++) {
                Heuristic h = heuristics.get(step);
                int action = getActionFromHeuristic(h);
                
                // اعمال هیوریستیک روی schedule فعلی
                Schedule newSchedule = applyHeuristic(currentSchedule, h, cloudlets, vms);
                double newFitness = calculateScheduleFitness(newSchedule, cloudlets, vms);
                
                // محاسبه reward با جریمه SLA
                double deltaFitness = newFitness - prevFitness;
                double slaPenalty = 0;
                // محاسبه جریمه بر اساس نقض deadline تسک‌ها
                for (PriorityAwareCloudlet cl : cloudlets) {
                    int vmId = newSchedule.getVmForCloudlet(cl.getCloudletId());
                    if (vmId != -1) {
                        Vm vm = findVmById(vms, vmId);
                        if (vm != null) {
                            double execTime = (double) cl.getCloudletLength() / vm.getMips();
                            if (execTime > cl.getDeadline()) {
                                slaPenalty += getPriorityWeight(cl.getPriority()) * (execTime - cl.getDeadline());
                            }
                        }
                    }
                }
                double reward = -deltaFitness - slaPenalty;
                
                double[] nextState = extractState(cloudlets, vms);
                allStates.add(prevState);
                allActions.add(action);
                allRewards.add(reward);
                allNextStates.add(nextState);
                allDones.add((step == heuristics.size()-1) ? 1 : 0);
                
                prevState = nextState;
                prevFitness = newFitness;
                currentSchedule = newSchedule.copy();
            }
        }
        
        String filePath = System.getProperty("user.dir") + File.separator + "real_transitions.csv";
        saveTransitionsToCSV(allStates, allActions, allRewards, allNextStates, allDones, filePath);
        System.out.println("✅ Real transitions saved to: " + filePath);
    }
    
    // ======================== اعمال هیوریستیک روی Schedule ========================
    private Schedule applyHeuristic(Schedule schedule, Heuristic h, List<PriorityAwareCloudlet> cloudlets, List<Vm> vms) {
        Schedule newSchedule = schedule.copy();
        int numTasks = cloudlets.size();
        int numVms = vms.size();
        if (numVms == 0) return newSchedule;
        
        switch (h) {
            case SELECT:
                // انتخاب یک تسک تصادفی و اختصاص به بهترین VM
                int taskIdx = random.nextInt(numTasks);
                PriorityAwareCloudlet cl = cloudlets.get(taskIdx);
                int bestVm = -1;
                double bestTime = Double.MAX_VALUE;
                for (Vm vm : vms) {
                    double time = (double) cl.getCloudletLength() / vm.getMips();
                    if (time < bestTime) {
                        bestTime = time;
                        bestVm = vm.getId();
                    }
                }
                if (bestVm != -1) {
                    newSchedule.assign(cl.getCloudletId(), bestVm);
                }
                break;
                
            case MOVE:
                Map<Integer, Integer> moved = heuristicOperator.move(newSchedule.getAllAssignments(), cloudlets, vms);
                newSchedule.getAllAssignments().clear();
                newSchedule.getAllAssignments().putAll(moved);
                break;
                
            case SWAP:
                Map<Integer, Integer> swapped = heuristicOperator.swap(newSchedule.getAllAssignments(), cloudlets, vms);
                newSchedule.getAllAssignments().clear();
                newSchedule.getAllAssignments().putAll(swapped);
                break;
                
            case DROP:
                Map<Integer, Integer> dropped = heuristicOperator.drop(newSchedule.getAllAssignments(), cloudlets, vms);
                newSchedule.getAllAssignments().clear();
                newSchedule.getAllAssignments().putAll(dropped);
                break;
        }
        return newSchedule;
    }
    
    // ======================== ذخیره CSV ========================
    private void saveTransitionsToCSV(List<double[]> states, List<Integer> actions, 
                                       List<Double> rewards, List<double[]> nextStates, 
                                       List<Integer> dones, String filename) {
        try (PrintWriter writer = new PrintWriter(new File(filename))) {
            int dim = states.get(0).length;
            for (int i = 0; i < dim; i++) writer.print("s" + i + ",");
            writer.print("action,");
            for (int i = 0; i < dim; i++) writer.print("ns" + i + ",");
            writer.print("reward,done\n");
            for (int i = 0; i < states.size(); i++) {
                for (double v : states.get(i)) writer.printf("%.6f,", v);
                writer.print(actions.get(i) + ",");
                for (double v : nextStates.get(i)) writer.printf("%.6f,", v);
                writer.printf("%.6f,%d\n", rewards.get(i), dones.get(i));
            }
            System.out.println("✅ Transitions saved to: " + filename);
        } catch (IOException e) {
            System.err.println("❌ Error saving transitions: " + e.getMessage());
        }
    }
    
    private int getActionFromHeuristic(Heuristic h) {
        switch (h) {
            case SELECT: return 0;
            case MOVE:   return 1;
            case SWAP:   return 2;
            case DROP:   return 3;
            default:     return 0;
        }
    }
    
    @Override
    public String getName() {
        return "BFO-HH";
    }
}