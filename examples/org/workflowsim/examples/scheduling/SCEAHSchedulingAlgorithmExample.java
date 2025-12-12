/**
 * Copyright 2012-2013 University Of Southern California
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.workflowsim.examples.scheduling;

import java.io.File;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.ArrayList;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.CloudletSchedulerSpaceShared;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.Cloudlet; // Required for the final cast

// New CloudSim Imports required for the Datacenter fix:
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;

import org.workflowsim.WorkflowDatacenter;
import org.workflowsim.Job;
import org.workflowsim.Task; // Base Task class
import org.workflowsim.WorkflowEngine;
import org.workflowsim.WorkflowPlanner;
import org.workflowsim.utils.ClusteringParameters;
import org.workflowsim.utils.OverheadParameters;
import org.workflowsim.utils.Parameters;
import org.workflowsim.utils.ReplicaCatalog;
import org.cloudbus.cloudsim.VmSchedulerSpaceShared;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;


// Custom Imports
import org.workflowsim.custom.scheduler.SCEAHVM; 
import org.workflowsim.SecureReliableTask; // Custom Task class

/**
 * This is the setup class for the SCEAHScheduling Algorithm.
 */
public class SCEAHSchedulingAlgorithmExample extends DataAwareSchedulingAlgorithmExample {

    // Helper to define VM resources - assumed consistent with the scheduler's needs
    private static final int VM_MIPS = 1000;
    private static final int VM_PES = 1;
    private static final int VM_RAM = 512;
    private static final long VM_BW = 1000;
    private static final long VM_SIZE = 10000;
    private static final String VM_VMM = "Xen";
    
    // Default TDP for power calculation
    private static final double TDP_MIST = 200.0;
    private static final double TDP_CLOUD = 100.0;
    
    // **FIXED createDatacenter METHOD**
    /**
     * Creates the WorkflowDatacenter with necessary Hosts and PEs.
     */
    protected static WorkflowDatacenter createDatacenter(String name) {
        
        // 1. Create a list to store the Hosts
        List<Host> hostList = new ArrayList<>();

        // 2. Define Host and PE characteristics
        int hostId = 0;
        int hostRam = 3072;   // Host memory (RAM) in MB (increased to support 5 VMs)
        long hostBw = 10000;   // Host bandwidth in Mb/s
        long hostStorage = 1000000; // Host storage in MB
        
        // Define the processing elements (PEs) for the Host
        List<Pe> peList = new ArrayList<>();
        int mips = 5000000; // MIPS capacity of a single PE (increased to allow more VMs)

        // Create 5 PEs (cores) for the host to support 5 VMs
        peList.add(new Pe(0, new PeProvisionerSimple(mips))); 
        peList.add(new Pe(1, new PeProvisionerSimple(mips)));
        peList.add(new Pe(2, new PeProvisionerSimple(mips)));
        peList.add(new Pe(3, new PeProvisionerSimple(mips)));
        peList.add(new Pe(4, new PeProvisionerSimple(mips))); 
      
     // 3. Create the Host
        Host host = new Host(
                hostId,
                // **FIX**: Revert to the constructor with capacity, as this is the standard CloudSim pattern.
                new RamProvisionerSimple(hostRam), 
                new BwProvisionerSimple(hostBw), 
                hostStorage,
                peList,
                new VmSchedulerSpaceShared(peList) 
        );

        // 4. Add the Host to the list
        hostList.add(host); 
        
        // 5. Define Datacenter Characteristics
        String arch = "x86"; // system architecture
        String os = "Linux"; // operating system
        String vmm = "Xen";
        double time_zone = 10.0; // time zone this resource located
        double cost = 3.0; // the cost of using processing in this resource
        double costPerMem = 0.05; // the cost of using memory in this resource
        double costPerStorage = 0.1; // the cost of using storage in this resource
        double costPerBw = 0.1; // the cost of using bw in this resource

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
     * Helper method to create custom SCEAHVM instances with different tiers.
     * Tier 1 = Mist (high security, low cost, low power)
     * Tier 2 = Fog (medium security, medium cost, medium power)
     * Tier 3 = Cloud (low security, high cost, high power)
     */
    private static List<SCEAHVM> createVM(int userId, int vms, int vmMips, int vmPes, int vmRam, long vmBw, long vmSize, String vmm) {
        
        List<SCEAHVM> vmList = new ArrayList<>();

        for (int i = 0; i < vms; i++) {
            
            int tier;
            int mips;
            double costRatePerHour;
            double tdp;
            
            // Distribute VMs across three tiers to test SCEAH algorithm
            if (i < 2) {
                // Tier 1: Mist VMs (2 VMs) - low power, low cost, high security
                tier = 1;
                mips = 1000;
                costRatePerHour = 0.05;
                tdp = 100.0;
            } else if (i < 4) {
                // Tier 2: Fog VMs (2 VMs) - medium power, medium cost, medium security
                tier = 2;
                mips = 2000;
                costRatePerHour = 0.15;
                tdp = 200.0;
            } else {
                // Tier 3: Cloud VMs (1 VM) - high power, high cost, low security
                tier = 3;
                mips = 3000;
                costRatePerHour = 0.30;
                tdp = 300.0;
            }

            // Create SCEAHVM with all custom properties
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
                costRatePerHour,
                tdp,
                vmPes // numPhysicalCores
            );
            
            vmList.add(vm);
        }
        return vmList;
    }


    ////////////////////////// STATIC METHODS ///////////////////////
    
    public static void main(String[] args) {

        try {
            int vmNum = 5; // number of vms;
            
            String daxPath = "config/dax/Montage_100.xml"; 
            File daxFile = new File(daxPath);
            if (!daxFile.exists()) {
                Log.printLine("Warning: DAX file not found at: " + daxPath);
                return;
            }
            Log.printLine("DAX file found at: " + daxFile.getAbsolutePath());

            Parameters.SchedulingAlgorithm sch_method = Parameters.SchedulingAlgorithm.SCEAH;
            Parameters.PlanningAlgorithm pln_method = Parameters.PlanningAlgorithm.INVALID; 
            ReplicaCatalog.FileSystem file_system = ReplicaCatalog.FileSystem.LOCAL;

            OverheadParameters op = new OverheadParameters(0, null, null, null, null, 0);
            ClusteringParameters.ClusteringMethod method = ClusteringParameters.ClusteringMethod.NONE;
            ClusteringParameters cp = new ClusteringParameters(0, 0, method, null); 
            
            /**
             * No task factory - use default Task objects.
             */
            Parameters.init(vmNum, daxPath, null,
                    null, op, cp, sch_method, pln_method,
                    null, 0);
            
            ReplicaCatalog.init(file_system);

            int num_user = 1;
            Calendar calendar = Calendar.getInstance();
            boolean trace_flag = false;

            // Initialize the CloudSim library
            CloudSim.init(num_user, calendar, trace_flag); 

            // **Datacenter Creation is now fixed**
            WorkflowDatacenter datacenter0 = createDatacenter("Datacenter_0");

            WorkflowPlanner wfPlanner = new WorkflowPlanner("planner_0", 1);
            WorkflowEngine wfEngine = wfPlanner.getWorkflowEngine();
            
            // Manually parse the workflow to get tasks
            wfPlanner.getWorkflowParser().parse();
            List<Task> taskList = wfPlanner.getWorkflowParser().getTaskList();
            Log.printLine("Parsed " + taskList.size() + " tasks from DAX file");
            
            // Submit tasks directly to the engine (not through planner)
            wfEngine.submitCloudletList((List)taskList);
            
            // Call the custom VM creator
            List<SCEAHVM> vmlist0 = createVM(wfEngine.getSchedulerId(0), Parameters.getVmNum(), 
                    VM_MIPS, VM_PES, VM_RAM, VM_BW, VM_SIZE, VM_VMM); 
            
            // Submits this list of vms to this WorkflowEngine.
            wfEngine.submitVmList((List)vmlist0, 0); 

            // Binds the data centers with the scheduler.
            wfEngine.bindSchedulerDatacenter(datacenter0.getId(), 0);

            // Start the simulation
            CloudSim.startSimulation();
            
            List<Job> outputList0 = wfEngine.getJobsReceivedList();
            
            // Stop the simulation
            CloudSim.stopSimulation();
            
            // Assuming printJobList is defined elsewhere (e.g., in the superclass)
            printJobList(outputList0);
            
            // Calculate and print SCEAH metrics
            printSCEAHMetrics(outputList0, vmlist0);
            
        } catch (Exception e) {
            Log.printLine("Unwanted errors happen");
            e.printStackTrace();
        } 
    }
    
    /**
     * Calculate and print SCEAH algorithm metrics:
     * - Security compliance
     * - Energy consumption
     * - Deadline compliance
     * - Reliability (success rate)
     */
    private static void printSCEAHMetrics(List<Job> completedJobs, List<SCEAHVM> vms) {
        Log.printLine("\n========== SCEAH ALGORITHM METRICS ==========");
        
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
        
        // Reliability tracking (Eq. 21)
        double workflowReliability = 1.0;
        
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
                // Energy (kWh) = Power (W) * Time (hours)
                double executionTimeHours = job.getActualCPUTime() / 3600.0;
                double powerConsumption = vm.getPMax(); // in Watts
                double jobEnergy = (powerConsumption * executionTimeHours) / 1000.0; // Convert to kWh
                totalEnergy += jobEnergy;
                
                // Cost: Calculate monetary cost
                double costForJob = vm.getCostRatePerHour() * executionTimeHours;
                totalCost += costForJob;
                
                // Deadline: Check if job met its deadline
                // Using finish time vs depth-based deadline approximation
                double deadline = job.getDepth() * 50.0; // Approximate deadline based on depth
                if (job.getFinishTime() <= deadline) {
                    jobsMetDeadline++;
                } else {
                    jobsMissedDeadline++;
                }
                
                // Reliability calculation (Eq. 21 - Weibull distribution)
                double lambda = 1e-5; // Failure rate
                double beta = 1.0;    // Shape parameter
                double jobReliability = Math.exp(-Math.pow(job.getActualCPUTime() * lambda, beta));
                workflowReliability *= jobReliability;
            }
        }
        
        // Print metrics
        Log.printLine("\n--- Reliability Metrics ---");
        Log.printLine("Total Jobs: " + totalJobs);
        Log.printLine("Successful Jobs: " + successfulJobs);
        Log.printLine("Failed Jobs: " + failedJobs);
        Log.printLine("Success Rate: " + String.format("%.2f%%", (successfulJobs * 100.0 / totalJobs)));
        Log.printLine("Workflow Reliability: " + String.format("%.4f", workflowReliability));
        int reliabilityConstraintMet = (workflowReliability >= 0.95) ? 1 : 0;
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