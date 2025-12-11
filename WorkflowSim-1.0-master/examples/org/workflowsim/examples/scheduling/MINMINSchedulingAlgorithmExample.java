package org.workflowsim.examples.scheduling;

import java.util.Map;
import java.util.HashMap;
import java.util.Calendar;
import java.util.List;
import java.util.ArrayList;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.CloudletSchedulerSpaceShared;
import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.VmAllocationPolicySimple;
import org.cloudbus.cloudsim.Storage; 
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.cloudbus.cloudsim.Cloudlet; 
import org.cloudbus.cloudsim.Vm;
import org.workflowsim.CondorVM; 
import org.cloudbus.cloudsim.core.CloudSimTags; // Added import for CloudSimTags

import org.workflowsim.WorkflowDatacenter;
import org.workflowsim.Job; 
import org.workflowsim.WorkflowParser;
import org.workflowsim.Task;
import org.workflowsim.SecureReliableTask; 
import org.workflowsim.WorkflowEngine;
import org.workflowsim.utils.ClusteringParameters;
import org.workflowsim.utils.OverheadParameters;
import org.workflowsim.utils.Parameters;
import org.workflowsim.utils.ReplicaCatalog;
import org.workflowsim.utils.Parameters.SchedulingAlgorithm;
import org.workflowsim.utils.Parameters.PlanningAlgorithm;
import org.workflowsim.custom.scheduler.SCEAHVM; 
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;



/**
 * Main class for running the SCEAH Scheduler.
 * Extends DataAwareSchedulingAlgorithmExample.
 */
public class MINMINSchedulingAlgorithmExample extends DataAwareSchedulingAlgorithmExample { 

    public static List<CondorVM> createVM(int userId, int vms) {
        
        List<CondorVM> list = new ArrayList<>(); 
        long size = 10000; 
        int ram = 512;     
        long bw = 1000;
        int pesNumber = 1; 
        String vmm = "Xen"; 
        int tier; 
        int mips; 
        double costRatePerHour;
        double tdp; 
        int numPhysicalCores = 1; 
        

        for (int i = 0; i < vms; i++) {
            
            if (i < 3) {
                tier = 1; 
                mips = 1000;
                costRatePerHour = 0.05; 
                tdp = 100.0; 
            } else {
                tier = 2; 
                mips = 2000; 
                costRatePerHour = 0.15; 
                tdp = 200.0; 
            }
            
            SCEAHVM vm = new SCEAHVM(
                    i, 
                    userId, 
                    mips, 
                    pesNumber, 
                    ram, 
                    bw, 
                    size, 
                    vmm,
                    new CloudletSchedulerSpaceShared(), 
                    tier,                             
                    costRatePerHour,                  
                    tdp,                              
                    numPhysicalCores                  
            );
            
            list.add(vm); 
        }
        Log.printLine("LOG: Successfully created " + list.size() + " SCEAHVMs.");
        return list; 
    }
    
    public static WorkflowDatacenter createDatacenter(String name) {
        
    	List<Host> hostList = new ArrayList<>();
        int hostNum = Parameters.getVmNum(); 
        long storage = 1000000;
        int hostRam = 2048;
        long hostBw = 10000;
        int hostMips = 10000;
        int hostPes = 2;
        double cost = 3.0;             
        double costPerMem = 0.05;       
        double costPerStorage = 0.001;  
        double costPerBw = 0.0;         
        
        for (int i = 0; i < hostNum; i++) {
            List<Pe> peList = new ArrayList<>();
            double peMips = (double)hostMips / hostPes; 

            for (int j = 0; j < hostPes; j++) {
                peList.add(new Pe(j, new PeProvisionerSimple(peMips)));
            }
            
            hostList.add(
                new Host(
                    i,
                    new RamProvisionerSimple(hostRam),
                    new BwProvisionerSimple(hostBw),
                    storage,
                    peList,
                    new VmSchedulerTimeShared(peList)
                )
            );
        }
        
        try {
            String arch = "x86";      
            String os = "Linux";      
            String vmm = "Xen";       
            double time_zone = 10.0;  
            double scheduling_interval = 0.0; 
            
            DatacenterCharacteristics characteristics = new org.cloudbus.cloudsim.DatacenterCharacteristics(
                arch, os, vmm, hostList, time_zone, cost, costPerMem, costPerStorage, costPerBw
            );

            VmAllocationPolicy vmAllocationPolicy = new VmAllocationPolicySimple(hostList);
            List<Storage> storageList = new ArrayList<>(); 

            return new WorkflowDatacenter(
                name, 
                characteristics,
                vmAllocationPolicy,     
                storageList,            
                scheduling_interval     
            );

        } catch (Exception e) {
            Log.printLine("Error creating Datacenter: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }


	public static void main(String[] args) {

	    try {
	        // 1. INITIALIZATION AND PARAMETERS
	        int vmNum = 5;
	        String daxPath = "C:\\workflowsim\\config\\dax\\Montage_100.xml"; 
	        
	        SchedulingAlgorithm sch_method = Parameters.SchedulingAlgorithm.MINMIN; 
	        PlanningAlgorithm pln_method = Parameters.PlanningAlgorithm.HEFT; 
	        
	        ReplicaCatalog.FileSystem file_system = ReplicaCatalog.FileSystem.LOCAL;
	        OverheadParameters op = new OverheadParameters(0, null, null, null, null, 0);
	        ClusteringParameters.ClusteringMethod method = ClusteringParameters.ClusteringMethod.NONE;
	        ClusteringParameters cp = new ClusteringParameters(0, 0, method, null);

	        Parameters.init(vmNum, daxPath, null, null, op, cp, sch_method, pln_method, null, 0);
	        ReplicaCatalog.init(file_system);

	        int num_user = 1; 
	        Calendar calendar = Calendar.getInstance();
	        boolean trace_flag = false;

	        CloudSim.init(num_user, calendar, trace_flag);

            // Datacenter creation
	        WorkflowDatacenter datacenter0 = createDatacenter("Datacenter_0"); 
            
            if (datacenter0 == null) { 
                Log.printLine("FATAL ERROR: Datacenter initialization failed. Simulation cannot proceed.");
                return; 
            }
	        
	        // ----------------------------------------------------------------------
	        // 2. ENGINE/SCHEDULER INITIALIZATION 
	        // ----------------------------------------------------------------------
            int userId = 0; // The base user ID expected by CloudSim
            
            // 1. Create the WorkflowEngine.
	        WorkflowEngine wfEngine = new WorkflowEngine("engine_" + userId, userId);
	        
            // 2. The scheduler ID is the engine's entity ID (e.g., 3), which is passed to tasks/VMs.
	        int schedulerId = wfEngine.getId(); 
	        int datacenterId = datacenter0.getId();
            Log.printLine("LOG: Assuming schedulerId is EngineId: " + schedulerId + " and DatacenterId is " + datacenterId);

	        
	        // ----------------------------------------------------------------------
	        // 3. TASK PARSING AND CONVERSION LAYER
	        // ----------------------------------------------------------------------
	        
	        Log.printLine("Starting custom task conversion...");
	        
	        // The parser uses the schedulerId
	        WorkflowParser wfParser = new WorkflowParser(schedulerId);
	        wfParser.parse(); 

	        List<Task> genericTaskList = wfParser.getTaskList(); 
	        List<SecureReliableTask> convertedTaskList = new ArrayList<>(); 
	        Map<Integer, SecureReliableTask> idToCustomTask = new HashMap<>(); 

	        for (Task genericTask : genericTaskList) {
	            SecureReliableTask customTask = new SecureReliableTask(
	                genericTask.getCloudletId(), genericTask.getCloudletLength(),
	                genericTask.getNumberOfPes(), genericTask.getCloudletFileSize(),
	                genericTask.getCloudletOutputSize(), genericTask.getUtilizationModelCpu(),
	                genericTask.getUtilizationModelRam(), genericTask.getUtilizationModelBw(),
	                1, true, false    
	            );
	            customTask.setDepth(genericTask.getDepth()); 
	            customTask.setUserId(schedulerId); // Set the correct scheduler ID
	            convertedTaskList.add(customTask);
	            idToCustomTask.put(customTask.getCloudletId(), customTask);
	        }
	        
	        // Dependency logic
	        for (Task genericTask : genericTaskList) {
	            SecureReliableTask customTask = idToCustomTask.get(genericTask.getCloudletId());
	            for (Task genericParent : genericTask.getParentList()) {
	                SecureReliableTask customParent = idToCustomTask.get(genericParent.getCloudletId());
	                if (customParent != null) {
	                    customTask.addParent(customParent);
	                }
	            }
	            for (Task genericChild : genericTask.getChildList()) {
	                SecureReliableTask customChild = idToCustomTask.get(genericChild.getCloudletId());
	                if (customChild != null) {
	                    customTask.addChild(customChild);
	                }
	            }
	        }
	        
	        if (!convertedTaskList.isEmpty()) {
	            Task lastTask = convertedTaskList.get(convertedTaskList.size() - 1);
	            if (lastTask.getCloudletId() == 100 && lastTask instanceof SecureReliableTask) {
	                convertedTaskList.remove(convertedTaskList.size() - 1);
	            }
	        }

	        // ----------------------------------------------------------------------
	        // 4. TASK AND VM SUBMISSION
	        // ----------------------------------------------------------------------
	        
	        @SuppressWarnings("unchecked")
	        List<? extends Cloudlet> cloudletListToSubmit = (List<? extends Cloudlet>) (List<?>) convertedTaskList;
	        
            wfEngine.submitCloudletList(cloudletListToSubmit);
            Log.printLine("LOG: Successfully submitted " + convertedTaskList.size() + " Cloudlets directly to WorkflowEngine.");


	        // 5. VM SUBMISSION (Bypassing the broken submitVmList)
	        @SuppressWarnings("unchecked")
	        List<? extends Vm> vmlist0 = (List<? extends Vm>) createVM(schedulerId, Parameters.getVmNum()); 

            Log.printLine("LOG: Bypassing internal submitVmList(). Sending VM_CREATE_ACK message.");
            
            // WORKAROUND 1: Send the VM list directly to the WorkflowEngine (Broker) entity 
            CloudSim.send(
                schedulerId, // Source ID 
                schedulerId, // Destination ID 
                0.0, // Delay
                CloudSimTags.VM_CREATE_ACK, 
                vmlist0 // Data
            );

            // ----------------------------------------------------------------------
            // 5B. DATACENTER BINDING (Bypassing the broken bindSchedulerDatacenter)
            // ----------------------------------------------------------------------
            Log.printLine("LOG: Bypassing internal bindSchedulerDatacenter(). Sending REGISTER_SCHEDULER (8011) message.");
            
            // WORKAROUND 2: Send a direct message to the Datacenter to register the Scheduler ID.
            // CloudSimTags.REGISTER_SCHEDULER is usually 8011 in WorkflowSim/CloudSim.
            final int REGISTER_SCHEDULER_TAG = 8011; 
            
            CloudSim.send(
                schedulerId, // Source ID (The scheduler entity ID 3)
                datacenterId, // Destination ID (The Datacenter entity ID 2)
                0.0, // Delay
                REGISTER_SCHEDULER_TAG, // Tag
                schedulerId // Data: The ID of the scheduler being registered
            );


	        // 6. START SIMULATION
	        CloudSim.startSimulation();
	        List<Job> outputList0 = wfEngine.getJobsReceivedList(); 
	        CloudSim.stopSimulation();
	        
	        printJobList(outputList0);
	        
	    } catch (Exception e) {
	        Log.printLine("The simulation has been terminated due to an unexpected error");
	        e.printStackTrace();
	    }
	}
	
	
}