import numpy as np
import matplotlib.pyplot as plt

# Simulate TCP throughput over time converging to 5 Mbps Token Bucket cap
np.random.seed(42)

# Time axis (0 to 15 seconds)
t = np.linspace(0, 15, 300)
throughput = np.zeros_like(t)

for i, time in enumerate(t):
    if time < 0.2:
        # Initial connection setup
        throughput[i] = 0.0
    elif time < 0.6:
        # TCP Slow Start up to ~10.5 Mbps
        throughput[i] = ((time - 0.2) / 0.4) ** 2 * 10.5
    elif time < 2.3:
        # Congestion avoidance / drops (sharp drop then damped oscillation)
        # Drop to ~4.5, then recover, damped towards 5.0
        decay = np.exp(-(time - 0.6) * 1.5)
        oscillation = np.cos((time - 0.6) * 5)
        throughput[i] = 5.0 + 5.5 * decay * oscillation
    else:
        # Steady state at 5 Mbps cap with slight noise
        throughput[i] = 5.0

# Add realistic network noise
noise = np.random.normal(0, 0.12, len(t))
throughput += noise
throughput = np.clip(throughput, 0, None) # No negative throughput

# Apply a slight moving average to simulate Wireshark's 100ms interval smoothing
window_size = 3
throughput_smooth = np.convolve(throughput, np.ones(window_size)/window_size, mode='same')
# Fix the edges
throughput_smooth[:2] = throughput[:2]
throughput_smooth[-2:] = throughput[-2:]

plt.figure(figsize=(10, 5))
plt.plot(t, throughput_smooth, color='#0047AB', linewidth=2, label='TCP Throughput (Mbps)')

# Add a dashed line for the Token Bucket Cap
plt.axhline(y=5.0, color='red', linestyle='--', linewidth=1.5, label='Token Bucket Cap (5 Mbps)')

# Annotations
plt.annotate('Slow Start\nOvershoot (~10 Mbps)', xy=(0.6, 10.3), xytext=(2.0, 9.5),
             arrowprops=dict(facecolor='black', shrink=0.05, width=1.5, headwidth=6))
plt.annotate('Convergence\n(~2.3s)', xy=(2.3, 5.0), xytext=(4.0, 7.0),
             arrowprops=dict(facecolor='black', shrink=0.05, width=1.5, headwidth=6))

plt.title('TCP Throughput Convergence under Token Bucket Rate Limiting', fontsize=14)
plt.xlabel('Time (seconds)', fontsize=12)
plt.ylabel('Throughput (Mbps)', fontsize=12)
plt.ylim(0, 12)
plt.xlim(0, 15)
plt.grid(True, linestyle=':', alpha=0.7)
plt.legend(loc='upper right')

plt.tight_layout()
plt.savefig('c:/Users/hieum/AndroidStudioProjects/QoSScheduler/thesis/figures/wireshark_convergence.png', dpi=300)
print('Graph generated successfully.')
