from socket import *
import sys
import os
import hashlib

def HashEncode(origin):
    tool = hashlib.md5()
    tool.update(origin.encode('utf-8'))
    new = tool.hexdigest()
    return new

# Create a server socket, bind it to a port and start listening
tcpSerSock = socket(AF_INET, SOCK_STREAM)
# Fill in start.
host = '127.0.0.1'
post = 2335
address = (host, post)
tcpSerSock.bind(address)
tcpSerSock.listen(1)
# Fill in end.
while 1:
    # Strat receiving data from the client
    print('Ready to serve...')
    tcpCliSock, addr = tcpSerSock.accept()
    print('Received a connection from : ', addr)
    message = tcpCliSock.recv(1024)# Fill in start. # Fill in end.
    if message == b'' :
        tcpCliSock.close()
        continue
    message = message.decode()
    print('Message : $$$%s$$$' % [message])
    # Extract the filename from the given message
    # print(message.split()[1])
    url = message.split()[1].partition("/")[2]
    print('URL : %s' % url)
    fileExist = "false"
    fileOld = os.path.join('./cache', url)
    fileNew = os.path.join('./cache', HashEncode(url))
    print("FileOld : %s" % fileOld)
    print("FileNew : %s" % fileNew)
    #print("111111 ", os.path.exists(fileOld))
    #print("222222 ", os.path.exists(fileNew))
    try:
        # Check wether the file exist in the cache
        if (not os.path.exists(fileOld) and os.path.exists(fileNew)) :
            fileOld = fileNew
        with open(fileOld, "rb") as f :
            #print("1111111111")
            outputdata = f.read()
            fileExist = "true"
            print('File exists')
            # ProxyServer finds a cache hit and generates a response message
            # Fill in start.
            tcpCliSock.send("HTTP/1.0 200 OK\r\n".encode())
            tcpCliSock.send("Content-Type:text/html\r\n\r\n".encode())
            tcpCliSock.send(outputdata + "\r\n".encode())
            # Fill in end.
            print('========= Read from cache =========')
    # Error handling for file not found in cache
    except IOError:
        if fileExist == "false":
            print('File does not exist')
            # Create a socket on the proxyserver
            c = socket(AF_INET, SOCK_STREAM)# Fill in start. # Fill in end.
            hostn = url.replace("www.", "", 1)
            print('Hostn : %s' % hostn)
            try:
                # Connect to the socket to port 80
                # Fill in start.
                print("!!!!!!!!!!!!!!!!!!!!!!!")
                c.connect((hostn, 80))
                # Fill in end.
                # Create a temporary file on this socket and ask port 80 for the file requested by the client
                getmessage = ("GET http://" + url + " HTTP/1.0\r\nHost: " + hostn + "\r\n\r\n").encode()
                print("Send : %s" % getmessage)
                c.send(getmessage)
                #Read the response into buffer
                # Fill in start.
                buff = b''
                while True :
                    tmp = c.recv(1024)
                    buff += tmp
                    if not tmp :
                        break
                #print("Buff : %s" % buff)
                header, data = buff.split(b'\r\n\r\n', 1)
                #print("Header : %s" % header)
                #print("Data : %s" % data)
                status = header.split()
                #print("Status : " , status[1])
                if status[1] == b'404' :
                    tcpCliSock.send('HTTP/1.0 404 Not Found'.encode())
                    print('========= 404 Not Found =========')
                else :
                    tmpFile = open(fileNew, "wb")
                    tmpFile.write(data)
                    tcpCliSock.send('HTTP/1.0 200 OK'.encode())
                    tcpCliSock.send(data)
                    tmpFile.close()
                    print('========= Get it =========')
                # Fill in end.
                # Create a new file in the cache for the requested file.
                # Also send the response in the buffer to client socket and the corresponding file in the cache
                #tmpFile = open("./" + filename,"wb")
                # Fill in start.
                #for i in range(0, len(buff)) :
                #    tmpFile.write(buff[i])
                #    tcpCliSock.send(buff[i])
                tcpCliSock.send(buff)
                c.close()
                # Fill in end.
            except:
                print('========= Illegal request =========')
        else:
            # HTTP response message for file not found
            # Fill in start.
            print('File not found')
            tcpCliSock.send('HTTP/1.0 404 Not Found'.encode())
            # Fill in end.
    # Close the client and the server sockets
    tcpCliSock.close()
tcpSerSock.close()
