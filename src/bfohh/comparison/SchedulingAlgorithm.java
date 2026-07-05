package bfohh.comparison;

import bfohh.algorithm.BFO_HH;
import bfohh.cloud.PriorityAwareCloudlet;
import org.cloudbus.cloudsim.Vm;
import java.util.List;

public interface SchedulingAlgorithm {
    /**
     * اجرای الگوریتم زمان‌بندی
     * @param cloudlets لیست تسک‌ها
     * @param vms لیست ماشین‌های مجازی
     * @return Schedule نهایی
     */
    BFO_HH.Schedule schedule(List<PriorityAwareCloudlet> cloudlets, List<Vm> vms);
    
    /**
     * @return نام الگوریتم
     */
    String getName();
}