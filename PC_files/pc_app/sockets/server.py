import asyncio
import websockets
import socket
from pynput.mouse import Controller as MouseController, Button
from pynput.keyboard import Controller as KeyboardController, Key

class RemoteFusionServer:
    def __init__(self, host="0.0.0.0", port=8765):
        # --- Basic Setup ---
        self.host = host
        self.port = port
        self.mouse = MouseController()
        self.keyboard = KeyboardController()
        
        # --- State Management ---
        self.connected_client = None
        self.broadcast_task = None

    # --- Command Simulation Methods ---

    def simulate_mouse_move(self, dx, dy):
        self.mouse.move(float(dx), float(dy))

    def simulate_left_click(self):
        self.mouse.click(Button.left, 1)

    def simulate_right_click(self):
        self.mouse.click(Button.right, 1)

    def simulate_typing(self, text):
        for char in text:
            if char == '\b':
                self.keyboard.press(Key.backspace)
                self.keyboard.release(Key.backspace)
            else:
                self.keyboard.type(char)
    
    # NEW: Scroll functionality added
    def simulate_scroll(self, dy):
        # pynput scroll takes (dx, dy). We only need vertical (dy).
        self.mouse.scroll(0, int(dy))

    # --- Broadcasting Logic ---

    async def _start_broadcast_loop(self):
        """Asynchronously broadcasts server info until cancelled."""
        udp_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
        udp_sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
        
        pc_name = socket.gethostname()
        message = f"REMOTE_FUSION_PC|NAME={pc_name}|PORT={self.port}".encode('utf-8')
        broadcast_address = ('<broadcast>', 54665)

        print("📢 Starting UDP broadcast for discovery...")
        try:
            while True:
                udp_sock.sendto(message, broadcast_address)
                await asyncio.sleep(5)  # Use asyncio.sleep for non-blocking delay
        except asyncio.CancelledError:
            print("📢 Stopping UDP broadcast.")
        finally:
            udp_sock.close()

    # --- WebSocket Connection Handling ---

    async def handle_connection(self, websocket):
        """Manages a single client connection."""
        if self.connected_client:
            print("📱 Another client tried to connect. Denying.")
            await websocket.close(1013, "Server is busy with another client.")
            return

        self.connected_client = websocket
        # Stop broadcasting when a client connects
        if self.broadcast_task:
            self.broadcast_task.cancel()

        print(f"📱 Client connected from {websocket.remote_address}")
        try:
            async for message in websocket:
                self.process_command(message)
        except websockets.exceptions.ConnectionClosed as e:
            print(f"❌ Client disconnected: {e.reason} (Code: {e.code})")
        except Exception as e:
            print(f"💥 An unexpected error occurred with client: {e}")
        finally:
            # This block runs whether the disconnect was clean or from an error
            self.connected_client = None
            print("✅ Client connection closed. Resuming discovery broadcast...")
            # Restart broadcasting so the server can be discovered again
            self.broadcast_task = asyncio.create_task(self._start_broadcast_loop())

    def process_command(self, message):
        """Parses and executes commands from the client."""
        try:
            command, value = message.split(':', 1)
            
            if command == "mouse_move":
                dx, dy = value.split(',')
                self.simulate_mouse_move(dx, dy)
            elif command == "type":
                self.simulate_typing(value)
            elif command == "mouse_scroll":
                self.simulate_scroll(value)
            # Commands without a value
            elif value == "":
                if command == "mouse_lclick":
                    self.simulate_left_click()
                elif command == "mouse_rclick":
                    self.simulate_right_click()
        except Exception:
            # Handles commands without a ":" like "mouse_lclick"
            if message == "mouse_lclick":
                self.simulate_left_click()
            elif message == "mouse_rclick":
                self.simulate_right_click()
            else:
                print(f"⚠️ Received unknown or malformed command: {message}")


    # --- Main Server Execution ---

    async def run(self):
        """Starts the server and the initial broadcast."""
        # Start broadcasting immediately on launch
        self.broadcast_task = asyncio.create_task(self._start_broadcast_loop())
        
        async with websockets.serve(self.handle_connection, self.host, self.port):
            print(f"🚀 WebSocket Server listening on ws://{self.host}:{self.port}")
            await asyncio.Future()  # Run forever

if __name__ == "__main__":
    server = RemoteFusionServer()
    try:
        asyncio.run(server.run())
    except KeyboardInterrupt:
        print("\n🛑 Server shutting down.")
    finally:
        if server.broadcast_task:
            server.broadcast_task.cancel()
