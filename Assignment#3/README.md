## Programming Assigment 2
This is the description file of the task <br>
[Link & Network Layer Forwarding](https://github.com/louisja1/ComputerNetworkingAssignments/blob/master/Assignment%233/Mininet-P2.docx)<br>
### Step 1. Preparing The Environment
To prepare the VM: <br>
1.Install required packages
```
sudo apt-get update
```
```
sudo apt-get install -y python-dev python-setuptools flex bison ant openjdk-7-jdk git screen
```
2.Install ltprotocol
```
cd ~ 
```
```
git clone git://github.com/dound/ltprotocol.git 
```
```
cd ltprotocol 
```
```
sudo python setup.py install
```
3.Checkout the appropriate version of POX
```
cd ~
```
```
git clone https://github.com/noxrepo/pox
```
```
cd ~/pox 
```
```
git checkout f95dd1
```
4.Get the [starter codes](http://www.cs.sjtu.edu.cn//~yzhu/courses/comnet_18fall/ProgrammingAssignments/progAssign2.tar)<br>
5.Symlink POX and configure the POX modules
```
cd ~/progAssign2
```
```
ln -s ../pox
```
```
./config.sh
```
### Step 2. Implement Virtual Switch
#### Forwarding Packets
You should complete the `handlePacket()` method in the `cs.sdn.vnet.sw.Switch` class to send a received packet out the appropriate interface(s) of the switch. You can use the `getSourceMAC()` and `getDestinationMAC()` methods in the `net.floodlightcontroller.packet.Ethernet` class to determine the source and destination MAC addresses of the received packet.
You should call the `sendPacket()` function inherited from the `cs.sdn.vnet.Device` class to send a packet out a specific interface. To broadcast/flood a packet, you can call this method multiple times with a different interface specified each time. The interfaces variable inherited from the Device class contains all interfaces on the switch. The interfaces on a switch only have names; they do not have MAC addresses, IP addresses, or subnet masks.
You will need to add structures and/or classes to track the MAC addresses, and associated interfaces, learned by your switch. You should timeout learned MAC addresses after 15 seconds. The timeout does not need to be exact; a granularity of 1 second is fine. The timeout for a MAC address should be reset whenever the switch receives a new packet originating from that address.
#### Test switch with 3 terminals
1.Start Mininet emulation by running the following commands:
```
cd ~/progAssign2/
```
```
sudo python ./run_mininet.py topos/single_sw.topo
```
2.Open another terminal. Start the controller, by running the following commands:
```
cd ~/progAssign2/
```
```
./run_pox.sh
```
3.Open a third terminal. Build and start the virtual switch, by running the following commands:
```
cd ~/progAssign2/
```
```
ant
```
```
java -jar VirtualNetwork.jar -v s1
```
4.Go back to the terminal where Mininet is running. To issue a command on an emulated host, type the hostname followed by the command in the Mininet console. Only the host on which to run the command should be specified by name; any arguments for the command should use IP addresses. For example, the following command sends 2 ping packets from h1 to h2:
```
mininet> h1 ping -c 2 10.0.1.102
```
### Step 3.Implement Virtual Router
#### Route Lookups
Your first task is to complete the `lookup()` function in the `cs.sdn.vnet.rt.RouteTable` class. Given an IP address, this function should return the RouteEntry object that has _the longest prefix match_ with the given IP address. If no entry matches, then the function should return null.
#### Checking Packets
Your second task is to complete the `handlePacket()` method in the `cs.sdn.vnet.rt.Router` class to update and send a received packet out the appropriate interface of the router.
When an Ethernet frame is received, you should first check if it contains an IPv4 packet. You can use the `getEtherType()` method in the `net.floodlightcontroller.packet.Ethernet` class to determine the type of packet contained in the payload of the Ethernet frame. If the packet is not IPv4, you do not need to do any further processing—i.e., your router should drop the packet.
If the frame contains an IPv4 packet, then you should verify the checksum and TTL of the IPv4 packet. You use the `getPayload()` method of the Ethernet class to get the IPv4 header; you will need to cast the result to `net.floodlightcontroller.packet.IPv4`.
The IP checksum should only be computed over the IP header. The length of the IP header can be determined from the header length field in the IP header, which specifies the length of the IP header in 4-byte words (i.e., multiple the header length field by 4 to get the length of the IP header in bytes). The checksum field in the IP header should be zeroed before calculating the IP checksum. You can borrow code from the `serialize()` method in the IPv4 class to compute the checksum. If the checksum is incorrect, then you do not need to do any further processing—i.e., your router should drop the packet.
After verifying the checksum, you should decrement the IPv4 packet’s TTL by 1. If the resulting TTL is 0, then you do not need to do any further processing—i.e., your router should drop the packet.
Now, you should determine whether the packet is destined for one of the router’s interfaces. The interfaces variable inherited from the Device class contains all interfaces on the router. Each interface has a name, MAC address, IP address, and subnet mask. If the packet’s destination IP address exactly matches one of the interface’s IP addresses (not necessarily the incoming interface), then you do not need to do any further processing—i.e., your router should drop the packet.
Forwarding Packets
IPv4 packets with a correct checksum, TTL > 1 (pre decrement), and a destination other than one of the router’s interfaces should be forwarded. You should use the `lookup()` method in the RouteTable class, which you implemented earlier, to obtain the RouteEntry that has the longest prefix match with the destination IP address. If no entry matches, then you do not need to do any further processing—i.e., your router should drop the packet.
If an entry matches, then you should determine the next-hop IP address and lookup the MAC address corresponding to that IP address. You should call the `lookup()` method in the `cs.sdn.vnet.rt.ArpCache` class to obtain the MAC address from the statically populated ARP cache. This address should be the new destination MAC address for the Ethernet frame.  The MAC address of the outgoing interface should be the new source MAC address for the Ethernet frame.
After you have correctly updated the Ethernet header, you should call the `sendPacket()` function inherited from the `cs.sdn.vnet.Device` class to send the frame out the correct interface.
#### Test router
You can test your learning switch by following the directions from _Test switch with 3 terminals_. However, when starting your virtual router, you must include the appropriate static route table and static ARP cache as arguments. For example:
```
java -jar VirtualNetwork.jar -v r1 -r rtable.r1 -a arp_cache
```
