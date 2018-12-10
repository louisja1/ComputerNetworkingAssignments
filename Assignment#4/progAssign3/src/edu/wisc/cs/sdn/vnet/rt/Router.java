package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.*;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device
{
    /** Routing table for the router */
    private RouteTable routeTable;

    /** ARP cache for the router */
    private ArpCache arpCache;

    private Map<Integer, Queue> ipMap = new HashMap<>();

    /**
     * Creates a router for a specific host.
     * @param host hostname for the router
     */
    public Router(String host, DumpFile logfile)
    {
        super(host,logfile);
        this.routeTable = new RouteTable();
        this.arpCache = new ArpCache();
    }

    /**
     * @return routing table for the router
     */
    public RouteTable getRouteTable()
    { return this.routeTable; }

    /**
     * Load a new routing table from a file.
     * @param routeTableFile the name of the file containing the routing table
     */
    public void loadRouteTable(String routeTableFile)
    {
        if (!routeTable.load(routeTableFile, this))
        {
            System.err.println("Error setting up routing table from file "
                    + routeTableFile);
            System.exit(1);
        }

        System.out.println("Loaded static route table");
        System.out.println("-------------------------------------------------");
        System.out.print(this.routeTable.toString());
        System.out.println("-------------------------------------------------");
    }

    /**
     * Load a new ARP cache from a file.
     * @param arpCacheFile the name of the file containing the ARP cache
     */
    public void loadArpCache(String arpCacheFile)
    {
        if (!arpCache.load(arpCacheFile))
        {
            System.err.println("Error setting up ARP cache from file "
                    + arpCacheFile);
            System.exit(1);
        }

        System.out.println("Loaded static ARP cache");
        System.out.println("----------------------------------");
        System.out.print(this.arpCache.toString());
        System.out.println("----------------------------------");
    }

    /**
     * Handle an Ethernet packet received on a specific interface.
     * @param etherPacket the Ethernet packet that was received
     * @param inIface the interface on which the packet was received
     */
    public void handlePacket(Ethernet etherPacket, Iface inIface)
    {
        System.out.println("*** -> Received packet: " +
                etherPacket.toString().replace("\n", "\n\t"));

        /********************************************************************/
        /* TODO: Handle packets                                             */

        switch(etherPacket.getEtherType())
        {
            case Ethernet.TYPE_IPv4 :
                this.handleIpPacket(etherPacket, inIface);
                break;
            case Ethernet.TYPE_ARP :
                this.handleArpPacket(etherPacket, inIface);
                break;
            // Ignore all other packet types, for now
        }

        /********************************************************************/
    }

    private void handleIpPacket(Ethernet etherPacket, Iface inIface)
    {
        // Make sure it's an IP packet
        if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4)
        { return; }

        // Get IP header
        IPv4 ipPacket = (IPv4)etherPacket.getPayload();
        System.out.println("Handle IP packet");

        // Verify checksum
        short origCksum = ipPacket.getChecksum();
        ipPacket.resetChecksum();
        byte[] serialized = ipPacket.serialize();
        ipPacket.deserialize(serialized, 0, serialized.length);
        short calcCksum = ipPacket.getChecksum();
        if (origCksum != calcCksum)
        { return; }

        // Check TTL
        ipPacket.setTtl((byte)(ipPacket.getTtl()-1));
        if (0 == ipPacket.getTtl())
        {
            // Time exceeded
            sendICMPPacket(ipPacket, inIface, (byte) 11, (byte) 0);
            return;
        }

        // Reset checksum now that TTL is decremented
        ipPacket.resetChecksum();

        if (routeTable.isRIP() && ipPacket.getProtocol() == IPv4.PROTOCOL_UDP && ipPacket.getDestinationAddress() == IPv4.toIPv4Address("224.0.0.9")) {
            UDP udpPacket = (UDP) ipPacket.getPayload();
            if (udpPacket.getDestinationPort() == UDP.RIP_PORT) {
                System.out.println("This is a RIP packet : ");
                RIPv2 ripPacket = (RIPv2) udpPacket.getPayload();
                if (ripPacket.getCommand() == RIPv2.COMMAND_REQUEST) {
                    sendPacket(generateRIPResponse(inIface.getIpAddress(), inIface.getMacAddress()), inIface);
                    return;
                } else if (ripPacket.getCommand() == RIPv2.COMMAND_RESPONSE){
                    updateRIP(etherPacket, inIface);
                    return;
                }
            }
        }

        // Check if packet is destined for one of router's interfaces
        for (Iface iface : this.interfaces.values())
        {
            if (ipPacket.getDestinationAddress() == iface.getIpAddress())
            {
                // Destination port unreachable
                byte protocol = ipPacket.getProtocol();
                if (protocol == IPv4.PROTOCOL_TCP || protocol == IPv4.PROTOCOL_UDP) {
                    sendICMPPacket(ipPacket, inIface, (byte) 3, (byte) 3);
                } else if (protocol == IPv4.PROTOCOL_ICMP) {
                    ICMP icmp = (ICMP) ipPacket.getPayload();
                    if (icmp.getIcmpType() == ICMP.TYPE_ECHO_REQUEST) {
                        // Echo reply
                        sendICMPPacket(ipPacket, inIface, (byte) 0, (byte) 0);
                    }

                }
                return;
            }
        }

        // Do route lookup and forward
        this.forwardIpPacket(etherPacket, inIface);
    }

    private void forwardIpPacket(Ethernet etherPacket, Iface inIface)
    {
        // Make sure it's an IP packet
        if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4)
        { return; }
        System.out.println("Forward IP packet");

        // Get IP header
        IPv4 ipPacket = (IPv4)etherPacket.getPayload();
        int dstAddr = ipPacket.getDestinationAddress();

        // Find matching route table entry
        RouteEntry bestMatch = this.routeTable.lookup(dstAddr);

        // If no entry matched, do nothing
        if (null == bestMatch)
        {
            // Destination net unreachable
            sendICMPPacket(ipPacket, inIface, (byte) 3, (byte) 0);
            return;
        }

        // Make sure we don't sent a packet back out the interface it came in
        Iface outIface = bestMatch.getInterface();
        if (outIface == inIface)
        { return; }

        // Set source MAC address in Ethernet header
        etherPacket.setSourceMACAddress(outIface.getMacAddress().toBytes());

        // If no gateway, then nextHop is IP destination
        int nextHop = bestMatch.getGatewayAddress();
        if (0 == nextHop)
        { nextHop = dstAddr; }

        // Set destination MAC address in Ethernet header
        ArpEntry arpEntry = this.arpCache.lookup(nextHop);
        if (null == arpEntry)
        {
            // Destination host unreachable
            // sendICMPPacket(ipPacket, inIface, (byte) 3, (byte) 1);
            if (!ipMap.containsKey(nextHop)) {
                ipMap.put(nextHop, new LinkedList());
                System.out.println("Wait for ARP reply for new ip" + IPv4.fromIPv4Address(nextHop));
            }
            generateARPRequest(etherPacket, nextHop, inIface, outIface);
            return;
        }
        etherPacket.setDestinationMACAddress(arpEntry.getMac().toBytes());

        this.sendPacket(etherPacket, outIface);
    }

    private static final int ICMP_PADDING_SIZE = 4;

    private void sendICMPPacket(IPv4 ipPacket, Iface inIface, byte type, byte code) {
        System.out.println("Send ICMP packet of type[" + String.valueOf(type) + "] and code[" + String.valueOf(code) + "]");
        Ethernet ether = new Ethernet();
        IPv4 ip = new IPv4();
        ICMP icmp = new ICMP();
        Data data = new Data();
        ether.setPayload(ip);
        ip.setPayload(icmp);
        icmp.setPayload(data);

        ether.setEtherType(Ethernet.TYPE_IPv4);

        ip.setTtl((byte) 64);
        ip.setProtocol(IPv4.PROTOCOL_ICMP);
        ip.setSourceAddress(inIface.getIpAddress());
        if(type == 0){
            ip.setSourceAddress(ipPacket.getDestinationAddress());
        } else {
            ip.setSourceAddress(inIface.getIpAddress());
        }
        ip.setDestinationAddress(ipPacket.getSourceAddress());

        icmp.setIcmpType(type);
        icmp.setIcmpCode(code);

        if (type != 0) {
            byte[] serialize = ipPacket.serialize();
            int headerLength = ipPacket.getHeaderLength() * 4;
            byte[] buffer = new byte[ICMP_PADDING_SIZE + headerLength + 8];
            for (int i = 0; i < headerLength + 8; i++) {
                buffer[ICMP_PADDING_SIZE + i] = serialize[i];
            }
            data.setData(buffer);
        } else {
            // echo reply
            ICMP icmpPacket = (ICMP) ipPacket.getPayload();
            byte[] icmpPayload = icmpPacket.getPayload().serialize();
            data.setData(icmpPayload);
        }
        forwardIpPacket(ether, null);
    }

    private void handleArpPacket(Ethernet etherPacket, Iface inIface) {
        // Make sure it's an IP packet
        if (etherPacket.getEtherType() != Ethernet.TYPE_ARP)
        { return; }
        System.out.println("Forward ARP packet");

        ARP arpPacket = (ARP) etherPacket.getPayload();
        int sourceIP = ByteBuffer.wrap(arpPacket.getSenderProtocolAddress()).getInt();
        int targetIP = ByteBuffer.wrap(arpPacket.getTargetProtocolAddress()).getInt();

        short operation = arpPacket.getOpCode();

        if (operation == ARP.OP_REQUEST) {
            if (targetIP != inIface.getIpAddress()) {
                System.out.println("The target IP is not equal to input interface IP");
                return;
            }
            // generate arp reply : construct new headers
            Ethernet ethernetHeader = generateARPPacket(etherPacket.getSourceMACAddress(), ARP.OP_REPLY, arpPacket.getSenderHardwareAddress(), arpPacket.getSenderProtocolAddress(), inIface);
            this.sendPacket(ethernetHeader, inIface);
            return;
        } else if (operation == ARP.OP_REPLY) {
            byte[] ip = arpPacket.getSenderProtocolAddress();
            byte[] mac = arpPacket.getSenderHardwareAddress();
            arpCache.insert(new MACAddress(mac), IPv4.toIPv4Address(ip));
            // send rest packet with corresponding ip
            if (ipMap.containsKey(IPv4.toIPv4Address(ip))) {
                Queue packetsQueue = ipMap.get(IPv4.toIPv4Address(ip));
                while (!packetsQueue.isEmpty()) {
                    Ethernet packet = (Ethernet) packetsQueue.poll();
                    packet.setDestinationMACAddress(mac);
                    this.sendPacket(packet, inIface);
                }
            }
        }
    }

    private static final byte[] ZERO = {0, 0, 0, 0, 0, 0};
    private static final byte[] BROADCAST = { (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff};

    private void generateARPRequest(Ethernet etherPacket, final int ip, Iface inIface, final Iface outIface) {
        Queue packetsQueue = ipMap.get(ip);
        packetsQueue.add(etherPacket);
        final Ethernet ether = generateARPPacket(BROADCAST, ARP.OP_REQUEST, ZERO, IPv4.toIPv4AddressBytes(ip), inIface);

        final AtomicReference<Ethernet> atomicEther = new AtomicReference<>(ether);
        final AtomicReference<Iface> atomicIface = new AtomicReference<>(outIface);
        final AtomicReference<Ethernet> atomicOriginalPacket = new AtomicReference<>(etherPacket);
        final AtomicReference<Queue> atomicQueue = new AtomicReference<>(packetsQueue);

        Thread waitForArpReply = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    int times = 3;
                    for (int i = 0; i < times; i++) {
                        System.out.println("----- Time " + i + atomicEther.get() + "-----");
                        sendPacket(atomicEther.get(), atomicIface.get());
                        Thread.sleep(1000);
                        if (arpCache.lookup(ip) != null) {
                            System.out.println("Have find the arp match in this turn !!");
                            return;
                        }
                    }
                    while (atomicQueue.get() != null && atomicQueue.get().peek() != null) {
                        atomicQueue.get().poll();
                    }
                    sendICMPPacket((IPv4) atomicOriginalPacket.get().getPayload(), atomicIface.get(), (byte) 3, (byte) 1);
                } catch (InterruptedException e) {
                    System.out.println(e);
                }
            }
        });

        waitForArpReply.start();
        return;
    }

    private Ethernet generateARPPacket(byte[] dstMAC, short opCode, byte[] targetHardwareAddr, byte[] targetProtocolAddr, Iface inIface) {
        // Ethernet header
        Ethernet ethernetHeader = new Ethernet();
        ethernetHeader.setEtherType(Ethernet.TYPE_ARP);
        ethernetHeader.setSourceMACAddress(inIface.getMacAddress().toBytes());
        ethernetHeader.setDestinationMACAddress(dstMAC);
        // ARP header
        ARP arpHeader = new ARP();
        arpHeader.setHardwareType(ARP.HW_TYPE_ETHERNET);
        arpHeader.setProtocolType(ARP.PROTO_TYPE_IP);
        arpHeader.setHardwareAddressLength((byte) Ethernet.DATALAYER_ADDRESS_LENGTH);
        arpHeader.setProtocolAddressLength((byte) 4);
        arpHeader.setOpCode(opCode);
        arpHeader.setSenderHardwareAddress(inIface.getMacAddress().toBytes());
        arpHeader.setSenderProtocolAddress(IPv4.toIPv4AddressBytes(inIface.getIpAddress()));
        arpHeader.setTargetHardwareAddress(targetHardwareAddr);
        arpHeader.setTargetProtocolAddress(targetProtocolAddr);
        // link
        ethernetHeader.setPayload(arpHeader);
        return ethernetHeader;
    }

    // RIP

    private static final int CLEAN_TABLE_PERIOD = 1000;
    private static final int UNSOLICITED_RESPONSE_PERIOD = 10 * 1000;
    private Timer cleanTimer, responseTimer;

    public void useRIP() {
        this.routeTable.useRIP();
        cleanTimer = new Timer();
        responseTimer = new Timer();
        cleanTimer.schedule(cleanTable, 0, CLEAN_TABLE_PERIOD);
        responseTimer.schedule(unsolicitedResponse, 0, UNSOLICITED_RESPONSE_PERIOD);
        initializeRIP();
    }

    private void initializeRIP() {
        System.out.println("===== Initialize the route table in RIP =====");
        for (Iface iface : this.interfaces.values()) {
            routeTable.insert_rip(iface.getIpAddress() & iface.getSubnetMask(), 0, iface.getSubnetMask(), 1, iface);
        }
        for (Iface iface : this.interfaces.values()) {
            sendPacket(generateRIPRequest(iface.getIpAddress(), iface.getMacAddress()), iface);
        }
        System.out.println("=============================================");
    }

    private TimerTask cleanTable = new TimerTask() {
        @Override
        public void run() {
            routeTable.cleanTable();
        }
    };

    private TimerTask unsolicitedResponse = new TimerTask() {
        @Override
        public void run() {
            for (Iface iface : interfaces.values()) {
                sendPacket(generateRIPResponse(iface.getIpAddress(), iface.getMacAddress()), iface);
            }
        }
    };

    private Ethernet generateRIPResponse(int srcIP, MACAddress srcMAC) {
        Ethernet etherHeader = new Ethernet();
        IPv4 ipHeader = new IPv4();
        UDP udpHeader = new UDP();
        RIPv2 ripHeader = new RIPv2();

        etherHeader.setEtherType(Ethernet.TYPE_IPv4);
        etherHeader.setSourceMACAddress(srcMAC.toBytes());
        etherHeader.setDestinationMACAddress("FF:FF:FF:FF:FF:FF");

        ipHeader.setSourceAddress(srcIP);
        ipHeader.setDestinationAddress("224.0.0.9");
        ipHeader.setProtocol(IPv4.PROTOCOL_UDP);

        udpHeader.setSourcePort(UDP.RIP_PORT);
        udpHeader.setDestinationPort(UDP.RIP_PORT);

        for (RouteEntry entry : routeTable.getEntries()) {
            RIPv2Entry ripEntry = new RIPv2Entry(entry.getDestinationAddress(), entry.getMaskAddress(), entry.getDistance());
            ripEntry.setNextHopAddress(entry.getDestinationAddress());
            ripHeader.addEntry(ripEntry);
        }
        ripHeader.setCommand(RIPv2.COMMAND_RESPONSE);

        etherHeader.setPayload(ipHeader);
        ipHeader.setPayload(udpHeader);
        udpHeader.setPayload(ripHeader);

        udpHeader.resetChecksum();
        ipHeader.resetChecksum();
        etherHeader.resetChecksum();

        return etherHeader;
    }

    private Ethernet generateRIPRequest(int srcIP, MACAddress srcMAC) {
        Ethernet etherHeader = new Ethernet();
        IPv4 ipHeader = new IPv4();
        UDP udpHeader = new UDP();
        RIPv2 ripHeader = new RIPv2();

        etherHeader.setEtherType(Ethernet.TYPE_IPv4);
        etherHeader.setSourceMACAddress(srcMAC.toBytes());
        etherHeader.setDestinationMACAddress("FF:FF:FF:FF:FF:FF");

        ipHeader.setSourceAddress(srcIP);
        ipHeader.setDestinationAddress("224.0.0.9");
        ipHeader.setProtocol(IPv4.PROTOCOL_UDP);

        udpHeader.setSourcePort(UDP.RIP_PORT);
        udpHeader.setDestinationPort(UDP.RIP_PORT);

        ripHeader.setCommand(RIPv2.COMMAND_REQUEST);

        etherHeader.setPayload(ipHeader);
        ipHeader.setPayload(udpHeader);
        udpHeader.setPayload(ripHeader);

        udpHeader.resetChecksum();
        ipHeader.resetChecksum();
        etherHeader.resetChecksum();

        return etherHeader;
    }

    private static final int RIP_BOUND = 32;

    private synchronized  void updateRIP(Ethernet etherPacket, Iface iface) {
        IPv4 ipPacket = (IPv4) etherPacket.getPayload();
        UDP udpPacket = (UDP) ipPacket.getPayload();
        RIPv2 ripPacket = (RIPv2) udpPacket.getPayload();
        for (RIPv2Entry ripEntry : ripPacket.getEntries()) {
            RouteEntry routeEntry = routeTable.lookup(ripEntry.getAddress());
            if (routeEntry == null && ripEntry.getMetric() + 1 <= RIP_BOUND) {
                //System.out.println("Update [" + IPv4.fromIPv4Address(ripEntry.getAddress()) + "][" + IPv4.fromIPv4Address(ripEntry.getSubnetMask()) + "] " + String.valueOf(ripEntry.getMetric() + 1));
                routeTable.insert_rip(ripEntry.getAddress() & ripEntry.getSubnetMask(), ipPacket.getSourceAddress(), ripEntry.getSubnetMask(), ripEntry.getMetric() + 1, iface);
            } else {
                if (routeEntry.getDistance() > ripEntry.getMetric() + 1) {
                    //System.out.println("Update [" + IPv4.fromIPv4Address(ripEntry.getAddress()) + "][" + IPv4.fromIPv4Address(ripEntry.getSubnetMask()) + "] " + String.valueOf(ripEntry.getMetric() + 1));
                    routeTable.update_rip(ripEntry.getAddress() & ripEntry.getSubnetMask(), ripEntry.getSubnetMask(), ipPacket.getSourceAddress(), Math.max(ripEntry.getMetric() + 1, RIP_BOUND), iface);
                }else{
                    //Update the time
                    routeTable.update_time(ripEntry.getAddress() & ripEntry.getSubnetMask(), ripEntry.getSubnetMask());
                }
            }
        }
    }

}