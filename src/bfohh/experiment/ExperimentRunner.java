package bfohh.experiment;

import bfohh.algorithm.BFO_HH;
import bfohh.algorithm.FitnessFunction;
import bfohh.comparison.*;
import bfohh.cloud.*;
import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import java.util.*;
import java.io.FileWriter;
import java.io.PrintWriter;

public class ExperimentRunner {
    
    private static final int NUM_RUNS = 25;
    private static final int[] TASK_COUNTS = {20, 50, 100, 150, 200};
    
    private static final int VM_COUNT = 100;
    private static final int HOST_COUNT = 200;
    
    // ========== آرایه‌ی الگوریتم‌ها (حتماً شامل DQNScheduler باشد) ==========
    private SchedulingAlgorithm[] algorithms = {
        new BFO_HH(10, new FitnessFunction()),
        new GA(),
        new PSO(),
        new ACO(),
        new ABC(),
        new DQNScheduler()   // <-- این خط حتماً باشد
    };
    
    private Map<String, List<Double>> fitnessResults = new HashMap<>();
    private Map<String, List<Double>> makespanResults = new HashMap<>();
    private Map<String, List<Double>> energyResults = new HashMap<>();
    
    public void runExperiments() throws Exception {
        // ====== چاپ لیست الگوریتم‌ها برای دیباگ ======
        System.out.println("============================================================");
        System.out.println("📋 الگوریتم‌های بارگذاری شده:");
        for (SchedulingAlgorithm algo : algorithms) {
            System.out.println("   - " + algo.getName());
        }
        System.out.println("============================================================");
        
        System.out.println("شروع آزمایش‌های مقایسه‌ای با " + algorithms.length + " الگوریتم");
        System.out.println("============================================================");
        
        // مقداردهی اولیه
        for (SchedulingAlgorithm algo : algorithms) {
            fitnessResults.put(algo.getName(), new ArrayList<>());
            makespanResults.put(algo.getName(), new ArrayList<>());
            energyResults.put(algo.getName(), new ArrayList<>());
        }
        
        for (int taskCount : TASK_COUNTS) {
            System.out.println("\n============================================================");
            System.out.println("آزمایش با " + taskCount + " تسک");
            System.out.println("============================================================");
            
            // پاک کردن نتایج قبلی برای این taskCount
            for (SchedulingAlgorithm algo : algorithms) {
                fitnessResults.get(algo.getName()).clear();
                makespanResults.get(algo.getName()).clear();
                energyResults.get(algo.getName()).clear();
            }
            
            for (int run = 0; run < NUM_RUNS; run++) {
                System.out.print("اجرای " + (run+1) + " از " + NUM_RUNS + "... ");
                runSingleSimulation(taskCount);
                System.out.println("انجام شد");
            }
            
            printResults(taskCount);
        }
        
        System.out.println("\n============================================================");
        System.out.println("همه آزمایش‌ها با موفقیت انجام شد!");
        System.out.println("============================================================");
    }
    
    private void runSingleSimulation(int taskCount) {
        // ساخت VMها و Cloudletهای پایه
        List<Vm> baseVms = VmBuilder.createVms(0, VM_COUNT);
        List<PriorityAwareCloudlet> baseCloudlets = CloudletBuilder.createCloudlets(0, taskCount);
        
        for (SchedulingAlgorithm algo : algorithms) {
            try {
                // 1. ریست CloudSim برای هر الگوریتم
                CloudSim.init(1, Calendar.getInstance(), false);
                Datacenter datacenter = DatacenterBuilder.createDatacenter("DC_" + taskCount, HOST_COUNT);
                DatacenterBroker broker = new DatacenterBroker("Broker_" + algo.getName());
                int brokerId = broker.getId();
                
                List<Vm> vms = copyVmList(baseVms, brokerId);
                List<PriorityAwareCloudlet> cloudlets = copyCloudletList(baseCloudlets, brokerId);
                broker.submitVmList(vms);
                broker.submitCloudletList(new ArrayList<>(cloudlets));
                
                // 2. اجرای الگوریتم زمان‌بندی
                BFO_HH.Schedule schedule = algo.schedule(cloudlets, vms);
                if (schedule == null) {
                    System.err.println("  ❌ " + algo.getName() + " -> schedule null");
                    addBadResult(algo.getName());
                    continue;
                }
                
                // 3. اعمال Schedule روی cloudlet‌ها
                for (PriorityAwareCloudlet cl : cloudlets) {
                    int assignedVm = schedule.getVmForCloudlet(cl.getCloudletId());
                    if (assignedVm != -1) {
                        cl.setVmId(assignedVm);
                    }
                }
                
                // 4. اجرای شبیه‌سازی
                CloudSim.startSimulation();
                List<Cloudlet> resultList = broker.getCloudletReceivedList();
                CloudSim.stopSimulation();
                
                // 5. محاسبه متریک‌ها
                double makespan = calcMakespan(resultList);
                double energy = calcEnergy(resultList, vms);
                double fitness = calculateFitness(schedule, cloudlets, vms);
                
                // 6. ذخیره نتایج
                fitnessResults.get(algo.getName()).add(fitness);
                makespanResults.get(algo.getName()).add(makespan);
                energyResults.get(algo.getName()).add(energy);
                
                System.out.printf("  %s -> Fitness: %.4f, Makespan: %.2f, Energy: %.2f\n",
                                  algo.getName(), fitness, makespan, energy);
                
            } catch (Exception e) {
                System.err.println("  ❌ خطا در " + algo.getName() + ": " + e.getMessage());
                e.printStackTrace();
                addBadResult(algo.getName());
            }
        }
    }
    
    private void addBadResult(String algoName) {
        fitnessResults.get(algoName).add(Double.MAX_VALUE);
        makespanResults.get(algoName).add(Double.MAX_VALUE);
        energyResults.get(algoName).add(Double.MAX_VALUE);
    }
    
    // ==================== توابع کمکی ====================
    
    private List<Vm> copyVmList(List<Vm> source, int newBrokerId) {
        List<Vm> copy = new ArrayList<>();
        for (Vm vm : source) {
            Vm newVm = new Vm(vm.getId(), newBrokerId, vm.getMips(), vm.getNumberOfPes(),
                              vm.getRam(), vm.getBw(), vm.getSize(), vm.getVmm(),
                              new CloudletSchedulerTimeShared());
            copy.add(newVm);
        }
        return copy;
    }
    
    private List<PriorityAwareCloudlet> copyCloudletList(List<PriorityAwareCloudlet> source, int newUserId) {
        List<PriorityAwareCloudlet> copy = new ArrayList<>();
        for (PriorityAwareCloudlet cl : source) {
            PriorityAwareCloudlet newCl = new PriorityAwareCloudlet(
                cl.getCloudletId(), cl.getCloudletLength(), cl.getNumberOfPes(),
                cl.getCloudletFileSize(), cl.getCloudletOutputSize(),
                cl.getUtilizationModelCpu(), cl.getUtilizationModelRam(), cl.getUtilizationModelBw(),
                cl.getPriority(), cl.getDeadline()
            );
            newCl.setUserId(newUserId);
            copy.add(newCl);
        }
        return copy;
    }
    
    private double calcMakespan(List<Cloudlet> list) {
        double max = 0;
        for (Cloudlet c : list) {
            max = Math.max(max, c.getFinishTime());
        }
        return max;
    }
    
    private double calcEnergy(List<Cloudlet> list, List<Vm> vms) {
        double totalEnergy = 0;
        for (Cloudlet c : list) {
            Vm vm = findVmById(vms, c.getVmId());
            if (vm != null && c.getStatus() == Cloudlet.SUCCESS) {
                double execTime = c.getActualCPUTime();
                double start = c.getExecStartTime();
                double finish = c.getFinishTime();
                double utilization = (finish > start) ? execTime / (finish - start) : 0.5;
                if (Double.isNaN(utilization)) utilization = 0.5;
                double power = DatacenterBuilder.calculatePower(utilization);
                totalEnergy += power * execTime;
            }
        }
        return totalEnergy;
    }
    
    private Vm findVmById(List<Vm> vms, int id) {
        for (Vm v : vms) if (v.getId() == id) return v;
        return null;
    }
    
    // ========== Fitness با انرژی، هزینه، makespan و جریمه SLA ==========
    private double calculateFitness(BFO_HH.Schedule schedule, List<PriorityAwareCloudlet> cloudlets, List<Vm> vms) {
        double totalCost = 0, maxFinishTime = 0, totalPenalty = 0, totalEnergy = 0;
        int validTasks = 0;
        
        for (Map.Entry<Integer, Integer> entry : schedule.getAllAssignments().entrySet()) {
            PriorityAwareCloudlet cloudlet = findCloudletById(cloudlets, entry.getKey());
            Vm vm = findVmById(vms, entry.getValue());
            
            if (cloudlet != null && vm != null) {
                double execTime = (double) cloudlet.getCloudletLength() / vm.getMips();
                totalCost += execTime * (0.05 + vm.getMips() / 20000.0);
                maxFinishTime = Math.max(maxFinishTime, execTime);
                if (execTime > cloudlet.getDeadline()) {
                    totalPenalty += (execTime - cloudlet.getDeadline()) * getPriorityWeight(cloudlet.getPriority());
                }
                // انرژی با مدل خطی
                double utilization = 0.8; // تخمین ساده
                double power = DatacenterBuilder.calculatePower(utilization);
                totalEnergy += power * execTime;
                validTasks++;
            }
        }
        if (validTasks == 0) return Double.MAX_VALUE;
        
        // نرمال‌سازی ساده
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
    
    private void printResults(int taskCount) throws Exception {
        // اضافه کردن timestamp به نام فایل
        String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
        String filename = "comparison_results_" + taskCount + "_" + timestamp + ".txt";
        PrintWriter writer = new PrintWriter(new FileWriter(filename));
        
        writer.println("نتایج برای " + taskCount + " تسک (تکرار = " + NUM_RUNS + ")\n");
        
        writer.println("Fitness:");
        for (SchedulingAlgorithm algo : algorithms) {
            String name = algo.getName();
            List<Double> values = fitnessResults.get(name);
            double mean = calculateMean(values);
            double std = calculateStdDev(values, mean);
            writer.printf("%s: %.4f ± %.4f\n", name, mean, std);
        }
        
        writer.println("\nMakespan:");
        for (SchedulingAlgorithm algo : algorithms) {
            String name = algo.getName();
            List<Double> values = makespanResults.get(name);
            double mean = calculateMean(values);
            double std = calculateStdDev(values, mean);
            writer.printf("%s: %.4f ± %.4f\n", name, mean, std);
        }
        
        writer.println("\nEnergy:");
        for (SchedulingAlgorithm algo : algorithms) {
            String name = algo.getName();
            List<Double> values = energyResults.get(name);
            double mean = calculateMean(values);
            double std = calculateStdDev(values, mean);
            writer.printf("%s: %.2f ± %.2f\n", name, mean, std);
        }
        
        writer.close();
        System.out.println("✅ نتایج در " + filename + " ذخیره شد");
    }
    
    private double calculateMean(List<Double> values) {
        if (values.isEmpty()) return 0;
        double sum = 0;
        for (double v : values) sum += v;
        return sum / values.size();
    }
    
    private double calculateStdDev(List<Double> values, double mean) {
        if (values.size() < 2) return 0;
        double sum = 0;
        for (double v : values) sum += Math.pow(v - mean, 2);
        return Math.sqrt(sum / (values.size() - 1));
    }
    
    public static void main(String[] args) {
        ExperimentRunner runner = new ExperimentRunner();
        try {
            runner.runExperiments();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}