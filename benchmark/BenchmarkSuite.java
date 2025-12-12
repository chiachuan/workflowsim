package org.workflowsim.benchmark;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.CloudletSchedulerSpaceShared;
import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.VmSchedulerSpaceShared;
import org.workflowsim.WorkflowDatacenter;
import org.workflowsim.Job;
import org.workflowsim.WorkflowEngine;
import org.workflowsim.WorkflowPlanner;
import org.workflowsim.utils.ClusteringParameters;
import org.workflowsim.utils.OverheadParameters;
import org.workflowsim.utils.Parameters;
import org.workflowsim.utils.ReplicaCatalog;
import org.workflowsim.custom.scheduler.SCEAHVM;

import java.io.*;
import java.text.DecimalFormat;
import java.util.*;
import java.io.File;

/**
 * PhD Thesis Benchmark Suite
 * Comprehensive comparison of SCEAH, RCSECH, and FATS schedulers
 * with variable parameters for validation
 */
public class BenchmarkSuite {
    
    private static final String CSV_FILE = "benchmark/results/benchmark_results.csv";
    private static final DecimalFormat df = new DecimalFormat("#.####");
    
    // Benchmark configuration
    private static final int[] TASK_COUNTS = {30, 50, 100};
    private static final int[] CLOUD_MIPS = {2000, 3000, 5000};
    private static final double[] HIGH_CONF_RATIOS = {0.2, 0.4, 0.6, 0.8};
    
    // Fixed base configuration
    private static final int MIST_MIPS = 1000;
    private static final int FOG_MIPS = 2000;
    
    public static void main(String[] args) {
        try {
            // Initialize CSV output
            initializeCSV();
            
            System.out.println("=".repeat(80));
            System.out.println("PhD THESIS BENCHMARK SUITE - Fog Computing Schedulers");
            System.out.println("=".repeat(80));
            
            // Benchmark 1: Scalability (vary task count)
            System.out.println("\n[BENCHMARK 1] Scalability Analysis - Varying Task Count");
            runScalabilityBenchmark();
            
            // Benchmark 2: Cloud Performance Impact (vary cloud MIPS)
            System.out.println("\n[BENCHMARK 2] Cloud Performance Impact - Varying Cloud MIPS");
            runCloudMIPSBenchmark();
            
            // Benchmark 3: Security Level Impact (vary high-conf ratio)
            System.out.println("\n[BENCHMARK 3] Security Level Impact - Varying High-Conf Ratio");
            runSecurityLevelBenchmark();
            
            System.out.println("\n" + "=".repeat(80));
            System.out.println("Benchmark complete! Results saved to: " + CSV_FILE);
            System.out.println("=".repeat(80));
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static void initializeCSV() throws IOException {
        File dir = new File("benchmark/results");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(CSV_FILE))) {
            writer.println("Benchmark,Algorithm,TaskCount,CloudMIPS,HighConfRatio," +
                          "Makespan,Cost,Energy,Security,Reliability,RSR," +
                          "MistJobs,FogJobs,CloudJobs,Fragmented");
        }
    }
    
    private static void runScalabilityBenchmark() throws Exception {
        for (int taskCount : TASK_COUNTS) {
            String daxFile = getDaxFileForTaskCount(taskCount);
            
            for (String algorithm : new String[]{"SCEAH", "RCSECH", "FATS"}) {
                System.out.println(String.format("  Running %s with %d tasks...", algorithm, taskCount));
                BenchmarkResult result = runSingleBenchmark(algorithm, daxFile, 3000, 0.6);
                saveResult("Scalability", algorithm, taskCount, 3000, 0.6, result);
            }
        }
    }
    
    private static void runCloudMIPSBenchmark() throws Exception {
        String daxFile = "config/dax/Montage_100.xml";
        
        for (int cloudMips : CLOUD_MIPS) {
            for (String algorithm : new String[]{"SCEAH", "RCSECH", "FATS"}) {
                System.out.println(String.format("  Running %s with Cloud MIPS=%d...", algorithm, cloudMips));
                BenchmarkResult result = runSingleBenchmark(algorithm, daxFile, cloudMips, 0.6);
                saveResult("CloudMIPS", algorithm, 100, cloudMips, 0.6, result);
            }
        }
    }
    
    private static void runSecurityLevelBenchmark() throws Exception {
        String daxFile = "config/dax/Montage_100.xml";
        
        for (double highConfRatio : HIGH_CONF_RATIOS) {
            for (String algorithm : new String[]{"SCEAH", "RCSECH", "FATS"}) {
                System.out.println(String.format("  Running %s with High-Conf Ratio=%.1f%%...", 
                                                algorithm, highConfRatio * 100));
                BenchmarkResult result = runSingleBenchmark(algorithm, daxFile, 3000, highConfRatio);
                saveResult("SecurityLevel", algorithm, 100, 3000, highConfRatio, result);
            }
        }
    }
    
    private static String getDaxFileForTaskCount(int taskCount) {
        if (taskCount <= 30) return "config/dax/Montage_25.xml";
        if (taskCount <= 50) return "config/dax/Montage_50.xml";
        return "config/dax/Montage_100.xml";
    }
    
    private static BenchmarkResult runSingleBenchmark(String algorithm, String daxFile, 
                                                      int cloudMips, double highConfRatio) throws Exception {
        // Suppress CloudSim logs
        Log.disable();
        
        int numUsers = 1;
        Calendar calendar = Calendar.getInstance();
        CloudSim.init(numUsers, calendar, false);
        
        // Create VMs with specified cloud MIPS
        List<CondorVM> vmList = createVMs(cloudMips);
        
        // Create datacenter
        WorkflowDatacenter datacenter = createDatacenter("Datacenter_0", vmList.size());
        
        // Create workflow engine
        WorkflowEngine wfEngine = new WorkflowEngine("WorkflowEngine", 10.0);
        wfEngine.submitVmList(vmList, 0);
        wfEngine.bindSchedulerDatacenter(datacenter.getId(), 0);
        
        // Set algorithm
        Parameters.SchedulingAlgorithm schedulingAlgorithm = 
            Parameters.SchedulingAlgorithm.valueOf(algorithm);
        Parameters.setSchedulingAlgorithm(schedulingAlgorithm);
        
        // Configure clustering and overheads
        Parameters.OverheadParams overheadParams = new Parameters.OverheadParams(0, null, null, null, null, 0);
        OverheadParameters.setOverheadParams(overheadParams);
        ReplicaCatalog.init(new HashMap<>());
        ClusteringParameters.init();
        Parameters.init();
        
        // Create planner
        WorkflowPlanner planner = new WorkflowPlanner("Planner", 1);
        planner.addWorkflow(wfEngine.getWorkflowList().get(0), daxFile);
        
        // Configure high-conf ratio (simplified for benchmark)
        BENCHMARK_HIGH_CONF_RATIO = highConfRatio;
        
        // Run simulation
        CloudSim.startSimulation();
        
        // Collect results
        List<Job> jobList = wfEngine.getJobsReceivedList();
        BenchmarkResult result = analyzeResults(jobList, vmList);
        
        CloudSim.stopSimulation();
        Log.enable();
        
        return result;
    }
    
    public static double BENCHMARK_HIGH_CONF_RATIO = 0.6;
    
    private static List<CondorVM> createVMs(int cloudMips) {
        List<CondorVM> vmList = new ArrayList<>();
        int vmId = 0;
        
        // Tier 1: Mist (2 VMs)
        for (int i = 0; i < 2; i++) {
            SCEAHVM vm = new SCEAHVM(vmId++, 0, MIST_MIPS, 1, 1024, 10000, 10000, "Xen",
                                    new org.cloudbus.cloudsim.CloudletSchedulerSpaceShared());
            vm.setTier(1);
            vm.setCostPerSecond(0.05 / 3600.0);
            vm.setTdpWatts(100.0);
            vm.setTrustScore(0.90);
            vmList.add(vm);
        }
        
        // Tier 2: Fog (2 VMs)
        for (int i = 0; i < 2; i++) {
            SCEAHVM vm = new SCEAHVM(vmId++, 0, FOG_MIPS, 1, 2048, 20000, 20000, "Xen",
                                    new org.cloudbus.cloudsim.CloudletSchedulerSpaceShared());
            vm.setTier(2);
            vm.setCostPerSecond(0.15 / 3600.0);
            vm.setTdpWatts(200.0);
            vm.setTrustScore(0.65);
            vmList.add(vm);
        }
        
        // Tier 3: Cloud (1 VM) - variable MIPS
        SCEAHVM vm = new SCEAHVM(vmId++, 0, cloudMips, 1, 4096, 50000, 50000, "Xen",
                                new org.cloudbus.cloudsim.CloudletSchedulerSpaceShared());
        vm.setTier(3);
        vm.setCostPerSecond(0.30 / 3600.0);
        vm.setTdpWatts(300.0);
        vm.setTrustScore(0.45);
        vmList.add(vm);
        
        return vmList;
    }
    
    private static WorkflowDatacenter createDatacenter(String name, int numVMs) {
        List<Host> hostList = new ArrayList<>();
        
        for (int i = 0; i < numVMs; i++) {
            List<Pe> peList = new ArrayList<>();
            peList.add(new Pe(0, new PeProvisionerSimple(10000)));
            
            hostList.add(new Host(i, new RamProvisionerSimple(8192),
                                 new BwProvisionerSimple(100000), 1000000, peList,
                                 new VmSchedulerTimeShared(peList)));
        }
        
        String arch = "x86";
        String os = "Linux";
        String vmm = "Xen";
        double timeZone = 10.0;
        double costPerSec = 0.0;
        double costPerMem = 0.0;
        double costPerStorage = 0.0;
        double costPerBw = 0.0;
        LinkedList<Storage> storageList = new LinkedList<>();
        
        org.cloudbus.cloudsim.DatacenterCharacteristics characteristics = 
            new org.cloudbus.cloudsim.DatacenterCharacteristics(arch, os, vmm, hostList, 
                                                                timeZone, costPerSec, 
                                                                costPerMem, costPerStorage, costPerBw);
        
        try {
            return new WorkflowDatacenter(name, characteristics, 
                                         new VmAllocationPolicySimple(hostList), 
                                         storageList, 0);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    private static BenchmarkResult analyzeResults(List<Job> jobList, List<CondorVM> vmList) {
        BenchmarkResult result = new BenchmarkResult();
        
        double totalCost = 0;
        double totalEnergy = 0;
        double maxFinishTime = 0;
        int[] tierCounts = new int[4];
        int fragmentedJobs = 0;
        double totalReliability = 0;
        int successfulJobs = 0;
        
        for (Job job : jobList) {
            if (job.getCloudletStatus() == Job.SUCCESS) {
                successfulJobs++;
                
                // Find VM
                SCEAHVM vm = null;
                for (CondorVM v : vmList) {
                    if (v.getId() == job.getVmId()) {
                        vm = (SCEAHVM) v;
                        break;
                    }
                }
                
                if (vm != null) {
                    double execTime = job.getActualCPUTime();
                    totalCost += execTime * vm.getCostPerSecond();
                    totalEnergy += (vm.getTdpWatts() * execTime) / 3600000.0;
                    
                    tierCounts[vm.getTier()]++;
                    
                    // Calculate reliability (Weibull)
                    double lambda = 1e-5;
                    double beta = 1.0;
                    double reliability = Math.exp(-Math.pow(lambda * execTime, beta));
                    totalReliability += reliability;
                }
                
                double finishTime = job.getFinishTime();
                if (finishTime > maxFinishTime) {
                    maxFinishTime = finishTime;
                }
                
                // Check if fragmented (simplified: depth > 10)
                if (job.getDepth() > 10) {
                    fragmentedJobs++;
                }
            }
        }
        
        result.makespan = maxFinishTime;
        result.cost = totalCost;
        result.energy = totalEnergy;
        result.reliability = (totalReliability / successfulJobs) * 100;
        result.rsr = (successfulJobs >= jobList.size() * 0.95) ? 100.0 : 0.0;
        result.mistJobs = tierCounts[1];
        result.fogJobs = tierCounts[2];
        result.cloudJobs = tierCounts[3];
        result.fragmentedJobs = fragmentedJobs;
        
        // Calculate security score
        result.security = (double)(tierCounts[1] * 3 + tierCounts[2] * 2 + tierCounts[3] * 1) / successfulJobs;
        
        return result;
    }
    
    private static void saveResult(String benchmark, String algorithm, int taskCount, 
                                   int cloudMips, double highConfRatio, BenchmarkResult result) 
                                   throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(CSV_FILE, true))) {
            writer.println(String.format("%s,%s,%d,%d,%.2f,%.2f,%.4f,%.4f,%.4f,%.2f,%.2f,%d,%d,%d,%d",
                benchmark, algorithm, taskCount, cloudMips, highConfRatio,
                result.makespan, result.cost, result.energy, result.security,
                result.reliability, result.rsr,
                result.mistJobs, result.fogJobs, result.cloudJobs, result.fragmentedJobs));
        }
    }
    
    static class BenchmarkResult {
        double makespan;
        double cost;
        double energy;
        double security;
        double reliability;
        double rsr;
        int mistJobs;
        int fogJobs;
        int cloudJobs;
        int fragmentedJobs;
    }
}
