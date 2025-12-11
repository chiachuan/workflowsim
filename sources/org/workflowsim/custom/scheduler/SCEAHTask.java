package org.workflowsim.custom.scheduler;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.UtilizationModelFull; 

public class SCEAHTask extends Cloudlet {
    private double deadline; 
    private int securityLevel; 
    private double level;
    private double computationalVolume; 

    public SCEAHTask(int userId, int jobId, double mips, long length, long fileSize, long outputSize) {
        
        // Final working super call based on the Job.java source you provided
        //super(jobId, length); 
        super(jobId, length, 1, 0, 0, new UtilizationModelFull(), new UtilizationModelFull(), new UtilizationModelFull());
        
        // This method still exists and is correct.
        setUserId(userId);

        // We cannot use setFileSize or setCloudletFileSize/OutputSize.
        // We MUST assume the job creation logic in your main runner file (where you call 
        // this constructor) is handling the file sizes correctly by passing them up 
        // the chain during the job object creation.
        
        // Initialize custom properties
        this.computationalVolume = mips; 
        // Other custom properties (deadline, securityLevel, level) should be set here 
        // if they aren't done via a secondary setter.
    }

    // --- Getters and Setters ---
    public double getDeadline() { return deadline; }
    public void setDeadline(double deadline) { this.deadline = deadline; }

    public int getSecurityLevel() { return securityLevel; }
    public void setSecurityLevel(int securityLevel) { this.securityLevel = securityLevel; }

    public double getLevel() { return level; }
    public void setLevel(double level) { this.level = level; }
    
    public double getComputationalVolume() { return computationalVolume; } 
    public void setComputationalVolume(double computationalVolume) { this.computationalVolume = computationalVolume; }
    
    public double getAvgComputationalCost() { 
        // Used for tie-breaking in task selection (Line 3)
        // We use volume as a proxy for largest cost.
        return computationalVolume; 
    }
}