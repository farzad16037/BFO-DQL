# online_scheduler.py - نسخه نهایی با مدیریت خطا
import numpy as np
import tensorflow as tf
from tensorflow import keras
import time

print("📂 Loading trained DQN model...")

# بارگذاری مدل با مدیریت خطا
try:
    model = keras.models.load_model('dqn_model.h5', compile=False)
    print("✅ Model loaded (compile=False)")
except Exception as e:
    print(f"⚠️ First attempt failed: {e}")
    try:
        model = keras.models.load_model('dqn_model.h5', compile=False, 
                                        custom_objects={'mse': tf.keras.losses.MeanSquaredError()})
        print("✅ Model loaded with custom objects")
    except Exception as e2:
        print(f"❌ Failed to load model: {e2}")
        exit(1)

def normalize_value(value, min_val, max_val):
    if max_val - min_val == 0:
        return 0.5
    return (value - min_val) / (max_val - min_val)

def extract_state_from_environment(vms, tasks):
    vm_util = [normalize_value(vm.get('utilization', 0.5), 0, 1) for vm in vms]
    vm_workload = [normalize_value(vm.get('workload', 0), 0, 50) for vm in vms]
    vm_energy = [normalize_value(vm.get('energy', 0), 0, 250) for vm in vms]
    task_deadlines = [normalize_value(task.get('deadline_proximity', 0.5), 0, 1) for task in tasks]
    task_priorities = [normalize_value(task.get('priority', 2), 1, 3) for task in tasks]
    task_remaining = [normalize_value(task.get('remaining_time', 0), 0, 10000) for task in tasks]
    state = np.array(vm_util + vm_workload + vm_energy + task_deadlines + task_priorities + task_remaining)
    return state.reshape(1, -1)

def select_heuristic(state):
    q_values = model.predict(state, verbose=0)
    action_idx = np.argmax(q_values[0])
    heuristics = ['SELECT', 'MOVE', 'SWAP', 'DROP']
    return heuristics[action_idx], q_values[0]

class SimpleCloudSimulator:
    def __init__(self, num_vms=100, num_tasks=50):
        self.vms = [{'utilization': np.random.uniform(0,1), 'workload': np.random.randint(0,50), 'energy': np.random.uniform(0,250)} for _ in range(num_vms)]
        self.tasks = [{'deadline_proximity': np.random.uniform(0,1), 'priority': np.random.randint(1,4), 'remaining_time': np.random.uniform(0,10000)} for _ in range(num_tasks)]
    def update_state(self):
        for vm in self.vms:
            vm['utilization'] = np.clip(vm['utilization'] + np.random.uniform(-0.1,0.1), 0, 1)
            vm['workload'] = max(0, vm['workload'] + np.random.randint(-5,5))
            vm['energy'] = max(0, vm['energy'] + np.random.uniform(-10,10))
        for task in self.tasks:
            task['deadline_proximity'] = np.clip(task['deadline_proximity'] + np.random.uniform(-0.05,0.05), 0, 1)
            task['remaining_time'] = max(0, task['remaining_time'] - np.random.uniform(0,100))
            if task['remaining_time'] <= 0:
                task['remaining_time'] = np.random.uniform(0,10000)
                task['deadline_proximity'] = np.random.uniform(0,1)
                task['priority'] = np.random.randint(1,4)
    def apply_heuristic(self, heuristic):
        print(f"   Executing: {heuristic}")

def run_online_scheduler(num_steps=20):
    print("\n🔄 Starting Online Scheduler...")
    print("="*50)
    sim = SimpleCloudSimulator()
    for step in range(num_steps):
        print(f"\n📌 Step {step+1}/{num_steps}")
        state = extract_state_from_environment(sim.vms, sim.tasks)
        start = time.time()
        heuristic, qvals = select_heuristic(state)
        decision_ms = (time.time() - start) * 1000
        print(f"   Selected: {heuristic}")
        print(f"   Q-values: SELECT={qvals[0]:.3f}, MOVE={qvals[1]:.3f}, SWAP={qvals[2]:.3f}, DROP={qvals[3]:.3f}")
        print(f"   Decision time: {decision_ms:.2f} ms")
        sim.apply_heuristic(heuristic)
        sim.update_state()
    print("\n✅ Finished")

if __name__ == "__main__":
    run_online_scheduler()