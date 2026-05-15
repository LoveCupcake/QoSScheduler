#!/usr/bin/env python3
"""
iPerf3 Results Analysis Script
Parses JSON outputs and generates graphs for thesis
"""

import json
import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
from pathlib import Path

# Configure matplotlib for publication-quality figures
plt.rcParams['figure.dpi'] = 300
plt.rcParams['savefig.dpi'] = 300
plt.rcParams['font.size'] = 11
plt.rcParams['font.family'] = 'serif'

class iPerfAnalyzer:
    def __init__(self, results_dir='results'):
        self.results_dir = Path(results_dir)
        self.results_dir.mkdir(exist_ok=True)
        self.graphs_dir = Path('graphs')
        self.graphs_dir.mkdir(exist_ok=True)
    
    def parse_iperf3_json(self, filename):
        """Parse iPerf3 JSON output file"""
        filepath = self.results_dir / filename
        
        if not filepath.exists():
            print(f"Warning: {filepath} not found")
            return None
        
        with open(filepath, 'r') as f:
            data = json.load(f)
        
        # Extract summary
        summary = data['end']['sum_received']
        
        results = {
            'throughput_mbps': summary['bits_per_second'] / 1_000_000,
            'throughput_bps': summary['bits_per_second'],
            'bytes': summary['bytes'],
            'seconds': summary['seconds'],
            'retransmits': data['end'].get('sum_sent', {}).get('retransmits', 0)
        }
        
        # For UDP tests, extract latency/jitter
        if 'jitter_ms' in summary:
            results['jitter_ms'] = summary.get('jitter_ms', 0)
            results['lost_packets'] = summary.get('lost_packets', 0)
            results['packets'] = summary.get('packets', 0)
            results['lost_percent'] = summary.get('lost_percent', 0)
        
        # Extract interval data for time series
        intervals = []
        for interval in data['intervals']:
            intervals.append({
                'time': interval['sum']['end'],
                'throughput_mbps': interval['sum']['bits_per_second'] / 1_000_000,
                'bytes': interval['sum']['bytes']
            })
        
        results['intervals'] = pd.DataFrame(intervals)
        
        return results
    
    def calculate_improvements(self, baseline, qos):
        """Calculate improvement metrics"""
        if baseline is None or qos is None:
            return None
        
        improvements = {
            'throughput_ratio': qos['throughput_mbps'] / baseline['throughput_mbps'],
            'throughput_improvement_percent': 
                ((qos['throughput_mbps'] - baseline['throughput_mbps']) / baseline['throughput_mbps']) * 100
        }
        
        # For UDP tests
        if 'jitter_ms' in baseline and 'jitter_ms' in qos:
            improvements['latency_reduction_percent'] = \
                ((baseline.get('jitter_ms', 0) - qos.get('jitter_ms', 0)) / baseline.get('jitter_ms', 1)) * 100
            improvements['jitter_reduction_percent'] = \
                ((baseline.get('jitter_ms', 0) - qos.get('jitter_ms', 0)) / baseline.get('jitter_ms', 1)) * 100
        
        return improvements
    
    def generate_throughput_comparison(self, baseline_appA, baseline_appB, qos_high, qos_low):
        """Generate Graph 1: Throughput Comparison Bar Chart"""
        categories = ['Baseline\nApp A', 'Baseline\nApp B', 
                      'QoS\nHIGH', 'QoS\nLOW']
        
        throughput = [
            baseline_appA['throughput_mbps'] if baseline_appA else 0,
            baseline_appB['throughput_mbps'] if baseline_appB else 0,
            qos_high['throughput_mbps'] if qos_high else 0,
            qos_low['throughput_mbps'] if qos_low else 0
        ]
        
        plt.figure(figsize=(10, 6))
        colors = ['#757575', '#757575', '#4CAF50', '#F44336']
        bars = plt.bar(categories, throughput, color=colors, edgecolor='black', linewidth=1.5)
        
        plt.ylabel('Throughput (Mbps)', fontsize=13, fontweight='bold')
        plt.title('Throughput Comparison: Baseline vs QoS', 
                  fontsize=15, fontweight='bold', pad=15)
        plt.ylim(0, max(throughput) * 1.2)
        plt.grid(axis='y', alpha=0.3, linestyle='--')
        
        # Add value labels on bars
        for bar in bars:
            height = bar.get_height()
            plt.text(bar.get_x() + bar.get_width()/2., height,
                     f'{height:.2f}\nMbps',
                     ha='center', va='bottom', fontsize=10, fontweight='bold')
        
        # Add improvement annotation
        if qos_high and baseline_appA:
            improvement = qos_high['throughput_mbps'] / baseline_appA['throughput_mbps']
            plt.text(0.5, 0.95, f'Improvement: {improvement:.2f}×',
                     transform=plt.gca().transAxes,
                     fontsize=12, fontweight='bold',
                     bbox=dict(boxstyle='round', facecolor='yellow', alpha=0.7),
                     ha='center')
        
        plt.tight_layout()
        plt.savefig(self.graphs_dir / 'graph1_throughput_comparison.png', dpi=300, bbox_inches='tight')
        print("✓ Generated: graph1_throughput_comparison.png")
        plt.close()
    
    def generate_timeseries_throughput(self, qos_high, qos_low):
        """Generate Graph 2: Time Series Throughput"""
        if qos_high is None or qos_low is None:
            print("Warning: Missing data for timeseries graph")
            return
        
        plt.figure(figsize=(12, 6))
        
        high_df = qos_high['intervals']
        low_df = qos_low['intervals']
        
        plt.plot(high_df['time'], high_df['throughput_mbps'], 
                 label='HIGH Priority', color='#4CAF50', linewidth=2.5, marker='o', markersize=4)
        plt.plot(low_df['time'], low_df['throughput_mbps'], 
                 label='LOW Priority', color='#F44336', linewidth=2.5, marker='s', markersize=4)
        
        plt.xlabel('Time (seconds)', fontsize=13, fontweight='bold')
        plt.ylabel('Throughput (Mbps)', fontsize=13, fontweight='bold')
        plt.title('Real-Time Throughput: HIGH vs LOW Priority', 
                  fontsize=15, fontweight='bold', pad=15)
        plt.legend(fontsize=12, loc='best', framealpha=0.9)
        plt.grid(alpha=0.3, linestyle='--')
        
        # Add average lines
        high_avg = high_df['throughput_mbps'].mean()
        low_avg = low_df['throughput_mbps'].mean()
        plt.axhline(y=high_avg, color='#4CAF50', linestyle=':', linewidth=2, alpha=0.7,
                    label=f'HIGH Avg: {high_avg:.2f} Mbps')
        plt.axhline(y=low_avg, color='#F44336', linestyle=':', linewidth=2, alpha=0.7,
                    label=f'LOW Avg: {low_avg:.2f} Mbps')
        
        plt.legend(fontsize=11, loc='best', framealpha=0.9)
        plt.tight_layout()
        plt.savefig(self.graphs_dir / 'graph2_throughput_timeseries.png', dpi=300, bbox_inches='tight')
        print("✓ Generated: graph2_throughput_timeseries.png")
        plt.close()
    
    def generate_latency_jitter_comparison(self, baseline_udp, qos_high_udp):
        """Generate Graph 3: Latency and Jitter Comparison"""
        if baseline_udp is None or qos_high_udp is None:
            print("Warning: Missing UDP data for latency/jitter graph")
            return
        
        metrics = ['Jitter (ms)', 'Packet Loss (%)']
        baseline = [
            baseline_udp.get('jitter_ms', 0),
            baseline_udp.get('lost_percent', 0)
        ]
        qos_high = [
            qos_high_udp.get('jitter_ms', 0),
            qos_high_udp.get('lost_percent', 0)
        ]
        
        x = np.arange(len(metrics))
        width = 0.35
        
        fig, ax = plt.subplots(figsize=(10, 6))
        bars1 = ax.bar(x - width/2, baseline, width, label='Baseline', 
                       color='#757575', edgecolor='black', linewidth=1.5)
        bars2 = ax.bar(x + width/2, qos_high, width, label='QoS HIGH', 
                       color='#4CAF50', edgecolor='black', linewidth=1.5)
        
        ax.set_ylabel('Value', fontsize=13, fontweight='bold')
        ax.set_title('Jitter and Packet Loss: Baseline vs QoS', 
                     fontsize=15, fontweight='bold', pad=15)
        ax.set_xticks(x)
        ax.set_xticklabels(metrics, fontsize=12, fontweight='bold')
        ax.legend(fontsize=12, framealpha=0.9)
        ax.grid(axis='y', alpha=0.3, linestyle='--')
        
        # Add value labels
        for bars in [bars1, bars2]:
            for bar in bars:
                height = bar.get_height()
                ax.text(bar.get_x() + bar.get_width()/2., height,
                        f'{height:.2f}',
                        ha='center', va='bottom', fontsize=10, fontweight='bold')
        
        # Add improvement percentages
        jitter_reduction = ((baseline[0] - qos_high[0]) / baseline[0]) * 100
        loss_reduction = ((baseline[1] - qos_high[1]) / baseline[1]) * 100
        
        ax.text(0.5, 0.95, 
                f'Jitter Reduction: {jitter_reduction:.1f}% | Loss Reduction: {loss_reduction:.1f}%',
                transform=ax.transAxes,
                fontsize=11, fontweight='bold',
                bbox=dict(boxstyle='round', facecolor='yellow', alpha=0.7),
                ha='center')
        
        plt.tight_layout()
        plt.savefig(self.graphs_dir / 'graph3_latency_jitter.png', dpi=300, bbox_inches='tight')
        print("✓ Generated: graph3_latency_jitter.png")
        plt.close()
    
    def generate_wfq_allocation(self, high, medium, low):
        """Generate Graph 4: WFQ Bandwidth Allocation Pie Chart"""
        if high is None or medium is None or low is None:
            print("Warning: Missing data for WFQ allocation graph")
            return
        
        labels = [
            f'HIGH\n({high["throughput_mbps"]:.1f} Mbps)',
            f'MEDIUM\n({medium["throughput_mbps"]:.1f} Mbps)',
            f'LOW\n({low["throughput_mbps"]:.1f} Mbps)'
        ]
        sizes = [
            high['throughput_mbps'],
            medium['throughput_mbps'],
            low['throughput_mbps']
        ]
        colors = ['#4CAF50', '#FFC107', '#F44336']
        explode = (0.1, 0, 0)  # Explode HIGH priority
        
        plt.figure(figsize=(9, 9))
        wedges, texts, autotexts = plt.pie(sizes, explode=explode, labels=labels, colors=colors,
                autopct='%1.1f%%', shadow=True, startangle=90,
                textprops={'fontsize': 12, 'fontweight': 'bold'})
        
        # Make percentage text bold and larger
        for autotext in autotexts:
            autotext.set_color('white')
            autotext.set_fontsize(13)
            autotext.set_fontweight('bold')
        
        total = sum(sizes)
        plt.title(f'WFQ Bandwidth Allocation\n(3 Local Apps, {total:.1f} Mbps Total)', 
                  fontsize=15, fontweight='bold', pad=20)
        plt.axis('equal')
        plt.tight_layout()
        plt.savefig(self.graphs_dir / 'graph4_wfq_allocation.png', dpi=300, bbox_inches='tight')
        print("✓ Generated: graph4_wfq_allocation.png")
        plt.close()
    
    def generate_packet_loss_comparison(self, high_udp, medium_udp, low_udp):
        """Generate Graph 5: Packet Loss Comparison"""
        if high_udp is None:
            print("Warning: Missing UDP data for packet loss graph")
            return
        
        categories = []
        packet_loss = []
        colors = []
        
        if high_udp:
            categories.append('HIGH Priority')
            packet_loss.append(high_udp.get('lost_percent', 0))
            colors.append('#4CAF50')
        
        if medium_udp:
            categories.append('MEDIUM Priority')
            packet_loss.append(medium_udp.get('lost_percent', 0))
            colors.append('#FFC107')
        
        if low_udp:
            categories.append('LOW Priority')
            packet_loss.append(low_udp.get('lost_percent', 0))
            colors.append('#F44336')
        
        plt.figure(figsize=(10, 6))
        bars = plt.bar(categories, packet_loss, color=colors, edgecolor='black', linewidth=1.5)
        plt.ylabel('Packet Loss (%)', fontsize=13, fontweight='bold')
        plt.title('Packet Loss by Priority Class', fontsize=15, fontweight='bold', pad=15)
        plt.ylim(0, max(packet_loss) * 1.2 if packet_loss else 100)
        plt.grid(axis='y', alpha=0.3, linestyle='--')
        
        # Add value labels
        for bar in bars:
            height = bar.get_height()
            plt.text(bar.get_x() + bar.get_width()/2., height,
                     f'{height:.2f}%',
                     ha='center', va='bottom', fontsize=11, fontweight='bold')
        
        # Add acceptable threshold line
        plt.axhline(y=1, color='blue', linestyle='--', linewidth=2.5, 
                    label='Acceptable threshold (1%)', alpha=0.7)
        plt.legend(fontsize=11, framealpha=0.9)
        
        plt.tight_layout()
        plt.savefig(self.graphs_dir / 'graph5_packet_loss.png', dpi=300, bbox_inches='tight')
        print("✓ Generated: graph5_packet_loss.png")
        plt.close()
    
    def generate_summary_report(self, results):
        """Generate text summary report"""
        report_path = self.graphs_dir / 'summary_report.txt'
        
        with open(report_path, 'w') as f:
            f.write("=" * 70 + "\n")
            f.write("QoS SCHEDULER - EXPERIMENTAL RESULTS SUMMARY\n")
            f.write("=" * 70 + "\n\n")
            
            # Baseline results
            f.write("BASELINE (No QoS):\n")
            f.write("-" * 70 + "\n")
            if results.get('baseline_appA'):
                f.write(f"App A Throughput: {results['baseline_appA']['throughput_mbps']:.2f} Mbps\n")
            if results.get('baseline_appB'):
                f.write(f"App B Throughput: {results['baseline_appB']['throughput_mbps']:.2f} Mbps\n")
            f.write("\n")
            
            # QoS results
            f.write("WITH QoS:\n")
            f.write("-" * 70 + "\n")
            if results.get('qos_high'):
                f.write(f"HIGH Priority Throughput: {results['qos_high']['throughput_mbps']:.2f} Mbps\n")
            if results.get('qos_low'):
                f.write(f"LOW Priority Throughput: {results['qos_low']['throughput_mbps']:.2f} Mbps\n")
            f.write("\n")
            
            # Improvements
            f.write("IMPROVEMENTS:\n")
            f.write("-" * 70 + "\n")
            if results.get('improvements'):
                imp = results['improvements']
                f.write(f"Throughput Improvement: {imp.get('throughput_ratio', 0):.2f}×\n")
                f.write(f"Throughput Gain: {imp.get('throughput_improvement_percent', 0):.1f}%\n")
                if 'jitter_reduction_percent' in imp:
                    f.write(f"Jitter Reduction: {imp['jitter_reduction_percent']:.1f}%\n")
            f.write("\n")
            
            # UDP results
            if results.get('qos_high_udp'):
                f.write("UDP PERFORMANCE (HIGH Priority):\n")
                f.write("-" * 70 + "\n")
                udp = results['qos_high_udp']
                f.write(f"Jitter: {udp.get('jitter_ms', 0):.2f} ms\n")
                f.write(f"Packet Loss: {udp.get('lost_percent', 0):.2f}%\n")
                f.write(f"Packets Sent: {udp.get('packets', 0)}\n")
                f.write(f"Packets Lost: {udp.get('lost_packets', 0)}\n")
                f.write("\n")
            
            f.write("=" * 70 + "\n")
            f.write("Graphs generated in: graphs/\n")
            f.write("=" * 70 + "\n")
        
        print(f"✓ Generated: summary_report.txt")
        
        # Also print to console
        with open(report_path, 'r') as f:
            print("\n" + f.read())
    
    def run_full_analysis(self):
        """Run complete analysis pipeline"""
        print("\n" + "=" * 70)
        print("QoS SCHEDULER - RESULTS ANALYSIS")
        print("=" * 70 + "\n")
        
        print("Parsing iPerf3 JSON files...")
        
        # Parse all results
        results = {}
        results['baseline_appA'] = self.parse_iperf3_json('baseline_appA.json')
        results['baseline_appB'] = self.parse_iperf3_json('baseline_appB.json')
        results['qos_high'] = self.parse_iperf3_json('qos_appA_high.json')
        results['qos_low'] = self.parse_iperf3_json('qos_appB_low.json')
        results['qos_high_udp'] = self.parse_iperf3_json('qos_high_udp.json')
        results['qos_low_udp'] = self.parse_iperf3_json('qos_low_udp.json')
        results['qos_3dev_high'] = self.parse_iperf3_json('qos_wfq_high.json')
        results['qos_3dev_medium'] = self.parse_iperf3_json('qos_wfq_medium.json')
        results['qos_3dev_low'] = self.parse_iperf3_json('qos_wfq_low.json')
        
        # Calculate improvements
        results['improvements'] = self.calculate_improvements(
            results['baseline_appA'], 
            results['qos_high']
        )
        
        print("\nGenerating graphs...")
        
        # Generate all graphs
        self.generate_throughput_comparison(
            results['baseline_appA'],
            results['baseline_appB'],
            results['qos_high'],
            results['qos_low']
        )
        
        self.generate_timeseries_throughput(
            results['qos_high'],
            results['qos_low']
        )
        
        self.generate_latency_jitter_comparison(
            results['baseline_appB'],  # Use as baseline UDP
            results['qos_high_udp']
        )
        
        self.generate_wfq_allocation(
            results['qos_3dev_high'],
            results['qos_3dev_medium'],
            results['qos_3dev_low']
        )
        
        self.generate_packet_loss_comparison(
            results['qos_high_udp'],
            results['qos_3dev_medium'],
            results['qos_low_udp']
        )
        
        # Generate summary report
        self.generate_summary_report(results)
        
        print("\n" + "=" * 70)
        print("Analysis complete! Check the 'graphs/' directory for outputs.")
        print("=" * 70 + "\n")


if __name__ == '__main__':
    analyzer = iPerfAnalyzer()
    analyzer.run_full_analysis()
