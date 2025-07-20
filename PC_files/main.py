import tkinter as tk
import subprocess
import signal
import os
import sys

current_process = None

def stop_current_process():
    global current_process
    if current_process and current_process.poll() is None:
        print("🔴 Stopping running process...")
        if os.name == 'nt':
            current_process.send_signal(signal.CTRL_BREAK_EVENT)
        else:
            current_process.terminate()
        current_process.wait()
        current_process = None
        print("🛑 Process stopped.")

def run_script(script_name):
    global current_process
    stop_current_process()

    script_path = os.path.join("pc_app", "sockets", script_name)
    python_executable = os.path.join("venv", "Scripts", "python.exe")

    print(f"🚀 Starting {script_name}...")
    current_process = subprocess.Popen(
        [python_executable, script_path],
        creationflags=subprocess.CREATE_NEW_PROCESS_GROUP if os.name == 'nt' else 0
    )

def on_close():
    stop_current_process()
    print("🧹 Exiting RemoteFusion App.")
    window.destroy()
    sys.exit(0)

# GUI Setup
window = tk.Tk()
window.title("RemoteFusion Mode Selector")
window.geometry("320x170")
window.configure(bg="#f0f0f0")

btn1 = tk.Button(window, text="PC to Mobile", font=("Segoe UI", 12), command=lambda: run_script("client.py"), height=2, width=25)
btn1.pack(pady=10)

btn2 = tk.Button(window, text="Mobile to PC", font=("Segoe UI", 12), command=lambda: run_script("server.py"), height=2, width=25)
btn2.pack(pady=10)

stop_btn = tk.Button(window, text="⛔ Stop", font=("Segoe UI", 11), command=stop_current_process, height=1, width=10, fg="white", bg="red")
stop_btn.pack(pady=5)

window.protocol("WM_DELETE_WINDOW", on_close)
window.mainloop()
