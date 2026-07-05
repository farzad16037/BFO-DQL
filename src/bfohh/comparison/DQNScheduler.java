package bfohh.comparison;

import bfohh.algorithm.BFO_HH;
import bfohh.algorithm.HeuristicOperator;
import bfohh.cloud.PriorityAwareCloudlet;
import bfohh.cloud.DatacenterBuilder;
import org.cloudbus.cloudsim.Vm;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class DQNScheduler implements SchedulingAlgorithm {
    
    private static final String BASE_DIR = "C:\\Users\\np\\eclipse-workspace\\BFO_HH_CloudScheduler";
    private static final String PYTHON_PATH = "python";
    private static final String SCRIPT_PATH = BASE_DIR + "\\dqn_predict_batch.py";
    
    private Random random = new Random();
    private HeuristicOperator op = new HeuristicOperator();
    
    @Override
    public BFO_HH.Schedule schedule(List<PriorityAwareCloudlet> cloudlets, List<Vm> vms) {
        try {
            int numTasks = cloudlets.size();
            String modelPath = getModelPath(numTasks);
            if (modelPath == null) {
                System.err.println("⚠️ مدل برای " + numTasks + " تسک یافت نشد → Fallback تصادفی");
                return randomSchedule(cloudlets, vms);
            }
            
            BFO_HH.Schedule currentSchedule = new BFO_HH.Schedule();
            for (PriorityAwareCloudlet cl : cloudlets) {
                currentSchedule.assign(cl.getCloudletId(), random.nextInt(vms.size()));
            }
            
            List<PriorityAwareCloudlet> pendingTasks = new ArrayList<>(cloudlets);
            Collections.shuffle(pendingTasks, random);
            
            for (PriorityAwareCloudlet targetTask : pendingTasks) {
                double[] stateArray = extractFullState(cloudlets, vms, currentSchedule);
                String stateLine = stateToCSV(stateArray);
                
                long ts = System.currentTimeMillis() + random.nextInt(10000);
                String tempInput = BASE_DIR + "\\temp_state_" + ts + ".txt";
                String tempOutput = BASE_DIR + "\\temp_action_" + ts + ".txt";
                
                Files.write(Paths.get(tempInput), Collections.singletonList(stateLine));
                
                ProcessBuilder pb = new ProcessBuilder(
                    PYTHON_PATH,
                    SCRIPT_PATH,
                    tempInput,
                    tempOutput,
                    modelPath
                );
                pb.directory(new File(BASE_DIR));
                pb.redirectErrorStream(false);
                
                // خاموش کردن اخطارهای oneDNN TensorFlow
                pb.environment().put("TF_ENABLE_ONEDNN_OPTS", "0");
                
                Process p = pb.start();
                
                BufferedReader stdOut = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                while ((line = stdOut.readLine()) != null) {
                    System.out.println("[Python] " + line);
                }
                
                BufferedReader stdErr = new BufferedReader(new InputStreamReader(p.getErrorStream()));
                while ((line = stdErr.readLine()) != null) {
                    System.err.println("[Python ERR] " + line);
                }
                
                int exitCode = p.waitFor();
                if (exitCode != 0) {
                    throw new IOException("Python failed with exit code " + exitCode);
                }
                
                List<String> actionLines = Files.readAllLines(Paths.get(tempOutput));
                if (actionLines.isEmpty()) {
                    throw new IOException("خروجی خالی است");
                }
                int action = Integer.parseInt(actionLines.get(0).trim());
                
                switch (action) {
                    case 0:
                        int bestVm = selectBestVm(targetTask, vms);
                        currentSchedule.assign(targetTask.getCloudletId(), bestVm);
                        break;
                    case 1:
                        Map<Integer, Integer> moved = op.move(currentSchedule.getAllAssignments(), cloudlets, vms);
                        currentSchedule.getAllAssignments().clear();
                        currentSchedule.getAllAssignments().putAll(moved);
                        break;
                    case 2:
                        Map<Integer, Integer> swapped = op.swap(currentSchedule.getAllAssignments(), cloudlets, vms);
                        currentSchedule.getAllAssignments().clear();
                        currentSchedule.getAllAssignments().putAll(swapped);
                        break;
                    case 3:
                        Map<Integer, Integer> dropped = op.drop(currentSchedule.getAllAssignments(), cloudlets, vms);
                        currentSchedule.getAllAssignments().clear();
                        currentSchedule.getAllAssignments().putAll(dropped);
                        break;
                }
                
                new File(tempInput).delete();
                new File(tempOutput).delete();
            }
            return currentSchedule;
            
        } catch (Exception e) {
            System.err.println("❌ CRITICAL ERROR in DQNScheduler: " + e.getMessage());
            e.printStackTrace();
            return randomSchedule(cloudlets, vms);
        }
    }
    
    private String getModelPath(int numTasks) {
        switch (numTasks) {
            case 20:  return BASE_DIR + "\\dqn_model_20.h5";
            case 50:  return BASE_DIR + "\\dqn_model_50.h5";
            case 100: return BASE_DIR + "\\dqn_model_100.h5";
            case 150: return BASE_DIR + "\\dqn_model_150.h5";
            case 200: return BASE_DIR + "\\dqn_model_200.h5";
            default:  return null;
        }
    }
    
    private BFO_HH.Schedule randomSchedule(List<PriorityAwareCloudlet> cloudlets, List<Vm> vms) {
        BFO_HH.Schedule s = new BFO_HH.Schedule();
        for (PriorityAwareCloudlet cl : cloudlets) {
            s.assign(cl.getCloudletId(), random.nextInt(vms.size()));
        }
        return s;
    }
    
    private int selectBestVm(PriorityAwareCloudlet cl, List<Vm> vms) {
        int bestId = -1;
        double bestTime = Double.MAX_VALUE;
        for (Vm vm : vms) {
            double time = (double) cl.getCloudletLength() / vm.getMips();
            if (time < bestTime) {
                bestTime = time;
                bestId = vm.getId();
            }
        }
        return bestId;
    }
    
    private double[] extractFullState(List<PriorityAwareCloudlet> cloudlets, List<Vm> vms, BFO_HH.Schedule schedule) {
        int m = vms.size();
        int q = cloudlets.size();
        double[] state = new double[3*m + 3*q];
        int idx = 0;
        for (Vm vm : vms) {
            double util = (double) vm.getCurrentRequestedTotalMips() / vm.getMips();
            state[idx++] = Math.min(1.0, Math.max(0.0, util));
        }
        for (Vm vm : vms) {
            int workload = 0;
            for (int cid : schedule.getAllAssignments().keySet()) {
                if (schedule.getVmForCloudlet(cid) == vm.getId()) workload++;
            }
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
    
    private String stateToCSV(double[] state) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < state.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(state[i]);
        }
        return sb.toString();
    }
    
    @Override
    public String getName() {
        return "BFO-DQL";
    }
}