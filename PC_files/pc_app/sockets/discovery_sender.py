import socket
import threading
import time

def broadcast_server_ip(port=8080, broadcast_port=54665, interval=3):
    pc_name = socket.gethostname()
    message = f"REMOTE_FUSION_PC|NAME={pc_name}|PORT={port}"
    broadcast_ip = '255.255.255.255'
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)

    def broadcast_loop():
        while True:
            try:
                #print(message.encode(), (broadcast_ip, broadcast_port))
                sock.sendto(message.encode('utf-8'), (broadcast_ip, broadcast_port))
                time.sleep(interval)
            except Exception as e:
                print(f"[Broadcast Error] {e}")
                break

    thread = threading.Thread(target=broadcast_loop, daemon=True)
    thread.start()
