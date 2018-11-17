package edu.wisc.cs.sdn.vnet.sw;

import net.floodlightcontroller.packet.Ethernet;
import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;
import net.floodlightcontroller.packet.MACAddress;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Aaron Gember-Jacobson
 */
public class Switch extends Device
{
    private class TableEntry {
        private Iface iface;
        private long time;

        public TableEntry(Iface _iface, long _time) {
            iface = _iface;
            time = _time;
        }

        public void setTime(long _time) {
            time = _time;
        }

        public long getTime() {
            return time;
        }

        public Iface getIface() {
            return iface;
        }
    }
    Map<MACAddress, TableEntry>  forwardingTable;
	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Switch(String host, DumpFile logfile)
	{
	    super(host,logfile);
	    forwardingTable = new HashMap<>();
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
        MACAddress sourceMAC = etherPacket.getSourceMAC();
        MACAddress destinationMAC = etherPacket.getDestinationMAC();
        if (!forwardingTable.containsKey(sourceMAC)) {
            forwardingTable.put(sourceMAC, new TableEntry(inIface, System.currentTimeMillis()));
        } else {
            (forwardingTable.get(sourceMAC)).setTime(System.currentTimeMillis());
        }
        if (forwardingTable.containsKey(destinationMAC)) {
            TableEntry destinationEntry = forwardingTable.get(destinationMAC);
            if (System.currentTimeMillis() - destinationEntry.getTime() > 15000) {
                for (Iface iface : interfaces.values()) {
                    if (!iface.equals(inIface)) {
                        sendPacket(etherPacket, iface);
                    }
                }
            } else {
                sendPacket(etherPacket, destinationEntry.getIface());
            }
        } else {
            for (Iface iface : interfaces.values()) {
                if (!iface.equals(inIface)) {
                    sendPacket(etherPacket, iface);
                }
            }
        }
		/********************************************************************/

	}
}
