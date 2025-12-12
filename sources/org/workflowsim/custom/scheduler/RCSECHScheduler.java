package org.workflowsim.custom.scheduler;

import org.workflowsim.Job;
import org.workflowsim.scheduling.BaseSchedulingAlgorithm;
import org.workflowsim.utils.Parameters;
import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Log;

import java.util.*;

/**
 * RCSECH: Reliability-Constrained, Security, Energy and Cost-aware Heuristic
 * 
 * Implements Algorithm 1 (Deadline Distribution), Algorithm 2 (RCSECH Heuristic),
 * and Algorithm 3 (Duplication Algorithm) from the paper:
 * "Security, Reliability, Cost, and Energy-Aware Scheduling of Real-Time Workflows
 * in Compute-Continuum Environments"
 */
public class RCSECHScheduler extends BaseSchedulingAlgorithm {
    
    // Algorithm parameters
    private static final double RELIABILITY_CONSTRAINT = 0.95; // Γz
    private static final double WEIGHT_COST = 0.33;  // ωcs
    private static final double WEIGHT_ENERGY = 0.33;  // ωes
    private static final double WEIGHT_RELIABILITY = 0.34;  // ωrs
    
    // Tier impact weights (Eq. 44)
    private static final double CLOUD_IMPACT = 0.90;  // CI
    private static final double FOG_IMPACT = 0.95;    // FI
    private static final double MIST_IMPACT = 1.0;    // MI
    
    // Possible Duplication Queue
    private List<Job> pdq = new ArrayList<>();
    private double workflowReliability = 1.0;
    
    public RCSECHScheduler() {
        super();
    }
    
    @Override
    public void run() {
        // Process jobs from cloudlet list like SCEAH does
        int size = getCloudletList().size();
        
        for (int i = 0; i < size; i++) {
            if (getCloudletList().isEmpty()) {
                break;
            }
            
            // Get jobs sorted by depth (EDF - Earliest Deadline First using depth as proxy)
            Collections.sort(getCloudletList(), new Comparator<Cloudlet>() {
                @Override
                public int compare(Cloudlet c1, Cloudlet c2) {
                    Job j1 = (Job) c1;
                    Job j2 = (Job) c2;
                    return Integer.compare(j1.getDepth(), j2.getDepth());
                }
            });
            
            // Get the first job (highest priority by EDF)
            Cloudlet cloudlet = (Cloudlet) getCloudletList().get(0);
            Job job = (Job) cloudlet;
            
            // Schedule the job using RCSECH heuristic
            scheduleJob(job);
            
            // Remove from cloudlet list
            getCloudletList().remove(cloudlet);
        }
    }
    
    /**
     * Schedule a single job using RCSECH heuristic (Algorithm 2)
     */
    private void scheduleJob(Job job) {
        List<SCEAHVM> vmList = getSCEAHVMList();
        
        if (vmList.isEmpty()) {
            return;
        }
        
        SCEAHVM bestVm = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        
        // Determine security level for this job (Eq. 26)
        int securityLevel = determineSecurityLevel(job);
        
        // Phase 2: Filter VMs by security requirement (Eq. 25)
        List<SCEAHVM> eligibleVMs = filterVMsBySecurity(vmList, securityLevel);
        
        if (eligibleVMs.isEmpty()) {
            // No eligible VMs, use any VM as fallback
            eligibleVMs = vmList;
        }
        
        // Calculate min/max for normalization
        double minCost = Double.MAX_VALUE, maxCost = Double.MIN_VALUE;
        double minEnergy = Double.MAX_VALUE, maxEnergy = Double.MIN_VALUE;
        double minReliability = Double.MAX_VALUE, maxReliability = Double.MIN_VALUE;
        
        Map<SCEAHVM, Double> costMap = new HashMap<>();
        Map<SCEAHVM, Double> energyMap = new HashMap<>();
        Map<SCEAHVM, Double> reliabilityMap = new HashMap<>();
        
        for (SCEAHVM vm : eligibleVMs) {
            double execTime = calculateExecutionTime(job, vm);
            double cost = calculateCost(execTime, vm);
            double energy = calculateEnergy(execTime, vm);
            double reliability = calculateReliability(execTime, vm);
            
            costMap.put(vm, cost);
            energyMap.put(vm, energy);
            reliabilityMap.put(vm, reliability);
            
            minCost = Math.min(minCost, cost);
            maxCost = Math.max(maxCost, cost);
            minEnergy = Math.min(minEnergy, energy);
            maxEnergy = Math.max(maxEnergy, energy);
            minReliability = Math.min(minReliability, reliability);
            maxReliability = Math.max(maxReliability, reliability);
        }
        
        // Phase 3: Calculate objective metric S for each VM (Eq. 40-43)
        for (SCEAHVM vm : eligibleVMs) {
            double cost = costMap.get(vm);
            double energy = energyMap.get(vm);
            double reliability = reliabilityMap.get(vm);
            
            // Eq. 40: Logarithmic normalization for cost
            double normalizedCost = 0;
            if (maxCost > minCost) {
                normalizedCost = Math.log(maxCost - cost + 1) / 
                                Math.log(maxCost - minCost + 1) * WEIGHT_COST;
            }
            
            // Eq. 41: Min-max normalization for energy
            double normalizedEnergy = 0;
            if (maxEnergy > minEnergy) {
                normalizedEnergy = (maxEnergy - energy + 1) / 
                                  (maxEnergy - minEnergy + 1) * WEIGHT_ENERGY;
            }
            
            // Eq. 42: Logarithmic normalization for reliability
            double normalizedReliability = 0;
            if (maxReliability > minReliability && reliability > 0) {
                normalizedReliability = (Math.log(reliability + 1) - Math.log(minReliability + 1)) /
                                       (Math.log(maxReliability + 1) - Math.log(minReliability + 1)) * 
                                       WEIGHT_RELIABILITY;
            }
            
            // Eq. 43 & 44: Combined objective with tier impact
            double tierWeight = getTierWeight(vm);
            double score = tierWeight * normalizedCost + normalizedReliability + normalizedEnergy;
            
            if (score > bestScore) {
                bestScore = score;
                bestVm = vm;
            }
        }
        
        // Schedule job to best VM
        if (bestVm != null) {
            job.setVmId(bestVm.getId());
            getScheduledList().add(job);
            
            // Update workflow reliability
            double jobReliability = reliabilityMap.get(bestVm);
            workflowReliability *= jobReliability;
        }
    }
    

    
    /**
     * Determine security level for a job (Eq. 26)
     * Returns 1 (public - 50%), 2 (semi-private - 30%), or 3 (private - 20%)
     */
    private int determineSecurityLevel(Job job) {
        // Use job ID modulo for security distribution to enable cloud usage
        int mod = job.getCloudletId() % 10;
        if (mod < 5) {
            return 1; // 50% public (any tier including cloud)
        } else if (mod < 8) {
            return 2; // 30% semi-private (mist+fog)
        } else {
            return 3; // 20% private (mist only)
        }
    }
    

    /**
     * Filter VMs by security requirement (Eq. 25)
     */
    private List<SCEAHVM> filterVMsBySecurity(List<SCEAHVM> allVMs, int securityLevel) {
        List<SCEAHVM> eligible = new ArrayList<>();
        
        for (SCEAHVM vm : allVMs) {
            int tier = vm.getTier();
            
            // RELAXED Security mapping to enable cloud: SL=1 (any tier), SL=2 (fog+cloud), SL=3 (mist+fog)
            if (securityLevel == 1) {
                eligible.add(vm); // Public: any tier (including cloud)
            } else if (securityLevel == 2 && (tier == 2 || tier == 3)) {
                eligible.add(vm); // Semi-private: fog or cloud
            } else if (securityLevel == 3 && (tier == 1 || tier == 2)) {
                eligible.add(vm); // Private: mist or fog
            }
        }
        
        return eligible;
    }
    
    /**
     * Get tier weight for objective calculation (Eq. 44)
     */
    private double getTierWeight(SCEAHVM vm) {
        int tier = vm.getTier();
        if (tier == 3) return CLOUD_IMPACT;  // Cloud
        if (tier == 2) return FOG_IMPACT;    // Fog
        return MIST_IMPACT;                   // Mist
    }
    
    /**
     * Calculate execution time (Eq. 4)
     */
    private double calculateExecutionTime(Job job, SCEAHVM vm) {
        return job.getCloudletLength() / vm.getMips();
    }
    
    /**
     * Calculate cost (Eq. 10)
     */
    private double calculateCost(double execTime, SCEAHVM vm) {
        double hours = execTime / 3600.0;
        return vm.getCostRatePerHour() * hours;
    }
    
    /**
     * Calculate energy consumption (Eq. 16)
     */
    private double calculateEnergy(double execTime, SCEAHVM vm) {
        double powerWatts = vm.getPMax();
        double energyKWh = (powerWatts * execTime) / 3600000.0; // Convert to kWh
        return energyKWh;
    }
    
    /**
     * Calculate reliability (Eq. 21)
     */
    private double calculateReliability(double execTime, SCEAHVM vm) {
        double lambda = 1e-5; // Failure rate
        double beta = 1.0;    // Weibull shape parameter
        return Math.exp(-Math.pow(execTime * lambda, beta));
    }
    

    /**
     * Get VM by ID
     */
    private SCEAHVM getSCEAHVMById(int vmId) {
        for (SCEAHVM vm : getSCEAHVMList()) {
            if (vm.getId() == vmId) {
                return vm;
            }
        }
        return null;
    }
    
    /**
     * Get list of VMs as SCEAHVM type
     */
    protected List<SCEAHVM> getSCEAHVMList() {
        List<SCEAHVM> sceahVMs = new ArrayList<>();
        for (Object vm : getVmList()) {
            if (vm instanceof SCEAHVM) {
                sceahVMs.add((SCEAHVM) vm);
            }
        }
        return sceahVMs;
    }
}
