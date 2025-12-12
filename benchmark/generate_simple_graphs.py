"""
Simple PhD Thesis Graph Generator
Generates comparison graphs from benchmark results
"""

import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
import numpy as np
import os

# Publication quality settings
sns.set_style("whitegrid")
plt.rcParams['figure.figsize'] = (12, 8)
plt.rcParams['font.size'] = 12
plt.rcParams['font.family'] = 'DejaVu Sans'

# Color palette
COLORS = {
    'SCEAH': '#1f77b4',   # Blue
    'RCSECH': '#2ca02c',  # Green
    'FATS': '#d62728'     # Red
}

def load_data():
    """Load results CSV"""
    file = 'benchmark/results/thesis_results.csv'
    if not os.path.exists(file):
        print(f"ERROR: {file} not found!")
        print("Run: java -cp \"lib/*;bin\" org.workflowsim.benchmark.SimpleBenchmarkRunner")
        return None
    return pd.read_csv(file)

def create_dirs():
    """Create output directory"""
    os.makedirs('benchmark/graphs', exist_ok=True)

def plot_performance_comparison(df):
    """Main comparison chart - all metrics"""
    fig, axes = plt.subplots(2, 3, figsize=(16, 10))
    axes = axes.flatten()
    
    metrics = [
        ('Makespan', 'Makespan (seconds)', 0),
        ('Cost', 'Total Cost ($)', 1),
        ('Energy', 'Energy (kWh)', 2),
        ('Security', 'Security Score', 3),
        ('Reliability', 'Reliability (%)', 4)
    ]
    
    for metric, ylabel, idx in metrics:
        ax = axes[idx]
        
        # Calculate mean and std for each algorithm
        summary = df.groupby('Algorithm')[metric].agg(['mean', 'std']).reset_index()
        
        x = np.arange(len(summary))
        width = 0.6
        
        bars = ax.bar(x, summary['mean'], width, 
                     color=[COLORS[algo] for algo in summary['Algorithm']],
                     alpha=0.8, edgecolor='black', linewidth=1.5)
        
        # Add error bars
        ax.errorbar(x, summary['mean'], yerr=summary['std'],
                   fmt='none', ecolor='black', capsize=5, linewidth=2)
        
        # Labels
        ax.set_ylabel(ylabel, fontweight='bold', fontsize=13)
        ax.set_title(ylabel, fontweight='bold', fontsize=14, pad=10)
        ax.set_xticks(x)
        ax.set_xticklabels(summary['Algorithm'], fontsize=12, fontweight='bold')
        ax.grid(True, alpha=0.3, axis='y')
        
        # Add value labels on bars
        for i, (mean, std) in enumerate(zip(summary['mean'], summary['std'])):
            ax.text(i, mean + std + max(summary['mean'])*0.02, 
                   f'{mean:.2f}', ha='center', va='bottom',
                   fontsize=10, fontweight='bold')
    
    # Remove extra subplot
    fig.delaxes(axes[5])
    
    plt.tight_layout()
    plt.savefig('benchmark/graphs/comparison_all_metrics.png', dpi=300, bbox_inches='tight')
    plt.savefig('benchmark/graphs/comparison_all_metrics.pdf', bbox_inches='tight')
    print("✓ Generated: comparison_all_metrics.png")
    plt.close()

def plot_radar_chart(df):
    """Radar chart for multi-dimensional comparison"""
    from math import pi
    
    # Normalize metrics to 0-1 scale
    summary = df.groupby('Algorithm').mean()
    
    # Lower is better for these, so invert
    for col in ['Makespan', 'Cost', 'Energy']:
        max_val = summary[col].max()
        summary[col] = 1 - (summary[col] / max_val)
    
    # Higher is better, normalize
    for col in ['Security', 'Reliability']:
        max_val = summary[col].max()
        summary[col] = summary[col] / max_val
    
    categories = ['Makespan\n(Lower Better)', 'Cost\n(Lower Better)', 
                  'Energy\n(Lower Better)', 'Security\n(Higher Better)', 
                  'Reliability\n(Higher Better)']
    
    fig, ax = plt.subplots(figsize=(10, 10), subplot_kw=dict(projection='polar'))
    
    angles = [n / len(categories) * 2 * pi for n in range(len(categories))]
    angles += angles[:1]
    
    for algorithm in ['SCEAH', 'RCSECH', 'FATS']:
        values = summary.loc[algorithm, ['Makespan', 'Cost', 'Energy', 'Security', 'Reliability']].values.tolist()
        values += values[:1]
        
        ax.plot(angles, values, 'o-', linewidth=3, label=algorithm, 
               color=COLORS[algorithm], markersize=10)
        ax.fill(angles, values, alpha=0.15, color=COLORS[algorithm])
    
    ax.set_xticks(angles[:-1])
    ax.set_xticklabels(categories, fontsize=11, fontweight='bold')
    ax.set_ylim(0, 1)
    ax.set_yticks([0.2, 0.4, 0.6, 0.8, 1.0])
    ax.set_yticklabels(['20%', '40%', '60%', '80%', '100%'])
    ax.grid(True)
    ax.legend(loc='upper right', bbox_to_anchor=(1.3, 1.1), fontsize=13, frameon=True, shadow=True)
    ax.set_title('Multi-Dimensional Performance Comparison', 
                y=1.08, fontweight='bold', fontsize=16)
    
    plt.tight_layout()
    plt.savefig('benchmark/graphs/radar_chart.png', dpi=300, bbox_inches='tight')
    plt.savefig('benchmark/graphs/radar_chart.pdf', bbox_inches='tight')
    print("✓ Generated: radar_chart.png")
    plt.close()

def plot_tradeoff_scatter(df):
    """Security vs Energy tradeoff"""
    fig, ax = plt.subplots(figsize=(10, 7))
    
    for algorithm in ['SCEAH', 'RCSECH', 'FATS']:
        data = df[df['Algorithm'] == algorithm]
        ax.scatter(data['Security'], data['Energy'],
                  s=250, alpha=0.7, label=algorithm, color=COLORS[algorithm],
                  edgecolors='black', linewidth=2)
        
        # Add mean marker
        mean_sec = data['Security'].mean()
        mean_eng = data['Energy'].mean()
        ax.scatter(mean_sec, mean_eng, s=500, marker='*',
                  color=COLORS[algorithm], edgecolors='black', linewidth=2)
    
    ax.set_xlabel('Security Score (Higher is Better)', fontweight='bold', fontsize=14)
    ax.set_ylabel('Energy Consumption in kWh (Lower is Better)', fontweight='bold', fontsize=14)
    ax.set_title('Security-Energy Trade-off Analysis', fontweight='bold', fontsize=16, pad=20)
    ax.legend(loc='best', fontsize=13, frameon=True, shadow=True)
    ax.grid(True, alpha=0.3)
    
    plt.tight_layout()
    plt.savefig('benchmark/graphs/security_energy_tradeoff.png', dpi=300, bbox_inches='tight')
    plt.savefig('benchmark/graphs/security_energy_tradeoff.pdf', bbox_inches='tight')
    print("✓ Generated: security_energy_tradeoff.png")
    plt.close()

def plot_cost_reliability(df):
    """Cost vs Reliability scatter"""
    fig, ax = plt.subplots(figsize=(10, 7))
    
    for algorithm in ['SCEAH', 'RCSECH', 'FATS']:
        data = df[df['Algorithm'] == algorithm]
        ax.scatter(data['Cost'], data['Reliability'],
                  s=250, alpha=0.7, label=algorithm, color=COLORS[algorithm],
                  edgecolors='black', linewidth=2)
        
        mean_cost = data['Cost'].mean()
        mean_rel = data['Reliability'].mean()
        ax.scatter(mean_cost, mean_rel, s=500, marker='*',
                  color=COLORS[algorithm], edgecolors='black', linewidth=2)
    
    ax.set_xlabel('Total Cost in $ (Lower is Better)', fontweight='bold', fontsize=14)
    ax.set_ylabel('Reliability % (Higher is Better)', fontweight='bold', fontsize=14)
    ax.set_title('Cost-Reliability Trade-off Analysis', fontweight='bold', fontsize=16, pad=20)
    ax.legend(loc='best', fontsize=13, frameon=True, shadow=True)
    ax.grid(True, alpha=0.3)
    ax.axhline(y=95, color='r', linestyle='--', linewidth=2, alpha=0.5, label='95% Threshold')
    
    plt.tight_layout()
    plt.savefig('benchmark/graphs/cost_reliability_tradeoff.png', dpi=300, bbox_inches='tight')
    plt.savefig('benchmark/graphs/cost_reliability_tradeoff.pdf', bbox_inches='tight')
    print("✓ Generated: cost_reliability_tradeoff.png")
    plt.close()

def generate_latex_table(df):
    """Generate LaTeX summary table"""
    summary = df.groupby('Algorithm').agg({
        'Makespan': ['mean', 'std'],
        'Cost': ['mean', 'std'],
        'Energy': ['mean', 'std'],
        'Security': ['mean', 'std'],
        'Reliability': ['mean', 'std']
    })
    
    with open('benchmark/graphs/summary_table.tex', 'w') as f:
        f.write("\\begin{table}[htbp]\n")
        f.write("\\centering\n")
        f.write("\\caption{Performance Metrics Comparison: SCEAH, RCSECH, and FATS Schedulers}\n")
        f.write("\\label{tab:scheduler_comparison}\n")
        f.write("\\begin{tabular}{|l|c|c|c|c|c|}\n")
        f.write("\\hline\n")
        f.write("\\textbf{Algorithm} & \\textbf{Makespan (s)} & \\textbf{Cost (\\$)} & \\textbf{Energy (kWh)} & \\textbf{Security} & \\textbf{Reliability (\\%)} \\\\\n")
        f.write("\\hline\n")
        
        for algo in ['SCEAH', 'RCSECH', 'FATS']:
            row = summary.loc[algo]
            f.write(f"\\textbf{{{algo}}} & ")
            f.write(f"{row[('Makespan', 'mean')]:.2f} $\\pm$ {row[('Makespan', 'std')]:.2f} & ")
            f.write(f"{row[('Cost', 'mean')]:.4f} $\\pm$ {row[('Cost', 'std')]:.4f} & ")
            f.write(f"{row[('Energy', 'mean')]:.4f} $\\pm$ {row[('Energy', 'std')]:.4f} & ")
            f.write(f"{row[('Security', 'mean')]:.2f} $\\pm$ {row[('Security', 'std')]:.2f} & ")
            f.write(f"{row[('Reliability', 'mean')]:.2f} $\\pm$ {row[('Reliability', 'std')]:.2f} \\\\\n")
        
        f.write("\\hline\n")
        f.write("\\end{tabular}\n")
        f.write("\\end{table}\n")
    
    print("✓ Generated: summary_table.tex")

def generate_text_summary(df):
    """Generate text summary"""
    summary = df.groupby('Algorithm').mean()
    
    with open('benchmark/graphs/summary.txt', 'w') as f:
        f.write("="*80 + "\n")
        f.write("BENCHMARK RESULTS SUMMARY\n")
        f.write("="*80 + "\n\n")
        
        for algo in ['SCEAH', 'RCSECH', 'FATS']:
            f.write(f"\n{algo}:\n")
            f.write(f"  Makespan:    {summary.loc[algo, 'Makespan']:.2f} seconds\n")
            f.write(f"  Cost:        ${summary.loc[algo, 'Cost']:.4f}\n")
            f.write(f"  Energy:      {summary.loc[algo, 'Energy']:.4f} kWh\n")
            f.write(f"  Security:    {summary.loc[algo, 'Security']:.2f}\n")
            f.write(f"  Reliability: {summary.loc[algo, 'Reliability']:.2f}%\n")
        
        f.write("\n" + "="*80 + "\n")
        f.write("BEST PERFORMERS:\n")
        f.write("="*80 + "\n")
        f.write(f"  Fastest (Makespan):     {summary['Makespan'].idxmin()} ({summary['Makespan'].min():.2f}s)\n")
        f.write(f"  Cheapest (Cost):        {summary['Cost'].idxmin()} (${summary['Cost'].min():.4f})\n")
        f.write(f"  Most Efficient (Energy): {summary['Energy'].idxmin()} ({summary['Energy'].min():.4f} kWh)\n")
        f.write(f"  Most Secure (Security):  {summary['Security'].idxmax()} ({summary['Security'].max():.2f})\n")
        f.write(f"  Most Reliable:          {summary['Reliability'].idxmax()} ({summary['Reliability'].max():.2f}%)\n")
    
    print("✓ Generated: summary.txt")

def main():
    print("="*80)
    print("PhD THESIS GRAPH GENERATOR")
    print("="*80)
    
    df = load_data()
    if df is None:
        return
    
    print(f"\nLoaded {len(df)} results")
    print(f"Algorithms: {df['Algorithm'].unique()}")
    print(f"Runs per algorithm: {df.groupby('Algorithm').size().values[0]}")
    
    create_dirs()
    
    print("\nGenerating graphs...")
    plot_performance_comparison(df)
    plot_radar_chart(df)
    plot_tradeoff_scatter(df)
    plot_cost_reliability(df)
    generate_latex_table(df)
    generate_text_summary(df)
    
    print("\n" + "="*80)
    print("COMPLETE! Output: benchmark/graphs/")
    print("="*80)

if __name__ == '__main__':
    main()
