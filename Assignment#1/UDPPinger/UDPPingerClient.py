import sys
import socket
import time
import random

clientSocket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
host = '127.0.0.1'
post = 2333
address = (host, post)
timeout = 1.0
packetsRTT = []
clientSocket.settimeout(timeout)

for i in range(10) :
    try :
        startTime = time.time()
        message = ' '.join(('Ping', str(i + 1), str(time.asctime(time.localtime(startTime)))))
        clientSocket.sendto(message.encode(), address)
        outputdata = clientSocket.recv(1024)
        endTime = time.time()
        RTT = (endTime - startTime)
        packetsRTT.append(RTT)
        print("## Ping %d ##" % (i + 1))
        print("Receive message : %s" % str(outputdata.decode()))
        print("RTT : %.8f" % RTT)
    except socket.timeout :
        print("## Ping %d ##" % (i + 1))
        print("Request timed out")

loss_cnt = 10 - len(packetsRTT)
if loss_cnt == 10 :
    packetsRTT.append(1)
print("Packet loss rate : {}%".format(loss_cnt * 10))
print("Minimum RTT : %.8f" % min(packetsRTT))
print("Maximum RTT : %.8f" % max(packetsRTT))
print("Average RTT : %.8f" % (sum(packetsRTT) / 10.0))
clientSocket.close()
