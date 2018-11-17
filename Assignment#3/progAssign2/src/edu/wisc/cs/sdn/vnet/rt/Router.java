package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.MACAddress;

/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device
{	
	/** Routing table for the router */
	private RouteTable routeTable;
	
	/** ARP cache for the router */
	private ArpCache arpCache;
	
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
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4) {
			return; //drop
		} else {
            IPv4 header = (IPv4) etherPacket.getPayload();
            int headerBytes = header.getHeaderLength() * 4;
            short originalChecksum = header.getChecksum();
            header.resetChecksum();
            byte[] tmp = header.serialize();
            header.deserialize(tmp, 0, tmp.length);
            if (originalChecksum != header.getChecksum()) {
                return; //drop
            }
            if (header.getTtl() > 0) {
                header.setTtl((byte) (header.getTtl() - 1));
            } else {
                return; //drop
            }
            header.resetChecksum();
            byte[] sendHeader = header.serialize();
            header.deserialize(sendHeader, 0, sendHeader.length);
            for (Iface iface : this.interfaces.values()) {
                if (iface.getIpAddress() == header.getDestinationAddress()) {
                    return; //drop
                }
            }
            //forwarding
            RouteEntry destinationRoute = this.routeTable.lookup(header.getDestinationAddress());
            if (destinationRoute == null) {
                return; //drop
            }
            Iface outIface = destinationRoute.getInterface();
            if (outIface == inIface) {
                return; //error
            }
            MACAddress sourceMAC = outIface.getMacAddress();
            int nextHopIP = destinationRoute.getGatewayAddress();
            if (nextHopIP == 0) { //next hop is the destination ip
                nextHopIP = header.getDestinationAddress();
            }
            ArpEntry arpEntry = this.arpCache.lookup(nextHopIP);
            if (arpEntry == null) {
                return; //cache miss
            }
            MACAddress nextHopMAC = arpEntry.getMac();
            etherPacket.setSourceMACAddress(sourceMAC.toBytes());
            etherPacket.setDestinationMACAddress(nextHopMAC.toBytes());
            System.out.println("The status of sending packet is " + sendPacket(etherPacket, outIface));
        }
		
		/********************************************************************/
	}
}
