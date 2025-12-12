package org.workflowsim.examples.scheduling;

// CloudSim imports
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.CloudletSchedulerSpaceShared;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.Cloudlet;

// Host imports
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;

// WorkflowSim imports
import org.workflowsim.WorkflowDatacenter;
import org.workflowsim.Job;
import org.workflowsim.Task;
import org.workflowsim.WorkflowEngine;
import org.workflowsim.WorkflowPlanner;
import org.workflowsim.utils.ClusteringParameters;
import org.workflowsim.utils.OverheadParameters;
import org.workflowsim.utils.Parameters;
import org.workflowsim.utils.ReplicaCatalog;
import org.cloudbus.cloudsim.VmSchedulerSpaceShared;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;

// Java imports
import java.util.*;
import java.text.DecimalFormat;
import java.io.File;

// Custom imports
import org.workflowsim.custom.scheduler.SCEAHVM;
import org.workflowsim.SecureReliableTask;

/**
 * RCSECH Scheduling Algorithm Example
 * 
 * Demonstrates the RCSECH (Reliability-Constrained, Security, Energy and Cost-aware Heuristic)
 * scheduling algorithm for real-time workflows in compute-continuum environments.
 */
public class RCSECHSchedulingAlgorithmExample extends DataAwareSchedulingAlgorithmExample {

    private static final int VM_PES = 1;
    private static final int VM_RAM = 512;
    private static final long VM_BW = 1000;
    private static final long VM_SIZE = 10000;
    private static final String VM_VMM = "Xen";

    /**
     * Creates datacenter with sufficient resources for 5 VMs
     */
    protected static WorkflowDatacenter createDatacenter(String name) {
        
        List<Host> hostList = new ArrayList<>();
        
        // Host specifications to support 5 VMs
        int hostId = 0;
        int hostRam = 3072; // 3GB RAM
        long hostStorage = 1000000; // 1TB
        int hostBw = 10000; // 10 Gb/s
        
        // Create PEs (Processing Elements) - 5 cores at 5M MIPS each
        int mips = 5000000;
        List<Pe> peList = new ArrayList<>();
        
        // Create 5 PEs for the host
        peList.add(new Pe(0, new PeProvisionerSimple(mips)));
        peList.add(new Pe(1, new PeProvisionerSimple(mips)));
        peList.add(new Pe(2, new PeProvisionerSimple(mips)));
        peList.add(new Pe(3, new PeProvisionerSimple(mips)));
        peList.add(new Pe(4, new PeProvisionerSimple(mips)));
        
        Host host = new Host(
                hostId,
                new RamProvisionerSimple(hostRam),
                new BwProvisionerSimple(hostBw),
                hostStorage,
                peList,
                new VmSchedulerSpaceShared(peList)
        );
        
        hostList.add(host);
        
        // Datacenter characteristics
        String arch = "x86";
        String os = "Linux";
        String vmm = "Xen";
        double time_zone = 10.0;
        double cost = 3.0;
        double costPerMem = 0.05;
        double costPerStorage = 0.001;
        double costPerBw = 0.0;
        LinkedList<Storage> storageList = new LinkedList<>();
        
        DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
                arch, os, vmm, hostList, time_zone, cost, costPerMem,
                costPerStorage, costPerBw);
        
        WorkflowDatacenter datacenter = null;
        try {
            datacenter = new WorkflowDatacenter(name, characteristics,
                    new org.cloudbus.cloudsim.VmAllocationPolicySimple(hostList),
                    new LinkedList<Storage>(), 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return datacenter;
    }

    /**
     * Creates 5 VMs across 3 tiers (Mist, Fog, Cloud)
     * Tier 1 (Mist): 2 VMs - highest security, lowest cost/power
     * Tier 2 (Fog): 2 VMs - medium security, medium cost/power
     * Tier 3 (Cloud): 1 VM - lowest security, highest cost/power
     */
    private static List<SCEAHVM> createVM(int userId, int vms, int vmMips, int vmPes, int vmRam, long vmBw, long vmSize, String vmm) {
        List<SCEAHVM> vmList = new ArrayList<>();
        
        // Create 3-tier VM architecture
        // Tier 1: Mist VMs (2 VMs)
        for (int i = 0; i < 2; i++) {
            int tier = 1; // Mist tier
            int mips = 1000; // Lower MIPS for edge devices
            double costPerHour = 0.05; // Lower cost
            double tdp = 100.0; // Lower power consumption (Watts)
            int cores = 2;
            
            SCEAHVM vm = new SCEAHVM(
                    i,
                    userId,
                    mips,
                    vmPes,
                    vmRam,
                    vmBw,
                    vmSize,
                    vmm,
                    new CloudletSchedulerSpaceShared(),
                    tier,
                    costPerHour,
                    tdp,
                    cores
            );
            vmList.add(vm);
        }
        
        // Tier 2: Fog VMs (2 VMs)
        for (int i = 2; i < 4; i++) {
            int tier = 2; // Fog tier
            int mips = 2000; // Medium MIPS
            double costPerHour = 0.15; // Medium cost
            double tdp = 200.0; // Medium power consumption
            int cores = 4;
            
            SCEAHVM vm = new SCEAHVM(
                    i,
                    userId,
                    mips,
                    vmPes,
                    vmRam,
                    vmBw,
                    vmSize,
                    vmm,
                    new CloudletSchedulerSpaceShared(),
                    tier,
                    costPerHour,
                    tdp,
                    cores
            );
            vmList.add(vm);
        }
        
        // Tier 3: Cloud VM (1 VM)
        for (int i = 4; i < 5; i++) {
            int tier = 3; // Cloud tier
            int mips = 3000; // Highest MIPS
            double costPerHour = 0.30; // Highest cost
            double tdp = 300.0; // Highest power consumption
            int cores = 8;
            
            SCEAHVM vm = new SCEAHVM(
                    i,
                    userId,
                    mips,
                    vmPes,
                    vmRam,
                    vmBw,
                    vmSize,
                    vmm,
                    new CloudletSchedulerSpaceShared(),
                    tier,
                    costPerHour,
                    tdp,
                    cores
            );
            vmList.add(vm);
        }
        
        return vmList;
    }

    public static void main(String[] args) {
        try {
            // Configuration
            int vmNum = 5; // Total VMs across all tiers
            String daxPath = "config/dax/Montage_100.xml";
            
            // Verify DAX file exists
            File daxFile = new File(daxPath);
            if (!daxFile.exists()) {
                Log.printLine("Warning: DAX file not found at: " + daxPath);
            } else {
                Log.printLine("DAX file found at: " + daxFile.getAbsolutePath());
            }
            Parameters.SchedulingAlgorithm sch_method = Parameters.SchedulingAlgorithm.RCSECH;
            Parameters.PlanningAlgorithm pln_method = Parameters.PlanningAlgorithm.INVALID;
            ReplicaCatalog.FileSystem file_system = ReplicaCatalog.FileSystem.LOCAL;
            
            OverheadParameters op = new OverheadParameters(0, null, null, null, null, 0);
            ClusteringParameters.ClusteringMethod method = ClusteringParameters.ClusteringMethod.NONE;
            ClusteringParameters cp = new ClusteringParameters(0, 0, method, null);
            
            // Initialize parameters
            Parameters.init(vmNum, daxPath, null,
                    null, op, cp, sch_method, pln_method,
                    null, 0);
            ReplicaCatalog.init(file_system);
            
            // Initialize CloudSim
            int num_user = 1;
            Calendar calendar = Calendar.getInstance();
            boolean trace_flag = false;
            
            CloudSim.init(num_user, calendar, trace_flag);
            
            // Create datacenter
            WorkflowDatacenter datacenter0 = createDatacenter("Datacenter_0");
            
            WorkflowPlanner wfPlanner = new WorkflowPlanner("planner_0", 1);
            WorkflowEngine wfEngine = wfPlanner.getWorkflowEngine();
            
            // Parse workflow manually
            wfPlanner.getWorkflowParser().parse();
            List<Task> taskList = wfPlanner.getWorkflowParser().getTaskList();
            Log.printLine("Parsed " + taskList.size() + " tasks from DAX file");
            
            // Submit tasks as cloudlets
            wfEngine.submitCloudletList((List)taskList);
            
            // Create VMs with 3-tier architecture
            List<SCEAHVM> vmlist0 = createVM(wfEngine.getSchedulerId(0), Parameters.getVmNum(), 
                    1000, VM_PES, VM_RAM, VM_BW, VM_SIZE, VM_VMM);
            
            wfEngine.submitVmList((List)vmlist0, 0); 

            // Binds the data centers with the scheduler.
            wfEngine.bindSchedulerDatacenter(datacenter0.getId(), 0);

            // Start the simulation
            CloudSim.startSimulation();
            
            List<Job> outputList0 = wfEngine.getJobsReceivedList();
            
            // Stop the simulation
            CloudSim.stopSimulation();
            
            // Print job execution results
            printJobList(outputList0);
            
            // Calculate and print RCSECH metrics
            printRCSECHMetrics(outputList0, vmlist0);
            
        } catch (Exception e) {
            Log.printLine("Unwanted errors happen");
            e.printStackTrace();
        } 
    }
    
    /**
     * Calculate and print RCSECH algorithm metrics:
     * - Security compliance
     * - Energy consumption
     * - Deadline compliance
     * - Reliability (success rate and constraint satisfaction)
     * - Cost
     */
    private static void printRCSECHMetrics(List<Job> completedJobs, List<SCEAHVM> vms) {
        Log.printLine("\n========== RCSECH ALGORITHM METRICS ==========");
        
        int totalJobs = completedJobs.size();
        int successfulJobs = 0;
        int failedJobs = 0;
        
        // Security metrics
        int mistJobs = 0;  // Tier 1 (highest security)
        int fogJobs = 0;   // Tier 2 (medium security)
        int cloudJobs = 0; // Tier 3 (lowest security)
        
        // Energy consumption
        double totalEnergy = 0.0; // in kWh
        
        // Deadline compliance
        int jobsMetDeadline = 0;
        int jobsMissedDeadline = 0;
        
        // Cost tracking
        double totalCost = 0.0;
        
        // Reliability tracking
        double workflowReliability = 1.0;
        int reliabilityConstraintMet = 0;
        
        // Create VM lookup map
        java.util.Map<Integer, SCEAHVM> vmMap = new java.util.HashMap<>();
        for (SCEAHVM vm : vms) {
            vmMap.put(vm.getId(), vm);
        }
        
        for (Job job : completedJobs) {
            // Reliability: Count successful vs failed jobs
            if (job.getCloudletStatus() == Job.SUCCESS) {
                successfulJobs++;
            } else {
                failedJobs++;
            }
            
            int vmId = job.getVmId();
            SCEAHVM vm = vmMap.get(vmId);
            
            if (vm != null) {
                // Security: Track which tier executed the job
                int tier = vm.getTier();
                if (tier == 1) mistJobs++;
                else if (tier == 2) fogJobs++;
                else cloudJobs++;
                
                // Energy: Calculate energy consumption
                double executionTimeHours = job.getActualCPUTime() / 3600.0;
                double powerConsumption = vm.getPMax();
                double jobEnergy = (powerConsumption * executionTimeHours) / 1000.0;
                totalEnergy += jobEnergy;
                
                // Cost: Calculate monetary cost
                double costForJob = vm.getCostRatePerHour() * executionTimeHours;
                totalCost += costForJob;
                
                // Deadline: Check if job met its deadline
                double deadline = job.getDepth() * 50.0;
                if (job.getFinishTime() <= deadline) {
                    jobsMetDeadline++;
                } else {
                    jobsMissedDeadline++;
                }
                
                // Reliability calculation (Eq. 21)
                double lambda = 1e-5;
                double beta = 1.0;
                double jobReliability = Math.exp(-Math.pow(job.getActualCPUTime() * lambda, beta));
                workflowReliability *= jobReliability;
            }
        }
        
        // Check reliability constraint (0.95)
        if (workflowReliability >= 0.95) {
            reliabilityConstraintMet = 1;
        }
        
        // Print metrics
        Log.printLine("\n--- Reliability Metrics ---");
        Log.printLine("Total Jobs: " + totalJobs);
        Log.printLine("Successful Jobs: " + successfulJobs);
        Log.printLine("Failed Jobs: " + failedJobs);
        Log.printLine("Success Rate: " + String.format("%.2f%%", (successfulJobs * 100.0 / totalJobs)));
        Log.printLine("Workflow Reliability: " + String.format("%.4f", workflowReliability));
        Log.printLine("Reliability Constraint (0.95) Met: " + (reliabilityConstraintMet == 1 ? "YES" : "NO"));
        Log.printLine("Reliability Success Rate (RSR): " + String.format("%.2f%%", reliabilityConstraintMet * 100.0));
        
        Log.printLine("\n--- Security Metrics ---");
        Log.printLine("Jobs on Mist VMs (Tier 1 - Highest Security): " + mistJobs + " (" + String.format("%.2f%%", mistJobs * 100.0 / totalJobs) + ")");
        Log.printLine("Jobs on Fog VMs (Tier 2 - Medium Security): " + fogJobs + " (" + String.format("%.2f%%", fogJobs * 100.0 / totalJobs) + ")");
        Log.printLine("Jobs on Cloud VMs (Tier 3 - Lowest Security): " + cloudJobs + " (" + String.format("%.2f%%", cloudJobs * 100.0 / totalJobs) + ")");
        
        double securityScore = (mistJobs * 3 + fogJobs * 2 + cloudJobs * 1) / (double) totalJobs;
        Log.printLine("Average Security Score (1-3, higher=better): " + String.format("%.2f", securityScore));
        
        Log.printLine("\n--- Energy Consumption Metrics ---");
        Log.printLine("Total Energy Consumption: " + String.format("%.4f", totalEnergy) + " kWh");
        Log.printLine("Average Energy per Job: " + String.format("%.6f", totalEnergy / totalJobs) + " kWh");
        
        // Energy consumption per tier
        double mistEnergy = 0, fogEnergy = 0, cloudEnergy = 0;
        for (Job job : completedJobs) {
            SCEAHVM vm = vmMap.get(job.getVmId());
            if (vm != null) {
                double execTimeHours = job.getActualCPUTime() / 3600.0;
                double energy = (vm.getPMax() * execTimeHours) / 1000.0;
                if (vm.getTier() == 1) mistEnergy += energy;
                else if (vm.getTier() == 2) fogEnergy += energy;
                else cloudEnergy += energy;
            }
        }
        Log.printLine("Energy by Mist VMs: " + String.format("%.4f", mistEnergy) + " kWh");
        Log.printLine("Energy by Fog VMs: " + String.format("%.4f", fogEnergy) + " kWh");
        Log.printLine("Energy by Cloud VMs: " + String.format("%.4f", cloudEnergy) + " kWh");
        
        Log.printLine("\n--- Cost Metrics ---");
        Log.printLine("Total Cloud Cost: $" + String.format("%.4f", totalCost));
        Log.printLine("Average Cost per Job: $" + String.format("%.6f", totalCost / totalJobs));
        
        Log.printLine("\n--- Deadline Compliance Metrics ---");
        Log.printLine("Jobs Met Deadline: " + jobsMetDeadline + " (" + String.format("%.2f%%", jobsMetDeadline * 100.0 / totalJobs) + ")");
        Log.printLine("Jobs Missed Deadline: " + jobsMissedDeadline + " (" + String.format("%.2f%%", jobsMissedDeadline * 100.0 / totalJobs) + ")");
        
        // Calculate average response time
        double totalResponseTime = 0;
        for (Job job : completedJobs) {
            totalResponseTime += (job.getFinishTime() - job.getSubmissionTime());
        }
        Log.printLine("Average Response Time: " + String.format("%.2f", totalResponseTime / totalJobs) + " seconds");
        
        Log.printLine("\n========== END OF METRICS ==========\n");
    }
}
