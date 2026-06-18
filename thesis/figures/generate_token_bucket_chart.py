import matplotlib.pyplot as plt
import numpy as np
import os

# Data for Top 5 Apps
apps = ['Termux (iPerf3)', 'YouTube', 'Chrome', 'Spotify', 'PUBG Mobile']
requested_mbps = [50.0, 15.0, 2.0, 1.0, 0.5]
allowed_mbps = [5.0, 5.0, 2.0, 1.0, 0.5]  # Assuming a 5Mbps cap for high bandwidth apps

x = np.arange(len(apps))
width = 0.35

fig, ax = plt.subplots(figsize=(10, 6))

# Dark theme for cyberpunk/tech aesthetic
plt.style.use('dark_background')
fig.patch.set_facecolor('#1e1e2e')
ax.set_facecolor('#1e1e2e')

rects1 = ax.bar(x - width/2, requested_mbps, width, label='Requested Bandwidth (Mbps)', color='#f38ba8')
rects2 = ax.bar(x + width/2, allowed_mbps, width, label='Allowed Bandwidth (Mbps)', color='#a6e3a1')

# Add some text for labels, title and custom x-axis tick labels, etc.
ax.set_ylabel('Bandwidth (Mbps)', color='#cdd6f4', fontsize=12)
ax.set_title('Token Bucket Enforcement: Requested vs. Allowed Bandwidth (Top 5 Apps)', color='#cdd6f4', fontsize=14, pad=20)
ax.set_xticks(x)
ax.set_xticklabels(apps, color='#cdd6f4', fontsize=11)
ax.tick_params(axis='y', colors='#cdd6f4')

# Add grid for readability
ax.yaxis.grid(True, linestyle='--', alpha=0.3, color='#cdd6f4')
ax.set_axisbelow(True)

ax.legend(facecolor='#313244', edgecolor='#45475a', labelcolor='#cdd6f4')

# Attach a text label above each bar, displaying its height.
def autolabel(rects):
    """Attach a text label above each bar in *rects*, displaying its height."""
    for rect in rects:
        height = rect.get_height()
        ax.annotate(f'{height}',
                    xy=(rect.get_x() + rect.get_width() / 2, height),
                    xytext=(0, 3),  # 3 points vertical offset
                    textcoords="offset points",
                    ha='center', va='bottom', color='#cdd6f4', fontweight='bold')

autolabel(rects1)
autolabel(rects2)

fig.tight_layout()

# Save the figure
output_dir = os.path.dirname(os.path.abspath(__file__))
output_path = os.path.join(output_dir, 'token_bucket_chart.png')
plt.savefig(output_path, dpi=300, bbox_inches='tight', transparent=True)
print(f"Chart generated successfully at: {output_path}")
