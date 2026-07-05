# dqn_training.py
# =====================================================
# آموزش DQN روی transitions تولیدشده توسط BFO-HH
# =====================================================

import numpy as np
import pandas as pd
import tensorflow as tf
from tensorflow import keras
import sys
import matplotlib.pyplot as plt
import os

# ========== 1. بارگذاری داده ==========
csv_file = sys.argv[1] if len(sys.argv) > 1 else 'transitions.csv'
print(f"📂 Loading {csv_file}...")
df = pd.read_csv(csv_file)

# شناسایی ابعاد state
state_cols = [c for c in df.columns if c.startswith('s') and not c.startswith('ns')]
state_dim = len(state_cols)
action_size = 4

print(f"✅ State dimension: {state_dim}")
print(f"✅ Number of transitions: {len(df)}")

# استخراج داده‌ها
states = df[[f's{i}' for i in range(state_dim)]].values.astype(np.float32)
next_states = df[[f'ns{i}' for i in range(state_dim)]].values.astype(np.float32)
actions = df['action'].values.astype(int)
rewards = df['reward'].values.astype(np.float32)
dones = df['done'].values.astype(int)

# ========== 2. ساخت مدل ==========
def build_model(state_dim, action_size):
    model = keras.Sequential([
        keras.layers.Dense(256, activation='relu', input_dim=state_dim),
        keras.layers.Dense(128, activation='relu'),
        keras.layers.Dense(64, activation='relu'),
        keras.layers.Dense(action_size, activation='linear')
    ])
    model.compile(optimizer=keras.optimizers.Adam(learning_rate=0.0005), loss='mse')
    return model

model = build_model(state_dim, action_size)
target_model = build_model(state_dim, action_size)
target_model.set_weights(model.get_weights())

print("✅ DQN model created")

# ========== 3. هایپرپارامترها (مطابق مقاله) ==========
batch_size = 64
gamma = 0.95
epsilon = 1.0
epsilon_min = 0.01
epsilon_decay = 0.995
target_update_freq = 100   # مطابق جدول ۷ مقاله
epochs = 500               # مطابق جدول ۷ مقاله

# ========== 4. حلقه آموزش ==========
print("\n🔄 Starting training...")
losses = []

for epoch in range(epochs):
    # نمونه‌گیری تصادفی از replay memory
    indices = np.random.choice(len(states), batch_size, replace=True)
    s_batch = states[indices]
    a_batch = actions[indices]
    r_batch = rewards[indices]
    ns_batch = next_states[indices]
    d_batch = dones[indices]
    
    # پیش‌بینی Q-values فعلی
    targets = model.predict(s_batch, verbose=0)
    q_next = target_model.predict(ns_batch, verbose=0)
    
    # محاسبه target Q-values با فرمول Bellman
    for i in range(batch_size):
        if d_batch[i]:
            targets[i][a_batch[i]] = r_batch[i]
        else:
            targets[i][a_batch[i]] = r_batch[i] + gamma * np.max(q_next[i])
    
    # آموزش مدل
    loss = model.fit(s_batch, targets, epochs=1, verbose=0)
    losses.append(loss.history['loss'][0])
    
    # به‌روزرسانی target network هر 100 اپیزود
    if (epoch + 1) % target_update_freq == 0:
        target_model.set_weights(model.get_weights())
        print(f"   Target network updated at epoch {epoch+1}")
    
    # کاهش epsilon
    epsilon = max(epsilon_min, epsilon * epsilon_decay)
    
    # گزارش هر 50 اپیزود
    if (epoch + 1) % 50 == 0:
        print(f"Epoch {epoch+1}/{epochs} - Loss: {losses[-1]:.6f} - ε: {epsilon:.4f}")

# ========== 5. ذخیره مدل ==========
model.save('dqn_model.h5')
print("\n✅ Model saved as dqn_model.h5")

# ========== 6. رسم نمودار loss ==========
plt.figure(figsize=(10, 5))
plt.plot(losses, linewidth=0.8)
plt.title('DQN Training Loss')
plt.xlabel('Epoch')
plt.ylabel('Loss (MSE)')
plt.grid(True, alpha=0.3)
plt.savefig('training_loss.png', dpi=150)
plt.close()
print("📊 Training loss plot saved as training_loss.png")

# ========== 7. گزارش توزیع actions در داده ==========
print("\n📊 Action distribution in training data:")
unique, counts = np.unique(actions, return_counts=True)
action_names = ['SELECT', 'MOVE', 'SWAP', 'DROP']
for u, c in zip(unique, counts):
    print(f"   {action_names[u]}: {c} ({c/len(actions)*100:.1f}%)")

print("\n✅ Training completed successfully.")