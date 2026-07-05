import sys
import numpy as np
import tensorflow as tf
from tensorflow import keras
import os

def load_model_safely(model_path):
    try:
        model = keras.models.load_model(model_path, compile=False)
        return model
    except Exception as e:
        print(f"⚠️ Error loading model: {e}", file=sys.stderr)
        try:
            model = keras.models.load_model(
                model_path,
                compile=False,
                custom_objects={'mse': tf.keras.losses.MeanSquaredError()}
            )
            return model
        except Exception as e2:
            print(f"❌ Error reloading: {e2}", file=sys.stderr)
            return None

def main():
    if len(sys.argv) < 4:
        print("Usage: python dqn_predict_batch.py <input_file> <output_file> <model_file>", file=sys.stderr)
        sys.exit(1)

    input_file = sys.argv[1]
    output_file = sys.argv[2]
    model_file = sys.argv[3]

    model = load_model_safely(model_file)
    if model is None:
        print(f"❌ Model {model_file} not loaded. Using default action=0 (SELECT)", file=sys.stderr)
        try:
            with open(input_file, 'r', encoding='utf-8') as f:
                lines = f.readlines()
        except:
            lines = []
        with open(output_file, 'w', encoding='utf-8') as f:
            for _ in lines:
                f.write("0\n")
        sys.exit(1)

    try:
        with open(input_file, 'r', encoding='utf-8') as f:
            lines = f.readlines()
    except Exception as e:
        print(f"❌ Error reading {input_file}: {e}", file=sys.stderr)
        sys.exit(1)

    if not lines:
        print("⚠️ Input file is empty", file=sys.stderr)
        sys.exit(0)

    states_list = []
    for line in lines:
        line = line.strip()
        if not line:
            continue
        parts = line.split(',')
        try:
            state = np.array([float(x) for x in parts], dtype=np.float32)
            states_list.append(state)
        except ValueError as e:
            print(f"⚠️ Error parsing state: {line[:50]}... -> {e}", file=sys.stderr)
            continue

    if not states_list:
        print("❌ No valid states found", file=sys.stderr)
        sys.exit(1)

    states = np.array(states_list)

    try:
        q_values = model.predict(states, verbose=0)
        actions = np.argmax(q_values, axis=1)
    except Exception as e:
        print(f"❌ Error in prediction: {e}", file=sys.stderr)
        actions = np.zeros(len(states), dtype=int)

    try:
        with open(output_file, 'w', encoding='utf-8') as f:
            for a in actions:
                f.write(f"{int(a)}\n")
        # استفاده از متن ساده به جای کاراکتر یونیکد
        print(f"[OK] Prediction completed: {len(actions)} actions written to {output_file}")
    except Exception as e:
        print(f"❌ Error writing {output_file}: {e}", file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()