#import socket module
from socket import *
import threading
def getConnection(connectionSocket, addr) :
    try :
        message = connectionSocket.recv(1024) #Fill in start #Fill in end
        print('New message : ' + str(message))
        filename = message.split()[1]
        #print(filename)
        #f = open(filename[1:])
        #outputdata =
        with open(filename[1:], 'rb') as f :
            outputdata = f.read()
        f.close()
        #Fill in start #Fill in end
        #Send one HTTP header line into socket
        #Fill in start
        connectionSocket.send(b'HTTP/1.1 200 OK\r\n\r\n')
        #Fill in end
        #Send the content of the requested file to the client
        connectionSocket.send(outputdata)
        connectionSocket.close()
    except IOError :
        #Send response message for file not found
        #Fill in start
        connectionSocket.send(b'HTTP/1.1 404 Not Found\r\n\r\n')
        #Fill in end
        #Close client clientSocket
        #Fill in start
        connectionSocket.close()
        #Fill in end
if __name__ == "__main__" :
    serverSocket = socket(AF_INET, SOCK_STREAM)
    #Prepare a servr socket
    #Fill in start
    host = '127.0.0.1'
    port = 2333
    serverSocket.bind((host, port))
    serverSocket.listen(3)
    #Fill in end
    while True :
        #Establish the connection
        print('Ready to serve ...')
        connectionSocket, addr = serverSocket.accept() #Fill in start #Fill in end
        print('Connection address %s:%s' % addr)
        thread = threading.Thread(target = getConnection, args = (connectionSocket, addr))
        thread.start()
    serverSocket.close()
