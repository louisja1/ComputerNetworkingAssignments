import socket
import time
import random

clientSocket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)

host = '127.0.0.1'
post = 2333
address = (host, post)

sleepTime = 1.0
sequenceNumber = 0

while True :
    time.sleep(sleepTime)
    sequenceNumber = sequenceNumber + 1
    localTime = time.time()
    message = ' '.join(('Heartbeat', str(sequenceNumber), str(localTime)))
    rand = random.randint(0, 10)
    if rand < 4 :
        continue
    print("Send Message %s to %s" % (message, address))
    clientSocket.sendto(message.encode(), address)
clientSocket.close()
