package org.workflowsim.benchmark;

import org.workflowsim.examples.scheduling.FATSSchedulingAlgorithmExample;
import org.workflowsim.examples.scheduling.SCEAHSchedulingAlgorithmExample;
import org.workflowsim.examples.scheduling.RCSECHSchedulingAlgorithmExample;

import java.io.*;

/**
 * Simple Benchmark Runner
 * Runs all three schedulers multiple times and collects results for thesis graphs
 */
public class SimpleBenchmarkRunner {
    
    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(80));
        System.out.println("PhD THESIS BENCHMARK RUNNER");
        System.out.println("=".repeat(80));
        
        // Create output directory
        new File("benchmark/results").mkdirs();
        
        // Initialize CSV
        PrintWriter csv = new PrintWriter(new FileWriter("benchmark/results/simple_results.csv"));
        csv.println("Algorithm,Run,Makespan,Cost,Energy,Security,Reliability");
        
        // Run each algorithm 5 times for statistical significance
        int runs = 5;
        
        for (int i = 1; i <= runs; i++) {
            System.out.println("\n[RUN " + i + "/" + runs + "]");
            
            System.out.println("  Running SCEAH...");
            runSCEAH(csv, i);
            
            System.out.println("  Running RCSECH...");
            runRCSECH(csv, i);
            
            System.out.println("  Running FATS...");
            runFATS(csv, i);
        }
        
        csv.close();
        System.out.println("\n" + "=".repeat(80));
        System.out.println("Benchmark complete! Results: benchmark/results/simple_results.csv");
        System.out.println("Run: python benchmark/generate_simple_graphs.py");
        System.out.println("=".repeat(80));
    }
    
    private static void runSCEAH(PrintWriter csv, int run) throws Exception {
        // Redirect stdout to capture metrics
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        PrintStream old = System.out;
        System.setOut(ps);
        
        try {
            SCEAHSchedulingAlgorithmExample.main(new String[]{});
        } catch (Exception e) {
            // Ignore
        }
        
        System.out.flush();
        System.setOut(old);
        
        String output = baos.toString();
        BenchmarkMetrics metrics = parseOutput(output, "SCEAH");
        csv.println(String.format("SCEAH,%d,%.2f,%.4f,%.4f,%.2f,%.2f",
                run, metrics.makespan, metrics.cost, metrics.energy, metrics.security, metrics.reliability));
        csv.flush();
    }
    
    private static void runRCSECH(PrintWriter csv, int run) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        PrintStream old = System.out;
        System.setOut(ps);
        
        try {
            RCSECHSchedulingAlgorithmExample.main(new String[]{});
        } catch (Exception e) {
            // Ignore
        }
        
        System.out.flush();
        System.setOut(old);
        
        String output = baos.toString();
        BenchmarkMetrics metrics = parseOutput(output, "RCSECH");
        csv.println(String.format("RCSECH,%d,%.2f,%.4f,%.4f,%.2f,%.2f",
                run, metrics.makespan, metrics.cost, metrics.energy, metrics.security, metrics.reliability));
        csv.flush();
    }
    
    private static void runFATS(PrintWriter csv, int run) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        PrintStream old = System.out;
        System.setOut(ps);
        
        try {
            FATSSchedulingAlgorithmExample.main(new String[]{});
        } catch (Exception e) {
            // Ignore
        }
        
        System.out.flush();
        System.setOut(old);
        
        String output = baos.toString();
        BenchmarkMetrics metrics = parseOutput(output, "FATS");
        csv.println(String.format("FATS,%d,%.2f,%.4f,%.4f,%.2f,%.2f",
                run, metrics.makespan, metrics.cost, metrics.energy, metrics.security, metrics.reliability));
        csv.flush();
    }
    
    private static BenchmarkMetrics parseOutput(String output, String algorithm) {
        BenchmarkMetrics metrics = new BenchmarkMetrics();
        
        // Parse makespan
        String[] lines = output.split("\n");
        for (String line : lines) {
            if (line.contains("Makespan:")) {
                try {
                    String value = line.split(":")[1].trim().split(" ")[0];
                    metrics.makespan = Double.parseDouble(value);
                } catch (Exception e) {}
            }
            else if (line.contains("Total Cost:")) {
                try {
                    String value = line.split("\\$")[1].trim().split(" ")[0];
                    metrics.cost = Double.parseDouble(value);
                } catch (Exception e) {}
            }
            else if (line.contains("Total Energy:")) {
                try {
                    String value = line.split(":")[1].trim().split(" ")[0];
                    metrics.energy = Double.parseDouble(value);
                } catch (Exception e) {}
            }
            else if (line.contains("Average Security Score:")) {
                try {
                    String value = line.split(":")[1].trim().split(" ")[0];
                    metrics.security = Double.parseDouble(value);
                } catch (Exception e) {}
            }
            else if (line.contains("Workflow Reliability:")) {
                try {
                    String value = line.split(":")[1].trim().split("%")[0];
                    metrics.reliability = Double.parseDouble(value);
                } catch (Exception e) {}
            }
        }
        
        return metrics;
    }
    
    static class BenchmarkMetrics {
        double makespan = 0;
        double cost = 0;
        double energy = 0;
        double security = 0;
        double reliability = 0;
    }
}
