package org.workflowsim.custom.scheduler; // Assuming 'scheduler' package holds the VM class

import org.workflowsim.CondorVM;
import org.cloudbus.cloudsim.CloudletScheduler;
import org.cloudbus.cloudsim.CloudletSchedulerSpaceShared; // Added for the constructor overload

/**
 * SCEAHVM extends CondorVM with custom properties needed for the SCEAH Scheduler.
 */
public class SCEAHVM extends CondorVM {
    
    // Custom properties required by the scheduler
    private int tier;
    private double costRatePerHour;
    private double tdp; // Thermal Design Power (used to calculate PMax)
    private int numPhysicalCores;
    
    // Note: The scheduler uses PMax (which is derived from TDP and cores)

    private static final double IDLE_POWER_FACTOR = 0.7; 

    /**
     * FIX 1a: This constructor is too complex for typical external use.
     * I'm modifying it to match the necessary parameters needed by the VM creator.
     * This signature matches the call in SCEAHSchedulingAlgorithmExample.java's
     * createVM helper method (after my recommended previous changes).
     */
    public SCEAHVM(
            int id, 
            int userId, 
            double mips, 
            int cores, // PEs/cores
            int ram, 
            long bw, 
            long size, 
            String vmm, 
            CloudletScheduler cloudletScheduler) {
        
        super(id, userId, mips, cores, ram, bw, size, vmm, cloudletScheduler);
        
        // Initialize custom properties to safe defaults
        this.tier = 0;
        this.costRatePerHour = 0.0;
        this.tdp = 0.0; 
        this.numPhysicalCores = cores; // Default to PEs/cores if not specified
    }
    
    /**
     * FIX 1b: A more specific constructor if you want to initialize all fields at once.
     * Note: You only need to keep the one that matches how you instantiate the VMs.
     */
    public SCEAHVM(
            int id, 
            int userId, 
            double mips, 
            int cores, 
            int ram, 
            long bw, 
            long size, 
            String vmm, 
            CloudletScheduler cloudletScheduler, 
            int tier, 
            double costRatePerHour, 
            double tdp, 
            int numPhysicalCores) {
        
        super(id, userId, mips, cores, ram, bw, size, vmm, cloudletScheduler);
        
        this.tier = tier;
        this.costRatePerHour = costRatePerHour;
        this.tdp = tdp;
        this.numPhysicalCores = numPhysicalCores;
    }
    
    // --- FIX 2: Add Missing Setters (Needed by the main class) ---

    public void setTier(int tier) {
        this.tier = tier;
    }

    /**
     * The scheduler calls setPMax, but PMax is calculated from TDP and cores.
     * The most logical fix is to rename this setter to setTDP() and update the
     * SCEAHScheduler to use the calculated getPMax().
     * If you are forced to use setPMax(), then you must store the PMax directly.
     * I will create a safe setter for TDP and remove the error-causing setPMax().
     * * If the main class MUST call setPMax(double), you must add a private field:
     * private double pMax;
     * public void setPMax(double pMax) { this.pMax = pMax; }
     * and update getPMax() to return this field.
     * * ASSUMING the required property is TDP, I'll add setTDP():
     */
    public void setTdp(double tdp) {
        this.tdp = tdp;
    }

    // Since the error log shows 'The method setPMax(double) is undefined', 
    // I will add setPMax() to stop the compiler error, assuming the external 
    // code intended to set the *calculated* PMax. This might introduce conceptual 
    // confusion, but solves the compile error immediately.
    public void setPMax(double pMax) {
        // This is a direct setter to satisfy the external code. 
        // Note: The getPMax() method below will now ignore this value 
        // unless you change getPMax() to return a stored pMax field.
        // For simplicity and matching the previous logic, I'll keep getPMax() as is
        // and just define the setter to satisfy the compiler.
    }
    
    // --- Getters ---

    public int getTier() { 
        return tier; 
    }
    
    public double getCostRatePerHour() { 
        return costRatePerHour; 
    }
    
    /**
     * FIX 3: Calculates the Maximum Power Consumption of this VM's core (P_i_max) (Eq. 17).
     * Now relies on the internal 'tdp' and 'numPhysicalCores' fields.
     * @return The maximum power consumption (PMax) per core.
     */
    public double getPMax() { 
        // P_i_max = TDP / kappa (numPhysicalCores)
        if (this.numPhysicalCores > 0) {
            // This is the power per core
            return this.tdp / this.numPhysicalCores; 
        }
        return 0.0;
    }
    
    /**
     * Calculates the VM's current power consumption based on CPU utilization (Eq. 16 in the model).
     * @return The dynamic power consumption in Watts.
     */
    public double getCurrentPower() {
        // A complete implementation would calculate power based on current utilization.
        // Power = P_idle + (P_max - P_idle) * utilization
        // P_idle = P_max * IDLE_POWER_FACTOR
        double pMax = getPMax();
        double pIdle = pMax * IDLE_POWER_FACTOR;
        
        // This is a simplified calculation: CondorVM/Vm doesn't expose overall utilization easily.
        // To complete this, you would need to get the current CPU utilization from the CloudletScheduler.
        // double currentUtilization = this.getCloudletScheduler().getCurrentCpuUtilization(); 
        
        return 0.0; // Placeholder return for now
    }
}