# UDPPingerServer.py
# We will need the following module to generate randomized lost packets
import random
import socket
import time
# Create a UDP socket
# Notice the use of SOCK_DGRAM for UDP packets
serverSocket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
# Assign IP address and port number to socket
serverSocket.bind(('127.0.0.1', 2333))

timeout = 5.0

sequenceNumbers = [0]
timeList = [0.0]
cnt = 0

message = serverSocket.recv(1024)
print("Heartbeat : %s" % (str(message)))
_, sequenceNumbers[cnt], timeList[cnt] = message.split()
cnt = cnt + 1

while True:
    try :
        serverSocket.settimeout(timeout)
        sequenceNumbers.append(0)
        timeList.append(0.0)
        message = serverSocket.recv(1024).decode()
        print("Heartbeat : %s" % (str(message)))
        _, sequenceNumbers[cnt], timeList[cnt] = message.split()
        print("Time difference : %.8f" % (float(timeList[cnt]) - float(timeList[cnt - 1])))
        print("Loss %d packets : " % (int(sequenceNumbers[cnt]) - int(sequenceNumbers[cnt - 1]) - 1), end = "")
        for i in range(int(sequenceNumbers[cnt - 1]) + 1, int(sequenceNumbers[cnt])) :
            print(i, end = " ")
        print("")
        cnt = cnt + 1
    except socket.timeout :
        print("The client application has stopped")
        break

serverSocket.close()
