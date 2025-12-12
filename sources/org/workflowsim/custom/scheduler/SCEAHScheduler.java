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

    // SCEAH Algorithm Parameters (from paper)
    private static final double CHI = 5.0; // Cost tolerance margin (χ) - for cost awareness
    private static final double PSI = 5.0; // Energy tolerance margin (ψ) - for energy awareness
    
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

        List<Job> readyJobs = new ArrayList<>();
        List<SCEAHVM> mistVMs = new ArrayList<>();
        List<SCEAHVM> fogVMs = new ArrayList<>();
        List<SCEAHVM> cloudVMs = new ArrayList<>();

        // Extract jobs from cloudlet list
        for (Cloudlet cl : cloudletList) {
            if (cl instanceof Job) {
                readyJobs.add((Job) cl);
            }
        }

        // Categorize VMs by tier
        for (Vm vm : vmList) {
            if (vm instanceof SCEAHVM) {
                SCEAHVM sceahVM = (SCEAHVM) vm;
                int tier = sceahVM.getTier();
                if (tier == 1) {
                    mistVMs.add(sceahVM);
                } else if (tier == 2) {
                    fogVMs.add(sceahVM);
                } else {
                    cloudVMs.add(sceahVM);
                }
            }
        }

        if (readyJobs.isEmpty() || (mistVMs.isEmpty() && fogVMs.isEmpty() && cloudVMs.isEmpty())) {
            return;
        }

        // ========== PHASE 1: TASK SELECTION ==========
        // Sort ready tasks by: 1) deadline (earliest first), 2) level (highest first), 3) comp cost (highest first)
        Collections.sort(readyJobs, new Comparator<Job>() {
            @Override
            public int compare(Job j1, Job j2) {
                // Priority 1: Deadline (earliest deadline = highest priority)
                // Jobs inherit deadline from their parent workflow, using depth as proxy
                int depthCompare = Integer.compare(j1.getDepth(), j2.getDepth());
                if (depthCompare != 0) return depthCompare;
                
                // Priority 2: Level (highest level = highest priority) - using depth as inverse level
                // In WorkflowSim, depth increases as we go deeper, so reverse for level
                int levelCompare = Integer.compare(j2.getDepth(), j1.getDepth());
                if (levelCompare != 0) return levelCompare;
                
                // Priority 3: Computational cost (highest cost = highest priority)
                return Long.compare(j2.getCloudletLength(), j1.getCloudletLength());
            }
        });

        // ========== PROCESS EACH READY TASK ==========
        for (Job currentJob : readyJobs) {
            
            // ========== PHASE 2: VM FILTERING - SECURITY AWARENESS ==========
            List<SCEAHVM> applicableVMs = new ArrayList<>();
            
            // Determine security level (using job depth as proxy for security level)
            // SL=1 (low security): all VMs, SL=2 (medium): mist+fog, SL=3 (high): mist only
            int securityLevel = determineSecurityLevel(currentJob);
            
            if (securityLevel == 1) {
                // SL=1: Can use mist, fog, or cloud VMs
                applicableVMs.addAll(mistVMs);
                applicableVMs.addAll(fogVMs);
                applicableVMs.addAll(cloudVMs);
            } else if (securityLevel == 2) {
                // SL=2: Can use only mist and fog VMs
                applicableVMs.addAll(mistVMs);
                applicableVMs.addAll(fogVMs);
            } else {
                // SL=3: Can use only mist VMs
                applicableVMs.addAll(mistVMs);
            }

            if (applicableVMs.isEmpty()) {
                continue; // No applicable VMs for this security level
            }

            // ========== PHASE 3: VM SELECTION ==========
            
            // Calculate EFT for all applicable VMs
            SCEAHVM bestVM = null;
            double minEFT = Double.MAX_VALUE;
            
            for (SCEAHVM vm : applicableVMs) {
                if (vm.getState() == WorkflowSimTags.VM_STATUS_IDLE) {
                    double eft = calculateEFT(currentJob, vm);
                    if (eft < minEFT) {
                        minEFT = eft;
                        bestVM = vm;
                    }
                }
            }

            if (bestVM == null) {
                continue; // No idle VM available
            }

            // ========== COST AWARENESS RULE ==========
            boolean costToleranceUtilized = false;
            
            if (securityLevel == 1 && bestVM.getTier() == 3) {
                // Best VM is cloud VM - check if mist/fog VM is within cost tolerance
                SCEAHVM secondBestMistFogVM = null;
                double secondMinEFT = Double.MAX_VALUE;
                
                // Find best mist or fog VM
                for (SCEAHVM vm : applicableVMs) {
                    if ((vm.getTier() == 1 || vm.getTier() == 2) && vm.getState() == WorkflowSimTags.VM_STATUS_IDLE) {
                        double eft = calculateEFT(currentJob, vm);
                        if (eft < secondMinEFT) {
                            secondMinEFT = eft;
                            secondBestMistFogVM = vm;
                        }
                    }
                }
                
                if (secondBestMistFogVM != null) {
                    // Calculate percentage increase α
                    double alpha = 100.0 * (secondMinEFT - minEFT) / minEFT;
                    
                    if (alpha <= CHI) {
                        // Within cost tolerance - use mist/fog VM instead of cloud
                        bestVM = secondBestMistFogVM;
                        minEFT = secondMinEFT;
                        costToleranceUtilized = true;
                    }
                }
            }

            // ========== ENERGY AWARENESS RULE ==========
            if (!costToleranceUtilized) {
                // Find VMs in same tier with lower power consumption
                List<SCEAHVM> eligibleVMs = new ArrayList<>();
                int bestVMTier = bestVM.getTier();
                double bestVMPMax = bestVM.getPMax();
                
                List<SCEAHVM> sameTierVMs = new ArrayList<>();
                if (bestVMTier == 1) sameTierVMs.addAll(mistVMs);
                else if (bestVMTier == 2) sameTierVMs.addAll(fogVMs);
                else sameTierVMs.addAll(cloudVMs);
                
                for (SCEAHVM vm : sameTierVMs) {
                    if (vm.getPMax() < bestVMPMax && vm.getState() == WorkflowSimTags.VM_STATUS_IDLE) {
                        double eft = calculateEFT(currentJob, vm);
                        double beta = 100.0 * (eft - minEFT) / minEFT;
                        
                        if (beta <= PSI) {
                            eligibleVMs.add(vm);
                        }
                    }
                }
                
                // Select VM with earliest EFT from eligible VMs
                if (!eligibleVMs.isEmpty()) {
                    SCEAHVM energyBestVM = null;
                    double energyMinEFT = Double.MAX_VALUE;
                    
                    for (SCEAHVM vm : eligibleVMs) {
                        double eft = calculateEFT(currentJob, vm);
                        if (eft < energyMinEFT) {
                            energyMinEFT = eft;
                            energyBestVM = vm;
                        }
                    }
                    
                    if (energyBestVM != null) {
                        bestVM = energyBestVM;
                    }
                }
            }

            // ========== FINAL ASSIGNMENT ==========
            if (bestVM != null) {
                currentJob.setVmId(bestVM.getId());
                bestVM.setState(WorkflowSimTags.VM_STATUS_BUSY);
                getScheduledList().add(currentJob);
            }
        }
    }

    /**
     * Determine security level of a job (using job ID modulo 3 + 1 as heuristic)
     * SL=1 (low): can use any tier, SL=2 (medium): mist+fog only, SL=3 (high): mist only
     */
    private int determineSecurityLevel(Job job) {
        // Simple heuristic: use job ID to determine security level (1, 2, or 3)
        int sl = (job.getCloudletId() % 3) + 1;
        return sl;
    }

    /**
     * Calculate Earliest Finish Time for a job on a VM
     */
    private double calculateEFT(Job job, SCEAHVM vm) {
        // Simplified EFT calculation: execution time on the VM
        double execTime = (double) job.getCloudletLength() / vm.getMips();
        return execTime;
    }
}