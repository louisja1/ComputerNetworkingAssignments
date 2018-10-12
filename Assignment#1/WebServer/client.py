import socket
import sys

def get(host, port, filename) :
    clientSocket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    port = int(port)
    clientSocket.connect((host, port))
    message = "GET /" + filename + " HTTP/1.1\r\n\r\n"
    clientSocket.send(message.encode('utf-8'));
    while True :
        data = clientSocket.recv(1024)
        print(data)
        if not data :
            break
    clientSocket.close()
    return

if __name__ == '__main__' :
    if len(sys.argv) < 4 :
        print(sys.argv)
        print("Error in the input format.")
        print("Please input like that : python3 client.py server_host server_post filename")
        sys.exit();
    get(sys.argv[1], sys.argv[2], sys.argv[3]);
