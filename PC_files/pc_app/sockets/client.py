import asyncio
import websockets
import json
import socket
import threading
import time
import sys

# --- Configuration ---
DISCOVERY_BROADCAST_PORT = 48888
DISCOVERY_MESSAGE_PREFIX = "RF_MOBILE_SERVER_DISCOVERY:"

# --- Global state ---
discovered_devices = {}
discovery_lock = threading.Lock()
event_queue = asyncio.Queue()
stop_event = threading.Event()

# --- UDP Discovery Listener ---
def udp_discovery_listener():
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    try:
        sock.bind(('', DISCOVERY_BROADCAST_PORT))
    except OSError as e:
        print(f"[Discovery Error] Error binding UDP socket: {e}")
        return

    sock.settimeout(1.0)
    while not stop_event.is_set():
        try:
            data, addr = sock.recvfrom(1024)
            message = data.decode('utf-8')
            if message.startswith(DISCOVERY_MESSAGE_PREFIX):
                parts = message[len(DISCOVERY_MESSAGE_PREFIX):].split(':')
                if len(parts) == 2:
                    ip_address = parts[0]
                    try:
                        ws_port = int(parts[1])
                    except ValueError:
                        continue
                    device_id = f"{ip_address}:{ws_port}"
                    with discovery_lock:
                        discovered_devices[device_id] = {
                            "ip": ip_address, "port": ws_port, "last_seen": time.time()
                        }
        except socket.timeout:
            pass
        except Exception as e:
            print(f"[Discovery Error] {e}")
            break
    sock.close()

# --- Send function ---
async def send_command(websocket, command_type, data):
    command = {"type": command_type, "data": data}
    try:
        await websocket.send(json.dumps(command))
    except websockets.exceptions.ConnectionClosed:
        print("Error: WebSocket connection closed.")
    except Exception as e:
        print(f"Error sending command: {e}")

# --- Event sender ---
async def send_events_from_queue(websocket):
    while True:
        try:
            event = await event_queue.get()
            await send_command(websocket, event["type"], event["data"])
            event_queue.task_done()
        except asyncio.CancelledError:
            break
        except Exception as e:
            print(f"Error in event sender: {e}")
            break

# --- WebSocket listener (optional) ---
async def listen_for_responses(websocket):
    try:
        async for message in websocket:
            print(f"Received from mobile: {message}")
    except:
        pass

# --- Device menu and connection loop ---
async def client_menu():
    while True:
        print("\n📡 Searching for devices... Press 's' to show, 'c' to connect, 'q' to quit.")
        user_input = (await asyncio.to_thread(input, "Enter command: ")).strip().lower()

        if user_input == 'q':
            stop_event.set()
            return

        elif user_input == 's':
            with discovery_lock:
                if not discovered_devices:
                    print("No devices discovered yet.")
                else:
                    print("\n--- Discovered Devices ---")
                    sorted_devices = sorted(discovered_devices.items(), key=lambda item: item[1]['last_seen'], reverse=True)
                    for i, (dev_id, info) in enumerate(sorted_devices):
                        print(f"{i+1}. {info['ip']}:{info['port']}")

        elif user_input == 'c':
            with discovery_lock:
                if not discovered_devices:
                    print("No devices to connect to.")
                    continue
                device_list = list(discovered_devices.values())
                try:
                    choice = int(await asyncio.to_thread(input, "Enter number: "))
                    if 1 <= choice <= len(device_list):
                        selected_device = device_list[choice - 1]
                        await handle_connection(selected_device["ip"], selected_device["port"])
                    else:
                        print("Invalid choice.")
                except ValueError:
                    print("Please enter a valid number.")

# --- WebSocket connection logic ---
async def handle_connection(ip, port):
    uri = f"ws://{ip}:{port}"
    print(f"\n🔌 Connecting to {uri}...")
    try:
        async with websockets.connect(uri) as websocket:
            print("✅ Connected. Type your message below.")
            print("Type '/exit' to disconnect and return to menu.")
            listen_task = asyncio.create_task(listen_for_responses(websocket))
            sender_task = asyncio.create_task(send_events_from_queue(websocket))

            try:
                while True:
                    text = await asyncio.to_thread(input, "> ")
                    if text.strip() == "/exit":
                        print("🔌 Disconnecting...")
                        break
                    await event_queue.put({"type": "TEXT", "data": {"text": text}})
            except KeyboardInterrupt:
                print("\nInterrupted.")
            finally:
                listen_task.cancel()
                sender_task.cancel()
    except Exception as e:
        print(f"❌ Failed to connect: {e}")

# --- Main Entry ---
async def client_main():
    udp_thread = threading.Thread(target=udp_discovery_listener, daemon=True)
    udp_thread.start()

    print("🚀 RemoteFusion PC Keyboard Client")
    try:
        await client_menu()
    except KeyboardInterrupt:
        print("\nInterrupted by user.")
    finally:
        stop_event.set()

if __name__ == "__main__":
    if sys.platform == "win32":
        asyncio.set_event_loop_policy(asyncio.WindowsProactorEventLoopPolicy())

    try:
        asyncio.run(client_main())
    except KeyboardInterrupt:
        print("Stopped by user.")
