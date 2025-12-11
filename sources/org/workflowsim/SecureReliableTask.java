package org.workflowsim;

import org.cloudbus.cloudsim.UtilizationModel;
import org.cloudbus.cloudsim.UtilizationModelFull;
// Import the base Task class from WorkflowSim
import org.workflowsim.Task; 
import org.cloudbus.cloudsim.Cloudlet;
/**
 * A Task extension that includes security and reliability attributes,
 * along with scheduling-level information.
 * * NOTE: This class MUST extend Task, not Cloudlet, to inherit DAG structure methods.
 */
public class SecureReliableTask extends Task { // 🚨 FIXED: Now extends Task

    private int level;
    private double executionTime;
    private double deadline;
    private double lft; // Latest Finish Time
    private boolean isSecure;
    private boolean isReliable;

    public SecureReliableTask(
            int cloudletId,
            long cloudletLength,
            int pesNumber,
            long cloudletFileSize,
            long cloudletOutputSize,
            UtilizationModel utilizationModelCpu,
            UtilizationModel utilizationModelRam,
            UtilizationModel utilizationModelBw,
            int level, // Custom parameter
            boolean isSecure, // Custom parameter
            boolean isReliable // Custom parameter
    ) {
        // 🚨 FIXED: Calling the Task superclass constructor 🚨
    	 // super(cloudletId, cloudletLength, 1, 0, 0, new UtilizationModelFull(), new UtilizationModelFull(), new UtilizationModelFull());
    	  super(cloudletId, cloudletLength);

        this.level = level;
        this.executionTime = cloudletLength; // Default assumption based on length
        this.deadline = 0; // Default initialization
        this.lft = 0;      // Default initialization
        this.isSecure = isSecure;
        this.isReliable = isReliable;
    }

    // --- Getters and Setters (Keep existing methods) ---

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    // Note: Task inherits getCloudletLength(), use that for execution time logic if needed.
    public double getExecutionTime() {
        return executionTime;
    }

    public void setExecutionTime(double executionTime) {
        this.executionTime = executionTime;
    }

    public double getDeadline() {
        return deadline;
    }

    public void setDeadline(double deadline) {
        this.deadline = deadline;
    }

    public double getLFT() {
        return lft;
    }

    public void setLFT(double lft) {
        this.lft = lft;
    }

    public boolean isSecure() {
        return isSecure;
    }

    public void setSecure(boolean secure) {
        isSecure = secure;
    }

    public boolean isReliable() {
        return isReliable;
    }

    public void setReliable(boolean reliable) {
        isReliable = reliable;
    }

    @Override
    public String toString() {
        // Use inherited methods like getCloudletId()
        return String.format("Task[%d]: Level=%d, ExecTime=%.2f, Deadline=%.2f, LFT=%.2f, Secure=%b, Reliable=%b",
                 getCloudletId(), level, executionTime, deadline, lft, isSecure, isReliable);
    }
}