import matplotlib.pyplot as plt
import numpy as np

# --- Parameters ---
r = 2.0         # Rate (slope): 2 Mbps
b = 1.5         # Burst capacity (y-intercept): 1.5 Mb
R_min = 1.0     # WFQ guaranteed lower bound rate: 1 Mbps
time_max = 5.0  # Max time: 5 seconds

# Time array
t = np.linspace(0, time_max, 500)

# --- Curves ---
# 1. Upper Bound (Token Bucket Envelope): alpha(t) = b + r*t
upper_bound = b + r * t

# 2. Lower Bound (WFQ Guarantee): beta(t) = R_min * t
lower_bound = R_min * t

# 3. Simulated Actual Traffic: Cumulative data sent
# Starts with a burst, then fluctuates but stays within bounds.
t_actual = np.array([0, 0.2, 0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 4.5, 5.0])
# Cumulative traffic data points
actual_traffic = np.array([0, 1.2, 1.4, 2.3, 3.5, 4.0, 5.1, 6.0, 6.8, 7.5, 8.8, 9.5])

# Interpolate actual traffic for smooth plotting
from scipy.interpolate import interp1d
f_actual = interp1d(t_actual, actual_traffic, kind='previous')
y_actual = f_actual(t)

# --- Plotting ---
plt.figure(figsize=(10, 6))

# Plot Upper Bound
plt.plot(t, upper_bound, 'r--', linewidth=2, label=r'Token Bucket Upper Bound: $\alpha(t) = b + r \cdot t$')

# Plot Lower Bound
plt.plot(t, lower_bound, 'g--', linewidth=2, label=r'WFQ Lower Bound: $\beta(t) = R_{min} \cdot t$')

# Plot Actual Traffic
plt.plot(t, y_actual, 'b-', linewidth=2.5, label='Actual Cumulative Traffic $A(t)$')

# Fill the valid region
plt.fill_between(t, lower_bound, upper_bound, color='gray', alpha=0.1, label='Valid Operation Region')

# Highlight Burst Capacity (b)
plt.annotate(f'Burst $b$ ({b} Mb)', xy=(0, b), xytext=(-0.5, b+0.5),
             arrowprops=dict(facecolor='black', shrink=0.05, width=1.5, headwidth=6))

# Highlight Maximum Delay
# The horizontal distance between Actual Traffic and Lower Bound is the Delay.
# Let's draw an arrow at t=2.0 to show Delay Bound
delay_y = f_actual(2.0)
t_service = delay_y / R_min # Time when this data is guaranteed to be serviced
plt.annotate(r'$D_{max}$ (Delay Bound)', xy=(2.0, delay_y), xytext=(t_service, delay_y),
             arrowprops=dict(arrowstyle='<->', color='purple', lw=2))


plt.title('Network Calculus: Theoretical Bounds of QoS Scheduler', fontsize=14, fontweight='bold')
plt.xlabel('Time (seconds)', fontsize=12)
plt.ylabel('Cumulative Traffic (Megabits)', fontsize=12)
plt.xlim(0, time_max)
plt.ylim(0, upper_bound[-1] + 1)
plt.grid(True, linestyle=':', alpha=0.7)
plt.legend(loc='upper left', fontsize=11)

plt.tight_layout()
plt.savefig('theoretical_bounds_network_calculus.png', dpi=300)
print("Graph generated successfully: theoretical_bounds_network_calculus.png")
