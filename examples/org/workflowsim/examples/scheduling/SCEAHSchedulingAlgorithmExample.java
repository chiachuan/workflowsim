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
        int hostRam = 2048;   // Host memory (RAM) in MB
        long hostBw = 10000;   // Host bandwidth in Mb/s
        long hostStorage = 1000000; // Host storage in MB
        
        // Define the processing elements (PEs) for the Host
        List<Pe> peList = new ArrayList<>();
        int mips = 80000; // MIPS capacity of a single PE

        // Create two PEs (cores) for the host
        peList.add(new Pe(0, new PeProvisionerSimple(mips))); 
        peList.add(new Pe(1, new PeProvisionerSimple(mips))); 
      
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
     * Helper method to create custom SCEAHVM instances.
     */
    private static List<SCEAHVM> createVM(int userId, int vms, int vmMips, int vmPes, int vmRam, long vmBw, long vmSize, String vmm) {
        
        List<SCEAHVM> vmList = new ArrayList<>();
        int tier; 

        for (int i = 0; i < vms; i++) {
            
            double tdp;
            
            // Distribute VMs into tiers for testing the scheduler logic
            if (i < 2) { 
                tier = 1; // Mist/Fog Tier (Higher Power/Cost for Tier 2)
                tdp = TDP_MIST;
            } else {
                tier = 2; // Cloud Tier (Lower Power/Cost for Tier 1)
                tdp = TDP_CLOUD;
            }

            // Create SCEAHVM instead of a standard VM
            SCEAHVM vm = new SCEAHVM(i, userId, vmMips, vmPes, vmRam, vmBw, vmSize, vmm, 
                                     new CloudletSchedulerSpaceShared());
            
            // Assign custom SCEAHVM properties
            vm.setTier(tier); 
            // Setting PMax via the custom setter (PMax is derived from TDP in the VM class)
            vm.setPMax(tdp); // We set TDP, which getPMax() uses
            
            vmList.add(vm);
        }
        return vmList;
    }


    ////////////////////////// STATIC METHODS ///////////////////////
    
    public static void main(String[] args) {

        try {
            int vmNum = 5; // number of vms;
            
            // **CHANGE THIS PATH** based on your environment
            String daxPath = "C:\\workflowsim\\config\\dax\\Montage_100.xml"; 
            File daxFile = new File(daxPath);
            if (!daxFile.exists()) {
                Log.printLine("Warning: Please replace daxPath with the physical path in your working environment! File not found at: " + daxPath);
                return;
            }

            Parameters.SchedulingAlgorithm sch_method = Parameters.SchedulingAlgorithm.SCEAH;
            Parameters.PlanningAlgorithm pln_method = Parameters.PlanningAlgorithm.INVALID; 
            ReplicaCatalog.FileSystem file_system = ReplicaCatalog.FileSystem.LOCAL;

            OverheadParameters op = new OverheadParameters(0, null, null, null, null, 0);
            ClusteringParameters.ClusteringMethod method = ClusteringParameters.ClusteringMethod.NONE;
            ClusteringParameters cp = new ClusteringParameters(0, 0, method, null); 
            
            /**
             * FIX 1: Set the Task Factory Class Name to NULL.
             * This bypasses the attempt to load a non-existent TaskFactory class.
             * WorkflowSim will now create default Task objects from the DAX file.
             */
            Parameters.init(vmNum, daxPath, null,
                    null, op, cp, sch_method, pln_method,
                    null, 0); // Task Factory set to null
            
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
            
            // Call the custom VM creator
            List<SCEAHVM> vmlist0 = createVM(wfEngine.getSchedulerId(0), Parameters.getVmNum(), 
                    VM_MIPS, VM_PES, VM_RAM, VM_BW, VM_SIZE, VM_VMM); 
            
            // Submits this list of vms to this WorkflowEngine.
            wfEngine.submitVmList((List)vmlist0, 0); 

            /**
             * FIX 2: MANUAL TASK CONVERSION
             * This section manually loads the tasks, converts them from the
             * standard Task class to the SecureReliableTask class, and 
             * submits them to the engine, resolving the TaskFactory issue.
             */

            // 1. Load the DAG (This creates standard Task/Job objects)
            // **FIX 5: Call the correct method on WorkflowParser**
            wfPlanner.getWorkflowParser().parse();

            // 2. Get the list of all tasks/jobs directly from the parser
            //    (Since wfPlanner.getTaskList() relies on internal event processing)
            List<Task> allItemsList = wfPlanner.getWorkflowParser().getTaskList();

            // List to hold the newly created Job objects with converted tasks
            List<Job> customJobList = new ArrayList<>();

            // 3. Iterate over the items (which may be Tasks or Jobs) and replace inner Tasks.
            for (Task taskItem : allItemsList) {
                
                // Check if the item is a Job (which contains clustered tasks)
                if (taskItem instanceof Job) { 
                    
                    Job originalJob = (Job) taskItem;
                    
                    // **CRITICAL FIX 1: The original Job object is already a Task, convert its *inner* tasks.**
                    List<Task> originalInnerTasks = originalJob.getTaskList();
                    List<Task> secureInnerTasks = new ArrayList<>();
                    
                    // Loop through the tasks *inside* the Job object
                    for (Task originalTask : originalInnerTasks) {
                        
                        // --- Custom Logic: Determine Security/Level (Heuristic) ---
                        int level; 
                        boolean isSecure;
                        
                        // Example Heuristic: Tasks with long execution time are Secure/Level 2
                        if (originalTask.getCloudletLength() > 50000) {
                            level = 2; 
                            isSecure = true;
                        } else {
                            level = 1; 
                            isSecure = false;
                        }
                        
                        // Create the SecureReliableTask, copying properties
                        SecureReliableTask srt = new SecureReliableTask(
                            originalTask.getCloudletId(),
                            originalTask.getCloudletLength(),
                            originalTask.getNumberOfPes(),
                            originalTask.getCloudletFileSize(),
                            originalTask.getCloudletOutputSize(),
                            new UtilizationModelFull(), 
                            new UtilizationModelFull(),
                            new UtilizationModelFull(),
                            level,
                            isSecure,
                            false 
                        );
                        
                        // Crucially, copy the DAG structure (parents/children)
                        srt.setParentList(originalTask.getParentList());
                        srt.setChildList(originalTask.getChildList());
                        
                        // Add the new SecureReliableTask to the new list
                        secureInnerTasks.add(srt);
                    }
                    
                    // **CRITICAL FIX 3: Recreate the Job and add the new task list**
                    Job secureJob = new Job(originalJob.getCloudletId(), originalJob.getCloudletLength());
                    
                    // Copy the critical Job properties:
                    secureJob.setVmId(originalJob.getVmId());
                    secureJob.setDepth(originalJob.getDepth());
                    
                    // Copy the DAG structure *for the Job itself* (parents/children between jobs)
                    secureJob.setParentList(originalJob.getParentList()); 
                    secureJob.setChildList(originalJob.getChildList()); 
                    
                    // Set the list of secure tasks inside the new Job
                    secureJob.setTaskList(secureInnerTasks); 
                    
                    // Add the new Job to the final list
                    customJobList.add(secureJob);
                    
                } 
                // else block for standalone tasks (not clustered) is omitted for simplicity
            } 
            
            // 4. Submit the converted job list to the engine.
            wfEngine.submitCloudletList((List<Cloudlet>)(List<?>)customJobList); 

            // Binds the data centers with the scheduler.
            wfEngine.bindSchedulerDatacenter(datacenter0.getId(), 0);

            // Start the simulation
            CloudSim.startSimulation();
            
            List<Job> outputList0 = wfEngine.getJobsReceivedList();
            
            // Stop the simulation
            CloudSim.stopSimulation();
            
            // Assuming printJobList is defined elsewhere (e.g., in the superclass)
            printJobList(outputList0);
        } catch (Exception e) {
            Log.printLine("Unwanted errors happen");
            e.printStackTrace();
        } 
    }
}