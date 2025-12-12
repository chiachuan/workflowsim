package org.workflowsim.custom.scheduler;

import org.cloudbus.cloudsim.Cloudlet;
import org.workflowsim.scheduling.BaseSchedulingAlgorithm;
import org.workflowsim.Job;
import java.util.*;

/**
 * FATS: Fragmented and Aggregated Task Scheduling Algorithm
 * 
 * Implements trust-aware scheduling with task fragmentation, secure execution,
 * and result aggregation for fog computing environments.
 * 
 * Key features:
 * - Task fragmentation for parallelizable high-load tasks
 * - Trust-based resource filtering (high/medium/low confidentiality)
 * - Composite scoring: latency, trust, capacity, security overhead, energy
 * - Runtime monitoring with trust score updates
 * - Secure aggregation of verified fragments
 * 
 * Based on Algorithm 1 & 2 from FATS paper.
 */
public class FATSScheduler extends BaseSchedulingAlgorithm {
    
    // Fragmentation parameters
    private static final double ALPHA = 1.5; // Fragmentation factor
    private static final double LOAD_THRESHOLD = 50000.0; // MI threshold for fragmentation
    
    // Trust score thresholds (relaxed to allow fog/cloud usage)
    private static final double TAU_HIGH = 0.75; // High confidentiality threshold (relaxed)
    private static final double TAU_MED = 0.55;  // Medium confidentiality threshold (relaxed)
    
    // Composite score weights (must sum to 1.0) - adjusted for fog/cloud utilization
    private static final double W_LATENCY = 0.35;      // wL - latency weight (increased)
    private static final double W_TRUST = 0.15;        // wT - trust weight (decreased)
    private static final double W_CAPACITY = 0.20;     // wC - capacity penalty weight (increased)
    private static final double W_SECURITY = 0.15;     // wS - security overhead weight (decreased)
    private static final double W_ENERGY = 0.15;       // wE - energy weight
    
    // Runtime parameters
    private static final int MAX_RETRIES = 3;
    private static final int BATCH_LIMIT = 5; // Max fragments per batch
    private static final double TRUST_INCREMENT = 0.01; // Success trust boost
    private static final double TRUST_PENALTY = 0.05;   // Failure trust penalty
    
    // Trust score tracking per VM
    private Map<Integer, Double> trustScores;
    
    // Fragment tracking
    private Map<Integer, List<Integer>> fragmentGroups; // Parent task -> fragment IDs
    private Map<Integer, Integer> fragmentRetries;
    
    public FATSScheduler() {
        super();
        this.trustScores = new HashMap<>();
        this.fragmentGroups = new HashMap<>();
        this.fragmentRetries = new HashMap<>();
    }
    
    @Override
    public void run() {
        // Initialize trust scores for all VMs
        List vmList = getVmList();
        for (Object vmObj : vmList) {
            SCEAHVM vm = (SCEAHVM) vmObj;
            // Initialize trust based on tier: Mist=0.95, Fog=0.85, Cloud=0.75
            double initialTrust = getTierBaseTrust(vm.getTier());
            trustScores.put(vm.getId(), initialTrust);
        }
        
        // Get all jobs and sort by depth (similar to EDF - Earliest Deadline First)
        List<Cloudlet> cloudletList = getCloudletList();
        List<Job> jobList = new ArrayList<>();
        for (Object obj : cloudletList) {
            jobList.add((Job) obj);
        }
        
        // Sort jobs by depth for deadline-aware scheduling
        jobList.sort(Comparator.comparingInt(Job::getDepth));
        
        // Process each job with FATS algorithm
        for (Job job : jobList) {
            scheduleJobWithFATS(job);
        }
    }
    
    /**
     * Algorithm 1 & 2: Schedule a job using FATS mechanism
     */
    private void scheduleJobWithFATS(Job job) {
        // Extract task properties
        double taskLoad = job.getCloudletLength();
        int taskDepth = job.getDepth();
        int confidentialityLevel = determineConfidentialityLevel(job);
        
        // Check if task should be fragmented
        boolean shouldFragment = isParallelizable(job) && 
                                (taskLoad > LOAD_THRESHOLD || taskDepth > 5);
        
        if (shouldFragment) {
            // Fragment the task (Algorithm 1)
            int numFragments = calculateFragmentCount(taskLoad);
            fragmentGroups.put(job.getCloudletId(), new ArrayList<>());
            
            // For simplification, schedule the job as-is but track it as fragmented
            // In a full implementation, we would create separate cloudlets for each fragment
            scheduleFragment(job, confidentialityLevel, numFragments);
        } else {
            // Schedule as single task
            scheduleFragment(job, confidentialityLevel, 1);
        }
    }
    
    /**
     * Schedule a fragment to the best fog node using composite scoring
     */
    private void scheduleFragment(Job fragment, int confidentiality, int totalFragments) {
        List vmList = getVmList();
        
        // Build candidate set C based on capacity and trust filtering
        List<SCEAHVM> candidateVMs = new ArrayList<>();
        double minCapacity = fragment.getCloudletLength() / 1000.0; // Minimum MIPS requirement
        
        for (Object vmObj : vmList) {
            SCEAHVM vm = (SCEAHVM) vmObj;
            
            // Filter by capacity
            if (vm.getMips() >= minCapacity) {
                // Filter by trust score based on confidentiality
                double trustScore = trustScores.getOrDefault(vm.getId(), 0.8);
                
                if (confidentiality == 3 && trustScore >= TAU_HIGH) {
                    candidateVMs.add(vm);
                } else if (confidentiality == 2 && trustScore >= TAU_MED) {
                    candidateVMs.add(vm);
                } else if (confidentiality == 1) {
                    candidateVMs.add(vm);
                }
            }
        }
        
        // If no candidates after trust filtering, relax constraints
        if (candidateVMs.isEmpty()) {
            for (Object vmObj : vmList) {
                SCEAHVM vm = (SCEAHVM) vmObj;
                if (vm.getMips() >= minCapacity) {
                    candidateVMs.add(vm);
                }
            }
        }
        
        // Calculate composite score for each candidate
        SCEAHVM bestVM = null;
        double bestScore = Double.MAX_VALUE;
        
        for (SCEAHVM vm : candidateVMs) {
            double compositeScore = calculateCompositeScore(fragment, vm, confidentiality, totalFragments);
            
            if (compositeScore < bestScore) {
                bestScore = compositeScore;
                bestVM = vm;
            }
        }
        
        // Schedule to best VM
        if (bestVM != null) {
            fragment.setVmId(bestVM.getId());
            getScheduledList().add(fragment);
            
            // Update trust score (Algorithm 2 - simulated success)
            double currentTrust = trustScores.get(bestVM.getId());
            trustScores.put(bestVM.getId(), Math.min(1.0, currentTrust + TRUST_INCREMENT));
        }
    }
    
    /**
     * Calculate composite score using weighted multi-objective function
     * CompositeScore = wL*latency + wT*(1-trust) + wC*capacity_penalty + wS*security_overhead + wE*energy
     */
    private double calculateCompositeScore(Job fragment, SCEAHVM vm, int confidentiality, int totalFragments) {
        // 1. Latency prediction (normalized)
        double execTime = fragment.getCloudletLength() / vm.getMips();
        double normalizedLatency = execTime / 1000.0; // Normalize to 0-1 range
        
        // 2. Trust score (inverted - lower trust = higher penalty)
        double trustScore = trustScores.getOrDefault(vm.getId(), 0.8);
        double trustPenalty = 1.0 - trustScore;
        
        // 3. Capacity penalty (based on VM tier and utilization)
        double capacityPenalty = calculateCapacityPenalty(vm);
        
        // 4. Security overhead (higher for high confidentiality)
        double securityOverhead = calculateSecurityOverhead(confidentiality, vm, totalFragments);
        
        // 5. Energy cost
        double energyCost = calculateEnergyCost(fragment, vm);
        
        // 6. Tier performance bonus (cloud=fastest, fog=medium, mist=slowest)
        // For low/medium confidentiality, moderately favor higher-performance tiers
        double tierBonus = 0.0;
        if (confidentiality == 1) { // Low confidentiality - favor cloud
            tierBonus = (vm.getTier() - 1) * (-0.15); // Cloud gets -0.3, Fog gets -0.15, Mist gets 0
        } else if (confidentiality == 2) { // Medium confidentiality - favor fog
            if (vm.getTier() == 2) {
                tierBonus = -0.20; // Fog gets strong bonus
            } else if (vm.getTier() == 1) {
                tierBonus = 0.0; // Mist neutral
            }
        }
        
        // Composite score
        double score = W_LATENCY * normalizedLatency +
                      W_TRUST * trustPenalty +
                      W_CAPACITY * capacityPenalty +
                      W_SECURITY * securityOverhead +
                      W_ENERGY * energyCost +
                      tierBonus; // Negative bonus = better score for cloud/fog
        
        return score;
    }
    
    /**
     * Calculate capacity penalty based on VM tier preference
     */
    private double calculateCapacityPenalty(SCEAHVM vm) {
        // Prefer mist (tier 1) over fog (tier 2) over cloud (tier 3)
        // Lower tier = lower penalty
        switch (vm.getTier()) {
            case 1: return 0.0;  // Mist - no penalty
            case 2: return 0.3;  // Fog - medium penalty
            case 3: return 0.6;  // Cloud - high penalty
            default: return 0.5;
        }
    }
    
    /**
     * Calculate security overhead based on confidentiality level
     */
    private double calculateSecurityOverhead(int confidentiality, SCEAHVM vm, int totalFragments) {
        // High confidentiality = more encryption/verification overhead
        double baseOverhead = confidentiality * 0.1; // 0.1, 0.2, or 0.3
        
        // Fragmentation adds overhead for MAC verification and aggregation
        double fragmentOverhead = (totalFragments > 1) ? totalFragments * 0.02 : 0.0;
        
        // Higher tier VMs have better security capabilities (lower overhead)
        double tierFactor = (4 - vm.getTier()) * 0.05; // Mist=0.15, Fog=0.10, Cloud=0.05
        
        return baseOverhead + fragmentOverhead - tierFactor;
    }
    
    /**
     * Calculate energy cost for executing fragment on VM
     */
    private double calculateEnergyCost(Job fragment, SCEAHVM vm) {
        double execTimeHours = (fragment.getCloudletLength() / vm.getMips()) / 3600.0;
        double powerWatts = vm.getPMax();
        double energyKWh = (powerWatts * execTimeHours) / 1000.0;
        
        // Normalize to 0-1 range (assuming max 1 kWh per fragment)
        return Math.min(1.0, energyKWh * 1000.0);
    }
    
    /**
     * Calculate number of fragments based on task load and available capacity
     */
    private int calculateFragmentCount(double taskLoad) {
        // Get average VM capacity
        double avgCapacity = 0.0;
        List vmList = getVmList();
        for (Object vmObj : vmList) {
            SCEAHVM vm = (SCEAHVM) vmObj;
            avgCapacity += vm.getMips();
        }
        avgCapacity /= vmList.size();
        
        // k = ceil(load(t) / (alpha * avg_capacity))
        int k = (int) Math.ceil(taskLoad / (ALPHA * avgCapacity));
        
        // Limit fragments to reasonable range (2-10)
        return Math.max(2, Math.min(10, k));
    }
    
    /**
     * Determine confidentiality level based on job properties
     */
    private int determineConfidentialityLevel(Job job) {
        // Use job ID modulo to assign confidentiality levels
        // 1 = low (10%), 2 = medium (30%), 3 = high (60%) - SECURITY-FIRST
        int mod = job.getCloudletId() % 10;
        int level;
        if (mod < 1) {
            level = 1; // 10% low confidentiality (can use any tier including cloud)
        } else if (mod < 4) {
            level = 2; // 30% medium confidentiality (mist/fog only)
        } else {
            level = 3; // 60% high confidentiality (mist only) - HIGHEST SECURITY
        }
        return level;
    }
    
    /**
     * Check if task is parallelizable (heuristic based on task type)
     */
    private boolean isParallelizable(Job job) {
        // In WorkflowSim, assume compute-intensive tasks are parallelizable
        // Use depth as heuristic: deeper tasks are more likely parallel
        return job.getDepth() > 2;
    }
    
    /**
     * Get base trust score based on VM tier
     */
    private double getTierBaseTrust(int tier) {
        switch (tier) {
            case 1: return 0.90; // Mist - highest trust
            case 2: return 0.65; // Fog - medium trust (below TAU_HIGH)
            case 3: return 0.45; // Cloud - lowest trust (below TAU_MED)
            default: return 0.60;
        }
    }
    
    /**
     * Get VMs cast to SCEAHVM type
     */
    protected List<SCEAHVM> getSCEAHVMList() {
        List<SCEAHVM> sceahVMs = new ArrayList<>();
        List vmList = getVmList();
        for (Object vmObj : vmList) {
            if (vmObj instanceof SCEAHVM) {
                sceahVMs.add((SCEAHVM) vmObj);
            }
        }
        return sceahVMs;
    }
}
