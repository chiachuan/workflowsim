"""
PhD Thesis Benchmark Graph Generator
Generates publication-quality comparison graphs for fog computing schedulers
"""

import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
import numpy as np
from matplotlib.patches import Patch
import os

# Set publication-quality style
sns.set_style("whitegrid")
plt.rcParams['figure.figsize'] = (12, 8)
plt.rcParams['font.size'] = 11
plt.rcParams['axes.labelsize'] = 12
plt.rcParams['axes.titlesize'] = 14
plt.rcParams['legend.fontsize'] = 10
plt.rcParams['xtick.labelsize'] = 10
plt.rcParams['ytick.labelsize'] = 10

# Color palette for algorithms
COLORS = {
    'SCEAH': '#1f77b4',   # Blue - baseline
    'RCSECH': '#2ca02c',  # Green - energy-optimized
    'FATS': '#d62728'     # Red - security-first
}

def load_data(csv_file='benchmark/results/benchmark_results.csv'):
    """Load benchmark results"""
    if not os.path.exists(csv_file):
        print(f"Error: {csv_file} not found. Run BenchmarkSuite first.")
        return None
    return pd.read_csv(csv_file)

def create_output_dir():
    """Create output directory for graphs"""
    os.makedirs('benchmark/graphs', exist_ok=True)

def plot_scalability_makespan(df):
    """Figure 1: Scalability - Makespan vs Task Count"""
    fig, ax = plt.subplots(figsize=(10, 6))
    
    scalability_data = df[df['Benchmark'] == 'Scalability']
    
    for algorithm in ['SCEAH', 'RCSECH', 'FATS']:
        data = scalability_data[scalability_data['Algorithm'] == algorithm]
        ax.plot(data['TaskCount'], data['Makespan'], 
               marker='o', linewidth=2.5, markersize=8,
               label=algorithm, color=COLORS[algorithm])
    
    ax.set_xlabel('Number of Tasks', fontweight='bold')
    ax.set_ylabel('Makespan (seconds)', fontweight='bold')
    ax.set_title('Algorithm Scalability: Makespan vs Task Count', fontweight='bold', pad=20)
    ax.legend(loc='upper left', frameon=True, shadow=True)
    ax.grid(True, alpha=0.3)
    
    plt.tight_layout()
    plt.savefig('benchmark/graphs/fig1_scalability_makespan.png', dpi=300, bbox_inches='tight')
    plt.savefig('benchmark/graphs/fig1_scalability_makespan.pdf', bbox_inches='tight')
    print("✓ Generated: fig1_scalability_makespan.png")
    plt.close()

def plot_scalability_energy_cost(df):
    """Figure 2: Scalability - Energy & Cost vs Task Count"""
    scalability_data = df[df['Benchmark'] == 'Scalability']
    
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(14, 5))
    
    # Energy subplot
    for algorithm in ['SCEAH', 'RCSECH', 'FATS']:
        data = scalability_data[scalability_data['Algorithm'] == algorithm]
        ax1.plot(data['TaskCount'], data['Energy'], 
                marker='s', linewidth=2.5, markersize=8,
                label=algorithm, color=COLORS[algorithm])
    
    ax1.set_xlabel('Number of Tasks', fontweight='bold')
    ax1.set_ylabel('Energy Consumption (kWh)', fontweight='bold')
    ax1.set_title('Energy Consumption vs Task Count', fontweight='bold', pad=15)
    ax1.legend(loc='upper left', frameon=True, shadow=True)
    ax1.grid(True, alpha=0.3)
    
    # Cost subplot
    for algorithm in ['SCEAH', 'RCSECH', 'FATS']:
        data = scalability_data[scalability_data['Algorithm'] == algorithm]
        ax2.plot(data['TaskCount'], data['Cost'], 
                marker='^', linewidth=2.5, markersize=8,
                label=algorithm, color=COLORS[algorithm])
    
    ax2.set_xlabel('Number of Tasks', fontweight='bold')
    ax2.set_ylabel('Total Cost ($)', fontweight='bold')
    ax2.set_title('Total Cost vs Task Count', fontweight='bold', pad=15)
    ax2.legend(loc='upper left', frameon=True, shadow=True)
    ax2.grid(True, alpha=0.3)
    
    plt.tight_layout()
    plt.savefig('benchmark/graphs/fig2_scalability_energy_cost.png', dpi=300, bbox_inches='tight')
    plt.savefig('benchmark/graphs/fig2_scalability_energy_cost.pdf', bbox_inches='tight')
    print("✓ Generated: fig2_scalability_energy_cost.png")
    plt.close()

def plot_cloud_mips_comparison(df):
    """Figure 3: Cloud MIPS Impact - Bar Chart Comparison"""
    cloud_data = df[df['Benchmark'] == 'CloudMIPS']
    
    metrics = ['Makespan', 'Energy', 'Cost', 'Security']
    fig, axes = plt.subplots(2, 2, figsize=(14, 10))
    axes = axes.flatten()
    
    mips_values = sorted(cloud_data['CloudMIPS'].unique())
    x = np.arange(len(mips_values))
    width = 0.25
    
    for idx, metric in enumerate(metrics):
        ax = axes[idx]
        
        for i, algorithm in enumerate(['SCEAH', 'RCSECH', 'FATS']):
            data = cloud_data[cloud_data['Algorithm'] == algorithm]
            values = [data[data['CloudMIPS'] == mips][metric].values[0] for mips in mips_values]
            ax.bar(x + i*width, values, width, label=algorithm, color=COLORS[algorithm], alpha=0.8)
        
        ax.set_xlabel('Cloud MIPS', fontweight='bold')
        ax.set_ylabel(metric, fontweight='bold')
        ax.set_title(f'{metric} vs Cloud Performance', fontweight='bold', pad=10)
        ax.set_xticks(x + width)
        ax.set_xticklabels([f'{m}' for m in mips_values])
        ax.legend(loc='best', frameon=True, shadow=True)
        ax.grid(True, alpha=0.3, axis='y')
    
    plt.tight_layout()
    plt.savefig('benchmark/graphs/fig3_cloud_mips_comparison.png', dpi=300, bbox_inches='tight')
    plt.savefig('benchmark/graphs/fig3_cloud_mips_comparison.pdf', bbox_inches='tight')
    print("✓ Generated: fig3_cloud_mips_comparison.png")
    plt.close()

def plot_security_level_impact(df):
    """Figure 4: Security Level Impact - Line Charts"""
    security_data = df[df['Benchmark'] == 'SecurityLevel']
    
    fig, axes = plt.subplots(2, 2, figsize=(14, 10))
    axes = axes.flatten()
    
    metrics = [
        ('Security', 'Security Score'),
        ('Energy', 'Energy Consumption (kWh)'),
        ('Cost', 'Total Cost ($)'),
        ('Makespan', 'Makespan (seconds)')
    ]
    
    for idx, (metric, ylabel) in enumerate(metrics):
        ax = axes[idx]
        
        for algorithm in ['SCEAH', 'RCSECH', 'FATS']:
            data = security_data[security_data['Algorithm'] == algorithm]
            high_conf_pct = data['HighConfRatio'] * 100
            ax.plot(high_conf_pct, data[metric], 
                   marker='o', linewidth=2.5, markersize=8,
                   label=algorithm, color=COLORS[algorithm])
        
        ax.set_xlabel('High-Confidentiality Jobs (%)', fontweight='bold')
        ax.set_ylabel(ylabel, fontweight='bold')
        ax.set_title(f'{ylabel.split(" (")[0]} vs Security Requirements', fontweight='bold', pad=10)
        ax.legend(loc='best', frameon=True, shadow=True)
        ax.grid(True, alpha=0.3)
    
    plt.tight_layout()
    plt.savefig('benchmark/graphs/fig4_security_level_impact.png', dpi=300, bbox_inches='tight')
    plt.savefig('benchmark/graphs/fig4_security_level_impact.pdf', bbox_inches='tight')
    print("✓ Generated: fig4_security_level_impact.png")
    plt.close()

def plot_tier_distribution(df):
    """Figure 5: Tier Distribution - Stacked Bar Chart"""
    fig, axes = plt.subplots(1, 3, figsize=(16, 5))
    
    benchmarks = ['Scalability', 'CloudMIPS', 'SecurityLevel']
    titles = ['Task Count Variation', 'Cloud MIPS Variation', 'Security Level Variation']
    
    for idx, (benchmark, title) in enumerate(zip(benchmarks, titles)):
        ax = axes[idx]
        data = df[df['Benchmark'] == benchmark]
        
        algorithms = ['SCEAH', 'RCSECH', 'FATS']
        
        # Get unique x-axis values
        if benchmark == 'Scalability':
            x_values = sorted(data['TaskCount'].unique())
            x_label = 'Task Count'
        elif benchmark == 'CloudMIPS':
            x_values = sorted(data['CloudMIPS'].unique())
            x_label = 'Cloud MIPS'
        else:
            x_values = sorted(data['HighConfRatio'].unique())
            x_label = 'High-Conf Ratio'
        
        x = np.arange(len(x_values))
        width = 0.25
        
        for i, algorithm in enumerate(algorithms):
            algo_data = data[data['Algorithm'] == algorithm]
            
            mist_vals = []
            fog_vals = []
            cloud_vals = []
            
            for val in x_values:
                if benchmark == 'Scalability':
                    row = algo_data[algo_data['TaskCount'] == val]
                elif benchmark == 'CloudMIPS':
                    row = algo_data[algo_data['CloudMIPS'] == val]
                else:
                    row = algo_data[algo_data['HighConfRatio'] == val]
                
                if len(row) > 0:
                    total = row['MistJobs'].values[0] + row['FogJobs'].values[0] + row['CloudJobs'].values[0]
                    mist_vals.append(row['MistJobs'].values[0] / total * 100 if total > 0 else 0)
                    fog_vals.append(row['FogJobs'].values[0] / total * 100 if total > 0 else 0)
                    cloud_vals.append(row['CloudJobs'].values[0] / total * 100 if total > 0 else 0)
            
            # Stacked bars
            p1 = ax.bar(x + i*width, mist_vals, width, label='Mist' if i == 0 else "", 
                       color='#90EE90', alpha=0.8)
            p2 = ax.bar(x + i*width, fog_vals, width, bottom=mist_vals, 
                       label='Fog' if i == 0 else "", color='#87CEEB', alpha=0.8)
            p3 = ax.bar(x + i*width, cloud_vals, width, 
                       bottom=[m+f for m,f in zip(mist_vals, fog_vals)],
                       label='Cloud' if i == 0 else "", color='#FFB6C1', alpha=0.8)
            
            # Add algorithm label on top
            for j, val in enumerate(x):
                ax.text(val + i*width, 102, algorithm, ha='center', va='bottom', 
                       fontsize=8, fontweight='bold', color=COLORS[algorithm])
        
        ax.set_xlabel(x_label, fontweight='bold')
        ax.set_ylabel('Job Distribution (%)', fontweight='bold')
        ax.set_title(f'Tier Distribution: {title}', fontweight='bold', pad=20)
        ax.set_xticks(x + width)
        if benchmark == 'SecurityLevel':
            ax.set_xticklabels([f'{v*100:.0f}%' for v in x_values])
        else:
            ax.set_xticklabels([str(v) for v in x_values])
        ax.set_ylim(0, 115)
        ax.legend(loc='upper right', frameon=True, shadow=True)
        ax.grid(True, alpha=0.3, axis='y')
    
    plt.tight_layout()
    plt.savefig('benchmark/graphs/fig5_tier_distribution.png', dpi=300, bbox_inches='tight')
    plt.savefig('benchmark/graphs/fig5_tier_distribution.pdf', bbox_inches='tight')
    print("✓ Generated: fig5_tier_distribution.png")
    plt.close()

def plot_security_energy_tradeoff(df):
    """Figure 6: Security vs Energy Trade-off - Scatter Plot"""
    fig, ax = plt.subplots(figsize=(10, 7))
    
    for algorithm in ['SCEAH', 'RCSECH', 'FATS']:
        data = df[df['Algorithm'] == algorithm]
        ax.scatter(data['Security'], data['Energy'], 
                  s=200, alpha=0.7, label=algorithm, color=COLORS[algorithm],
                  edgecolors='black', linewidths=1.5)
    
    ax.set_xlabel('Security Score', fontweight='bold', fontsize=13)
    ax.set_ylabel('Energy Consumption (kWh)', fontweight='bold', fontsize=13)
    ax.set_title('Security-Energy Trade-off Analysis', fontweight='bold', fontsize=15, pad=20)
    ax.legend(loc='best', frameon=True, shadow=True, fontsize=12)
    ax.grid(True, alpha=0.3)
    
    # Add annotations
    for algorithm in ['SCEAH', 'RCSECH', 'FATS']:
        data = df[df['Algorithm'] == algorithm]
        avg_security = data['Security'].mean()
        avg_energy = data['Energy'].mean()
        ax.annotate(f'{algorithm}\navg', xy=(avg_security, avg_energy),
                   xytext=(10, 10), textcoords='offset points',
                   bbox=dict(boxstyle='round,pad=0.5', fc=COLORS[algorithm], alpha=0.3),
                   fontsize=9, fontweight='bold')
    
    plt.tight_layout()
    plt.savefig('benchmark/graphs/fig6_security_energy_tradeoff.png', dpi=300, bbox_inches='tight')
    plt.savefig('benchmark/graphs/fig6_security_energy_tradeoff.pdf', bbox_inches='tight')
    print("✓ Generated: fig6_security_energy_tradeoff.png")
    plt.close()

def plot_reliability_comparison(df):
    """Figure 7: Reliability Comparison - Box Plot"""
    fig, ax = plt.subplots(figsize=(10, 6))
    
    data_to_plot = []
    labels = []
    colors_list = []
    
    for algorithm in ['SCEAH', 'RCSECH', 'FATS']:
        data = df[df['Algorithm'] == algorithm]['Reliability'].values
        data_to_plot.append(data)
        labels.append(algorithm)
        colors_list.append(COLORS[algorithm])
    
    bp = ax.boxplot(data_to_plot, labels=labels, patch_artist=True,
                    notch=True, showmeans=True,
                    meanprops=dict(marker='D', markerfacecolor='red', markersize=8))
    
    for patch, color in zip(bp['boxes'], colors_list):
        patch.set_facecolor(color)
        patch.set_alpha(0.7)
    
    ax.set_ylabel('Reliability (%)', fontweight='bold')
    ax.set_title('Reliability Comparison Across All Benchmarks', fontweight='bold', pad=20)
    ax.grid(True, alpha=0.3, axis='y')
    ax.set_ylim(95, 100.5)
    
    # Add 95% threshold line
    ax.axhline(y=95, color='r', linestyle='--', linewidth=2, alpha=0.5, label='95% Threshold')
    ax.legend(loc='lower right', frameon=True, shadow=True)
    
    plt.tight_layout()
    plt.savefig('benchmark/graphs/fig7_reliability_comparison.png', dpi=300, bbox_inches='tight')
    plt.savefig('benchmark/graphs/fig7_reliability_comparison.pdf', bbox_inches='tight')
    print("✓ Generated: fig7_reliability_comparison.png")
    plt.close()

def generate_summary_table(df):
    """Generate LaTeX summary table"""
    summary = df.groupby('Algorithm').agg({
        'Makespan': ['mean', 'std'],
        'Cost': ['mean', 'std'],
        'Energy': ['mean', 'std'],
        'Security': ['mean', 'std'],
        'Reliability': ['mean', 'std']
    }).round(4)
    
    latex_file = 'benchmark/graphs/summary_table.tex'
    with open(latex_file, 'w') as f:
        f.write("\\begin{table}[h]\n")
        f.write("\\centering\n")
        f.write("\\caption{Performance Metrics Comparison Across All Benchmarks}\n")
        f.write("\\begin{tabular}{|l|c|c|c|c|c|}\n")
        f.write("\\hline\n")
        f.write("\\textbf{Algorithm} & \\textbf{Makespan (s)} & \\textbf{Cost (\\$)} & \\textbf{Energy (kWh)} & \\textbf{Security} & \\textbf{Reliability (\\%)} \\\\\n")
        f.write("\\hline\n")
        
        for algo in ['SCEAH', 'RCSECH', 'FATS']:
            row = summary.loc[algo]
            f.write(f"{algo} & {row[('Makespan', 'mean')]:.2f}±{row[('Makespan', 'std')]:.2f} & ")
            f.write(f"{row[('Cost', 'mean')]:.4f}±{row[('Cost', 'std')]:.4f} & ")
            f.write(f"{row[('Energy', 'mean')]:.4f}±{row[('Energy', 'std')]:.4f} & ")
            f.write(f"{row[('Security', 'mean')]:.2f}±{row[('Security', 'std')]:.2f} & ")
            f.write(f"{row[('Reliability', 'mean')]:.2f}±{row[('Reliability', 'std')]:.2f} \\\\\n")
        
        f.write("\\hline\n")
        f.write("\\end{tabular}\n")
        f.write("\\end{table}\n")
    
    print(f"✓ Generated: summary_table.tex")

def main():
    print("=" * 80)
    print("PhD Thesis Graph Generator - Fog Computing Schedulers")
    print("=" * 80)
    
    # Load data
    df = load_data()
    if df is None:
        return
    
    print(f"\nLoaded {len(df)} benchmark results")
    print(f"Algorithms: {df['Algorithm'].unique()}")
    print(f"Benchmarks: {df['Benchmark'].unique()}")
    
    # Create output directory
    create_output_dir()
    
    # Generate all graphs
    print("\nGenerating graphs...")
    plot_scalability_makespan(df)
    plot_scalability_energy_cost(df)
    plot_cloud_mips_comparison(df)
    plot_security_level_impact(df)
    plot_tier_distribution(df)
    plot_security_energy_tradeoff(df)
    plot_reliability_comparison(df)
    generate_summary_table(df)
    
    print("\n" + "=" * 80)
    print("All graphs generated successfully!")
    print("Output location: benchmark/graphs/")
    print("Formats: PNG (300 DPI) + PDF (vector)")
    print("=" * 80)

if __name__ == '__main__':
    main()
