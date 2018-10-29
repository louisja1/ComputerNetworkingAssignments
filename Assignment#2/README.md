## Programming Assigment 2
This is the description file of the task <br>
[Mininet](https://github.com/louisja1/ComputerNetworkingAssignments/blob/master/Assignment%232/Mininet-P1.pdf) <br>
### Step 1 Write Iperfer
The tool can send and receive TCP packets between a pair of hosts using sockets, which contains both `Client Mode` and `Server Mode`. The commands are :
``` Client Mode
java Iperfer -c -h <server hostname> -p <server port> -t <time>
```
``` Server Mode
java Iperfer -s -p <listen port>
```
### Step 2 Mininet Tutorial
#### Part 1: Everyday Mininet Usage
+ Display Startup Options
+ Interact with Hosts and Switches
+ Test connectivity between hosts
+ Run a simple web server and client
+ Cleanup
#### Part 2: Advanced Startup Options
+ Run a Regression Test
+ Changing Topology Size and Type
+ Link variations
+ Adjustable Verbosity
#### Part 3: Mininet Command-Line Interface (CLI) Commands
+ Display Options
+ Python Interpreter
+ Link Up/Down
+ XTerm Display
### Step 3 Measurements in Mininet
It is important and convenient to use the `Xterm Display` for those required hosts. <br>
Desperately, I do the simultaneous experiment by my APM(action per minute). <br>
#### Latency
I measure the latency of the link or path by firstly measuring the RTT(latency = RTT / 2) between corresponding hosts using the tool `ping`.
```
ping -c <count> <IP>
```
#### Throughput
I measure the throughput of the link or path by measuring the rate between corresponding hosts using my own tool `Iperfer`.
``` Client Mode
java Iperfer -c -h <server hostname> -p <server port> -t <time>
```
``` Server Mode
java Iperfer -s -p <listen port>
```
