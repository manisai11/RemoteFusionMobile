To use RemoteFusion:
1. Go to Apps2download folder in this repo. Download RemoteFusionApk.apk in mobile and RemoteFusionPC.exe in laptop.

2. Run both apps, select the mode of operation
    a. Mobile to Pc- Mobile will have a virtual mousepad, keyboard to control laptop wirelessly.
    b. Pc to Mobile- Pc can control mobile (Under progress, adding preview in pc, cursor in mobile screen)

Implementation:

1. It uses UDP to broadcast ip addresses for visibility to be discovered by other devices.

2. Then establishes secure WebSockets connection for communication.

3. Handles a 2 way control i.e mobile as server and pc as client, mobile as client and pc as server.

Developed using: python, SocketIO for pc and Kotlin for android app development.
