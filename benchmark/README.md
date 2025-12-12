# PhD Thesis Benchmark Suite

Comprehensive benchmark framework for comparing fog computing schedulers: **SCEAH**, **RCSECH**, and **FATS**.

## Overview

This benchmark suite evaluates three scheduling algorithms across multiple dimensions:

### Algorithms Under Test
1. **SCEAH** - Security, Cost, Energy-Aware Heuristic (Baseline)
2. **RCSECH** - Reliability-Constrained Security, Energy, Cost Heuristic (Energy-Optimized)
3. **FATS** - Fragmented and Aggregated Task Scheduling (Security-First)

### Benchmark Categories

#### 1. Scalability Analysis
- **Variable**: Number of tasks (30, 50, 100)
- **Fixed**: Cloud MIPS = 3000, High-Conf Ratio = 60%
- **Purpose**: Evaluate how algorithms scale with workflow size

#### 2. Cloud Performance Impact
- **Variable**: Cloud MIPS (2000, 3000, 5000)
- **Fixed**: Task count = 100, High-Conf Ratio = 60%
- **Purpose**: Assess impact of cloud tier performance on scheduling decisions

#### 3. Security Level Impact
- **Variable**: High-Confidentiality Job Ratio (20%, 40%, 60%, 80%)
- **Fixed**: Task count = 100, Cloud MIPS = 3000
- **Purpose**: Validate algorithm behavior under varying security requirements

## Setup & Execution

### Prerequisites
```bash
# Java (already configured)
java -version  # Should show JDK 22.0.2

# Python for graph generation
pip install pandas matplotlib seaborn numpy
```

### Step 1: Compile Benchmark Suite
```bash
javac -cp "lib/*;bin" -d bin benchmark/BenchmarkSuite.java
```

### Step 2: Run Benchmarks
```bash
java -cp "lib/*;bin" org.workflowsim.benchmark.BenchmarkSuite
```

**Expected Runtime**: ~5-10 minutes (27 total benchmark runs)
- 9 runs for Scalability (3 task counts × 3 algorithms)
- 9 runs for Cloud MIPS (3 MIPS values × 3 algorithms)
- 9 runs for Security Level (3 ratios × 3 algorithms)

### Step 3: Generate Graphs
```bash
python benchmark/generate_graphs.py
```

## Output Files

### Benchmark Results
- `benchmark/results/benchmark_results.csv` - Raw benchmark data

### Generated Graphs (300 DPI PNG + Vector PDF)

1. **fig1_scalability_makespan.png**
   - Line chart: Makespan vs Task Count
   - Shows algorithm scalability characteristics

2. **fig2_scalability_energy_cost.png**
   - Dual line charts: Energy & Cost vs Task Count
   - Compares resource consumption scaling

3. **fig3_cloud_mips_comparison.png**
   - 4-panel bar chart: Makespan, Energy, Cost, Security vs Cloud MIPS
   - Demonstrates cloud performance impact

4. **fig4_security_level_impact.png**
   - 4-panel line charts: All metrics vs High-Conf Ratio
   - Validates security requirement handling

5. **fig5_tier_distribution.png**
   - 3-panel stacked bar chart: Mist/Fog/Cloud distribution
   - Shows tier selection patterns across all benchmarks

6. **fig6_security_energy_tradeoff.png**
   - Scatter plot: Security Score vs Energy Consumption
   - Visualizes multi-objective optimization trade-offs

7. **fig7_reliability_comparison.png**
   - Box plot: Reliability distribution across all benchmarks
   - Demonstrates 95% reliability constraint satisfaction

8. **summary_table.tex**
   - LaTeX table: Mean ± Std for all metrics
   - Ready for thesis inclusion

## Metrics Collected

For each benchmark run, the following metrics are recorded:

| Metric | Description | Unit |
|--------|-------------|------|
| Makespan | Workflow completion time | seconds |
| Cost | Total execution cost | $ |
| Energy | Total energy consumption | kWh |
| Security | Weighted security score (Mist=3, Fog=2, Cloud=1) | score |
| Reliability | Workflow-level reliability (Weibull) | % |
| RSR | Reliability Success Rate (≥95% threshold) | % |
| Mist Jobs | Jobs executed on Mist tier | count |
| Fog Jobs | Jobs executed on Fog tier | count |
| Cloud Jobs | Jobs executed on Cloud tier | count |
| Fragmented | Jobs that were fragmented | count |

## VM Configuration

### 3-Tier Fog Architecture

| Tier | VMs | MIPS | Cost/hr | TDP (W) | Trust Score |
|------|-----|------|---------|---------|-------------|
| Mist (T1) | 2 | 1000 | $0.05 | 100 | 0.90 |
| Fog (T2) | 2 | 2000 | $0.15 | 200 | 0.65 |
| Cloud (T3) | 1 | Variable* | $0.30 | 300 | 0.45 |

*Cloud MIPS varies in CloudMIPS benchmark (2000/3000/5000), fixed at 3000 otherwise.

## Expected Results Summary

Based on algorithm design characteristics:

### FATS (Security-First)
- ✅ **Highest Security Score** (~2.4-2.5)
- ✅ **Lowest Cost** (prefers cheap Mist tier)
- ⚠️ **Higher Makespan** (fragmentation overhead)
- ✅ **Mist-Dominant Distribution** (~60% Mist)

### RCSECH (Energy-Optimized)
- ✅ **Lowest Energy Consumption** (~80% savings vs SCEAH)
- ✅ **Highest Reliability** (multi-objective optimization)
- ⚠️ **Cloud-Heavy Distribution** (~30% Cloud)
- ⚠️ **Lower Security Score** (~1.7)

### SCEAH (Baseline)
- ✅ **Fastest Makespan** (no fragmentation)
- ⚠️ **Highest Energy** (no energy awareness)
- ⚠️ **Medium Security** (~2.0)
- ✅ **Balanced Tier Distribution**

## Thesis Integration

### Recommended Graph Usage

**Chapter: Algorithm Design**
- Fig 5: Shows tier distribution differences between algorithms

**Chapter: Performance Evaluation**
- Fig 1: Scalability validation
- Fig 2: Resource consumption analysis
- Fig 7: Reliability constraint satisfaction

**Chapter: Trade-off Analysis**
- Fig 6: Security-Energy Pareto frontier
- Fig 4: Security requirement sensitivity

**Chapter: Cloud Performance Impact**
- Fig 3: Heterogeneity handling

**Chapter: Comparative Analysis**
- Summary Table: Statistical comparison

### LaTeX Integration Example

```latex
\begin{figure}[htbp]
  \centering
  \includegraphics[width=0.9\textwidth]{benchmark/graphs/fig1_scalability_makespan.pdf}
  \caption{Algorithm scalability comparison: makespan vs task count. 
           FATS demonstrates acceptable scalability despite fragmentation overhead.}
  \label{fig:scalability}
\end{figure}
```

## Troubleshooting

### Issue: "benchmark_results.csv not found"
- **Solution**: Run `BenchmarkSuite.java` first before generating graphs

### Issue: Python import errors
- **Solution**: `pip install pandas matplotlib seaborn numpy`

### Issue: OutOfMemoryError during benchmark
- **Solution**: Increase Java heap: `java -Xmx4g -cp "lib/*;bin" org.workflowsim.benchmark.BenchmarkSuite`

### Issue: Graphs look blurry
- **Solution**: Use PDF versions for thesis (vector graphics, infinite zoom)

## Customization

### Add More Task Counts
Edit `BenchmarkSuite.java`:
```java
private static final int[] TASK_COUNTS = {30, 50, 100, 200, 500};
```

### Change Graph Colors
Edit `generate_graphs.py`:
```python
COLORS = {
    'SCEAH': '#1f77b4',   # Blue
    'RCSECH': '#2ca02c',  # Green
    'FATS': '#d62728'     # Red
}
```

### Add Custom Metrics
1. Modify `BenchmarkResult` class in `BenchmarkSuite.java`
2. Update CSV header in `initializeCSV()`
3. Calculate metric in `analyzeResults()`
4. Add graph function in `generate_graphs.py`

## Citation

If using this benchmark framework in publications:

```bibtex
@article{fats2024,
  title={Fragmented and Aggregated Task Scheduling in Fog Computing},
  author={[Your Name]},
  journal={[Journal Name]},
  year={2024}
}
```

## Contact & Support

For questions or issues with the benchmark suite, please refer to the main WorkflowSim documentation or contact the research team.
