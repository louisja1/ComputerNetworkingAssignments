import socket
import base64
import ssl

msg = "\r\n Do you want to fuck with me?"
endmsg = "\r\n.\r\n"
# Choose a mail server (e.g. Google mail server) and call it mailserver
mailserver = ("smtp.qq.com", 587)#Fill in start #Fill in end
# Create socket called clientSocket and establish a TCP connection with mailserver
#Fill in start
clientSocket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
clientSocket.connect(mailserver)
#Fill in end
recv = clientSocket.recv(1024)
print("Receive #0 : %s" % str(recv))
if recv[:3] != '220' :
    print("220 reply not received from server.")
# Send HELO command and print server response.
heloCommand = 'HELO QQmail\r\n'
clientSocket.send(heloCommand.encode())
recv1 = clientSocket.recv(1024)
print("Receive #1 : %s" % str(recv1))
if recv1[:3] != '250':
    print("250 reply not received from server.")

# Send STARTTLS command
starttlsCommand = 'STARTTLS\r\n'
clientSocket.send(starttlsCommand.encode())
recv2 = clientSocket.recv(1024)
print("Receive #2 : %s" % str(recv2))
clientSocket = ssl.wrap_socket(clientSocket)

# Send HELO command and print server response.
heloCommand = 'HELO QQmail\r\n'
clientSocket.send(heloCommand.encode())
recv3 = clientSocket.recv(1024)
print("Receive #3 : %s" % str(recv1))
if recv3[:3] != '250':
    print("250 reply not received from server.")

# Send Authentation Login
authCommand = 'AUTH LOGIN\r\n'
clientSocket.send(authCommand.encode())
recv4 = clientSocket.recv(1024)
print("Receive #4 : %s" % str(recv4))

# Send username
userCommand = 'Njc0ODA4NTE2QHFxLmNvbQ==\r\n'
clientSocket.send(userCommand.encode())
recv5 = clientSocket.recv(1024)
print("Receive #5 : %s" % str(recv5))
# Send password : huqb yoti hoip bdji
passwordCommand = 'aHVxYnlvdGlob2lwYmRqaQ==\r\n'
clientSocket.send(passwordCommand.encode())
recv6 = clientSocket.recv(1024)
print("Receive #6 : %s" % str(recv6))
# Send MAIL FROM command and print server response.
# Fill in start
fromCommand = "MAIL FROM: <674808516@qq.com>\r\n"
clientSocket.send(fromCommand.encode())
recv7 = clientSocket.recv(1024)
print("Receive #7 : %s" % str(recv7))
# Fill in end
# Send RCPT TO command and print server response.
# Fill in start
toCommand = "RCPT TO: <674808516@qq.com>\r\n"
clientSocket.send(toCommand.encode())
recv8 = clientSocket.recv(1024)
print("Receive #8 : %s" % str(recv8))
# Fill in end
# Send DATA command and print server response.
# Fill in start
dataCommand = "DATA\r\n"
clientSocket.send(dataCommand.encode())
recv9 = clientSocket.recv(1024)
print("Receive #9 : %s" % str(recv9))
# Fill in end
# Send Header
Header = 'From: \"Jiangmengjuan\" <{dengwxn@sjtu.edu.cn}>\r\nTo: \"Liuyuxi\"<{dengwxn@sjtu.edu.cn}>\r\nSubject: Fuck with me\r\n'
clientSocket.send(Header.encode())
# Send MIME header
mimeHeader = '\r\n'.join(('MIME-Version: 1.0', 'Content-Type: multipart/mixed; boundary=frontier', '\r\n'))
clientSocket.send(mimeHeader.encode())
# Send TEXT header
textHeader = '\r\n'.join(('\r\n', '--frontier', 'Content-Type: text/plain; charset=utf-8', '\r\n'))
clientSocket.send(textHeader.encode())
# Send message data.
# Fill in start
clientSocket.send(msg.encode())
# Fill in end
# Send IMAGE header
imageHeader = '\r\n'.join(('\r\n', '--frontier', 'Content-Type: image/png; name=wxy.png', 'Content-Transfer-Encoding: base64', '\r\n'))
clientSocket.send(imageHeader.encode())
# Send IMAGE
with open('wxy.png', 'rb') as f :
    imageData = f.read()
imageData = base64.b64encode(imageData)
clientSocket.send(imageData)
# Send Frontier
frontier = '\r\n\r\n--frontier--\r\n\r\n'
clientSocket.send(frontier.encode())
# Message ends with a single period.
# Fill in start
clientSocket.send(endmsg.encode())
# Fill in end
# Send QUIT command and get server response.
# Fill in start
quitCommand = "QUIT\r\n"
clientSocket.send(quitCommand.encode())
recv10 = clientSocket.recv(1024)
print("Receive #10 : %s" % str(recv10))
# Fill in end
clientSocket.close()
