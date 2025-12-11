package org.workflowsim;

import org.workflowsim.WorkflowPlanner;
import org.workflowsim.WorkflowEngine;
import org.workflowsim.Task;
import org.workflowsim.SecureReliableTask; // Your custom task
import org.cloudbus.cloudsim.Log;
import java.util.ArrayList;
import java.util.List;

public class SCEAHWorkflowPlanner extends WorkflowPlanner {

    public SCEAHWorkflowPlanner(String name, int plannerId) throws Exception {
        super();
    }
    
    // Override the submitJob method in the planner
    @Override
    public void submitJob(List jobList) {
        
        List<SecureReliableTask> customTaskList = new ArrayList<>();
        
        // Step 1: Perform the conversion (same logic as before)
        for (Object obj : jobList) {
            if (obj instanceof Task) {
                Task genericTask = (Task) obj;
                
                // IMPORTANT: You need access to the constructors/fields of your custom task here.
                // Assuming default values for level/security for the conversion demo:
                SecureReliableTask customTask = new SecureReliableTask(
                    genericTask.getCloudletId(), 
                    genericTask.getCloudletLength(),
                    genericTask.getNumberOfPes(),
                    genericTask.getCloudletFileSize(),
                    genericTask.getCloudletOutputSize(),
                    genericTask.getUtilizationModelCpu(),
                    genericTask.getUtilizationModelRam(),
                    genericTask.getUtilizationModelBw(),
                    1, true, false // Custom properties
                );
                
                // Transfer dependencies (CRITICAL for DAG structure)
                customTask.setParents(genericTask.getParentList());
                customTask.setChildren(genericTask.getChildList());
                customTask.setDepth(genericTask.getDepth());
                
                customTaskList.add(customTask);
            }
        }
        
        Log.printLine("Converted " + customTaskList.size() + " tasks to SecureReliableTask type.");

        // Step 2: Submit the converted list to the default WorkflowEngine
        // The base class's submitJob will now pass the customTaskList down to the Engine/Scheduler
        super.submitJob(customTaskList);
    }
}