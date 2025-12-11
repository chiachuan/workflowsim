package org.workflowsim.custom.scheduler;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;

import org.workflowsim.Job;
import org.workflowsim.SecureReliableTask; 
import org.workflowsim.Task;
import org.workflowsim.scheduling.BaseSchedulingAlgorithm;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim; 
import org.cloudbus.cloudsim.Cloudlet; 
import org.cloudbus.cloudsim.Log; 
import org.workflowsim.custom.scheduler.SCEAHVM; // <-- Required Import


public class SCEAHScheduler extends BaseSchedulingAlgorithm {

    private static final double CHI = 5.0; // Cost tolerance margin (χ)
    private static final double PSI = 5.0; // Energy tolerance margin (ψ)
    
    public SCEAHScheduler() {
        super();
    }
    
    // --- Helper Methods (assuming correct implementation) ---
    private double calculateTaskVolume(SecureReliableTask task) {
        if (task.getNumberOfPes() > 0) {
            return (double) task.getCloudletLength() / task.getNumberOfPes();
        }
        return (double) task.getCloudletLength();
    }
    
    private double calculateComp(SecureReliableTask task, SCEAHVM vm) {
        return calculateTaskVolume(task) / vm.getMips(); 
    }
    
    private double getEstimatedDataTransferTime(SecureReliableTask task, SCEAHVM vm) {
        long fileSize = task.getCloudletFileSize(); 
        double vmBw = vm.getBw();
        double fileSizeInBits = (double) fileSize * 8.0; 
        
        if (vmBw > 0) {
            return fileSizeInBits / vmBw;
        }
        return 0.0;
    }
    
    private double calculateEFT(SecureReliableTask task, SCEAHVM vm) {
        double dataTransferTime = getEstimatedDataTransferTime(task, vm);
        double eft = vm.getCloudletScheduler().cloudletSubmit(task, dataTransferTime); 
        return eft;
    }
    
    // ------------------------------------------------------------------

    @Override
    public void run() {

        List<Cloudlet> genericCloudletList = getCloudletList(); 
        List<Vm> vmList = getVmList(); 

        List<SecureReliableTask> readyTaskList = new ArrayList<>();
        List<SCEAHVM> sceahVmList = new ArrayList<>();

        // --- DEBUG LOG 1: Check what the engine passed to the scheduler ---
        Log.printLine("LOG 1 (Scheduler Start): Received " + genericCloudletList.size() + " potential tasks from engine.");

        // 2. Populate the readyTaskList (Type Check & Task Extraction FIX APPLIED HERE)
        for (Cloudlet cl : genericCloudletList) {
            
            // Skip the implicit final task 100 which is often just a plain Cloudlet/Job
            if (cl.getCloudletId() == 100) {
                Log.printLine("LOG 2 (Task Skip): Skipping implicit final task (ID 100).");
                continue;
            }

            SecureReliableTask currentTask = null;
            
            // WorkflowSim wraps Tasks inside a Job object (which is a Cloudlet)
            if (cl instanceof Job) {
                Job job = (Job) cl;
                
                // The Task list within the Job should contain your converted SecureReliableTask
                if (!job.getTaskList().isEmpty()) {
                    
                    Task firstTask = job.getTaskList().get(0);
                    
                    // The fix is here: Cast the Task only after a successful check
                    if (firstTask instanceof SecureReliableTask) {
                        currentTask = (SecureReliableTask) firstTask; 
                        // Log.printLine("LOG 2 (Task Cast): Successfully extracted SecureReliableTask " + currentTask.getCloudletId() + " from Job wrapper.");
                    } else {
                        // The original log statement was correct for debugging
                        Log.printLine("LOG 2 (Task Fail): Job " + job.getCloudletId() + " contains a Task that is NOT SecureReliableTask. Type: " + firstTask.getClass().getSimpleName());
                    }
                } else {
                    Log.printLine("LOG 2 (Task Fail): Job " + job.getCloudletId() + " has an empty task list.");
                }
            } 
            // Fallback for direct Cloudlet submission
            else if (cl instanceof SecureReliableTask) {
                currentTask = (SecureReliableTask) cl;
                // Log.printLine("LOG 2 (Task Cast): Successfully cast Cloudlet " + currentTask.getCloudletId() + " directly to SecureReliableTask.");
            } 
            else {
                Log.printLine("LOG 2 (Task Fail): Cloudlet " + cl.getCloudletId() + " is neither a Job nor a SecureReliableTask. Type: " + cl.getClass().getSimpleName());
            }

            if (currentTask != null) {
                readyTaskList.add(currentTask); 
            }
        }

        // 3. Populate SCEAHVM list
        for (Vm vm : vmList) {
             if (vm instanceof SCEAHVM) {
                 sceahVmList.add((SCEAHVM) vm);
             }
        }
        
        // --- DEBUG LOG 4: Check final lists before quitting ---
        Log.printLine("LOG 4 (Pre-Check): Final readyTaskList size: " + readyTaskList.size());
        Log.printLine("LOG 4 (Pre-Check): Final sceahVmList size: " + sceahVmList.size());


        if (readyTaskList.isEmpty() || sceahVmList.isEmpty()) {
            Log.printLine("Warning: Scheduler found 0 ready tasks or 0 SCEAHVMs. Returning.");
            return;
        }


        // --- 4.1 Task Selection Phase (Sorting) ---
        Collections.sort(readyTaskList, new Comparator<SecureReliableTask>() { 
            @Override
            public int compare(SecureReliableTask a, SecureReliableTask b) {
                // Assuming getDeadline and getLevel are implemented in SecureReliableTask
                int deadlineCompare = Double.compare(a.getDeadline(), b.getDeadline());
                if (deadlineCompare != 0) return deadlineCompare;

                int levelCompare = Double.compare(b.getLevel(), a.getLevel()); 
                if (levelCompare != 0) return levelCompare;

                return Double.compare(calculateTaskVolume(b), calculateTaskVolume(a));
            }
        });

        // --- Main Scheduling Loop ---
        while (!readyTaskList.isEmpty()) { 
            SecureReliableTask currentTask = readyTaskList.remove(0); 
            
            SCEAHVM bestVm = null;
            double minEFT = Double.MAX_VALUE;
            List<SCEAHVM> vAppl = new ArrayList<>();
            int sl = currentTask.isSecure() ? 3 : 1; 
        

            // 4.2 VM Filtering Phase
            for (SCEAHVM vm : sceahVmList) {
                int vmTier = vm.getTier();
                if (sl == 1 || (sl == 3 && vmTier == 1)) { 
                    vAppl.add(vm);
                } 
            }
            
            // 4.3 VM Selection Phase (Initial EFT)
            for (SCEAHVM vm : vAppl) {
                double eft = calculateEFT(currentTask, vm); 
                if (eft < minEFT) {
                    minEFT = eft;
                    bestVm = vm;
                }
            }
            
            if (bestVm == null || minEFT == Double.MAX_VALUE) {
                Log.printLine("Warning: Could not find suitable VM for Task " + currentTask.getCloudletId());
                continue;
            }
            
            // 4.3.1 Cost Awareness Rule 
            SCEAHVM initialBestVm = bestVm;
            double initialMinEFT = minEFT;
            boolean isEFTToleranceUtilized = false;
            
            if (sl == 1 && initialBestVm.getTier() == 2) { 
                SCEAHVM secondBestMistFogVm = null;
                double secondMinEFT = Double.MAX_VALUE;
                
                for (SCEAHVM vm : vAppl) {
                    if (vm.getTier() == 1) { 
                        double eft = calculateEFT(currentTask, vm);
                        if (eft < secondMinEFT) {
                            secondMinEFT = eft;
                            secondBestMistFogVm = vm;
                        }
                    }
                }
                
                if (secondBestMistFogVm != null) {
                    double alpha = 100.0 * (secondMinEFT - initialMinEFT) / initialMinEFT;
                    if (alpha <= CHI) {
                        bestVm = secondBestMistFogVm; 
                        minEFT = secondMinEFT;
                        isEFTToleranceUtilized = true; 
                    }
                }
            }

            // 4.3.2 Energy Awareness Rule
            if (isEFTToleranceUtilized == false) { 
                SCEAHVM tierBestVm = bestVm;
                double tierMinEFT = minEFT;
                // vEligible is a list of VMs with lower power consumption in the same tier
                List<SCEAHVM> vEligible = new ArrayList<>();
                int bestVmTier = bestVm.getTier();

                for (SCEAHVM vm : sceahVmList) {
                    if (vm.getTier() == bestVmTier && vm.getPMax() < tierBestVm.getPMax()) {
                        
                        double eft_vm = calculateEFT(currentTask, vm);
                        double beta = 100.0 * (eft_vm - tierMinEFT) / tierMinEFT;
                        
                        if (beta <= PSI) {
                            vEligible.add(vm); 
                        }
                    }
                }
                
                if (!vEligible.isEmpty()) {
                    // Find the best VM among the eligible, power-saving ones based on EFT
                    SCEAHVM energyBestVm = null;
                    double energyMinEFT = Double.MAX_VALUE;
                    for(SCEAHVM vm : vEligible) {
                        double eft = calculateEFT(currentTask, vm);
                        if(eft < energyMinEFT) {
                            energyMinEFT = eft;
                            energyBestVm = vm;
                        }
                    }
                    if (energyBestVm != null) {
                        bestVm = energyBestVm;
                    }
                }
            }

            // Final Scheduling Decision
            if (bestVm != null) {
                currentTask.setVmId(bestVm.getId());
                getScheduledList().add(currentTask);
            }
        }
        
        // Final debug log
        Log.printLine("LOG 5 (Scheduler Finish): Scheduled " + getScheduledList().size() + " tasks.");
    }
}