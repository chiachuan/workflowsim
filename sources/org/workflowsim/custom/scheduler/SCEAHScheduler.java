package org.workflowsim.custom.scheduler;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;

import org.workflowsim.Job;
import org.workflowsim.SecureReliableTask; 
import org.workflowsim.Task;
import org.workflowsim.WorkflowSimTags;
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

        List<Cloudlet> cloudletList = getCloudletList(); 
        List<Vm> vmList = getVmList(); 

        List<Job> jobList = new ArrayList<>();
        List<SCEAHVM> sceahVmList = new ArrayList<>();

        // Convert cloudlets to Jobs
        for (Cloudlet cl : cloudletList) {
            if (cl instanceof Job) {
                jobList.add((Job) cl);
            }
        }

        // Filter for SCEAHVM instances
        for (Vm vm : vmList) {
             if (vm instanceof SCEAHVM) {
                 sceahVmList.add((SCEAHVM) vm);
             }
        }
        
        if (jobList.isEmpty() || sceahVmList.isEmpty()) {
            return;
        }

        // Sort jobs by depth (topological order)
        Collections.sort(jobList, new Comparator<Job>() {
            @Override
            public int compare(Job j1, Job j2) {
                return Integer.compare(j1.getDepth(), j2.getDepth());
            }
        });

        // Simple SCEAH scheduling: assign each job to a SCEAH VM
        for (Job job : jobList) {
            SCEAHVM bestVm = null;
            double minFinishTime = Double.MAX_VALUE;

            for (SCEAHVM vm : sceahVmList) {
                if (vm.getState() == WorkflowSimTags.VM_STATUS_IDLE) {
                    double execTime = job.getCloudletLength() / vm.getMips();
                    if (execTime < minFinishTime) {
                        minFinishTime = execTime;
                        bestVm = vm;
                    }
                }
            }

            if (bestVm != null) {
                job.setVmId(bestVm.getId());
                bestVm.setState(WorkflowSimTags.VM_STATUS_BUSY);
                getScheduledList().add(job);
            }
        }
    }
}