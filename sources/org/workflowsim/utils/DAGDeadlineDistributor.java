package org.workflowsim.utils;

import org.workflowsim.SecureReliableTask;

import java.util.*;

/**
 * Distributes deadlines to tasks in a DAG based on a deadline distribution strategy.
 * Assumes tasks have a level, execution time, and a placeholder for deadline assignment.
 */
public class DAGDeadlineDistributor {

    private final double workflowDeadline;
    private final double workflowArrival;
    private final List<SecureReliableTask> tasks;

    public DAGDeadlineDistributor(List<SecureReliableTask> tasks, double workflowDeadline, double workflowArrival) {
        this.tasks = tasks;
        this.workflowDeadline = workflowDeadline;
        this.workflowArrival = workflowArrival;
    }

    public void distributeDeadlines() {
        // 1. Assign Level to Each Task
        assignLevels();

        // 2. Group Tasks by Level
        Map<Integer, List<SecureReliableTask>> levelGroups = groupTasksByLevel();

        // 3. Compute total execution time Θᶻ
        double totalTheta = computeTotalTheta();

        // 4. Calculate Θ(GL) for each level
        Map<Integer, Double> levelThetaMap = new HashMap<>();
        for (Map.Entry<Integer, List<SecureReliableTask>> entry : levelGroups.entrySet()) {
            double thetaGL = 0;
            for (SecureReliableTask t : entry.getValue()) {
                thetaGL += t.getExecutionTime();  // CP_t
            }
            levelThetaMap.put(entry.getKey(), thetaGL);
        }

        // 5. Compute Dl(GL) and assign deadline to each task
        Map<Integer, Double> levelDeadlineMap = new HashMap<>();
        for (Map.Entry<Integer, List<SecureReliableTask>> entry : levelGroups.entrySet()) {
            int level = entry.getKey();
            double prevDl = (level == 1) ? workflowArrival : levelDeadlineMap.get(level - 1);
            double currentTheta = levelThetaMap.get(level);

            double dlGL = prevDl + currentTheta * (workflowDeadline - workflowArrival) / totalTheta;
            levelDeadlineMap.put(level, dlGL);

            for (SecureReliableTask t : entry.getValue()) {
                double individualDl = dlGL + t.getLFT() / 2; // Eq. 38: harmonized deadline
                t.setDeadline(individualDl);
            }
        }
    }

    /**
     * Assigns a level to each task. This is a simplified placeholder. Ideally, you should
     * compute levels based on dependencies (e.g., BFS or topological sort).
     */
    private void assignLevels() {
        // Simple example: assign level = 1 to all
        for (SecureReliableTask task : tasks) {
            task.setLevel(1); // Replace with proper logic if dependencies exist
        }
    }

    private Map<Integer, List<SecureReliableTask>> groupTasksByLevel() {
        Map<Integer, List<SecureReliableTask>> levelGroups = new TreeMap<>();
        for (SecureReliableTask task : tasks) {
            int level = task.getLevel();
            levelGroups.computeIfAbsent(level, k -> new ArrayList<>()).add(task);
        }
        return levelGroups;
    }

    private double computeTotalTheta() {
        double total = 0;
        for (SecureReliableTask task : tasks) {
            total += task.getExecutionTime();
        }
        return total;
    }
}
