package edu.wisc.cs.sdn.apps.loadbalancer;

import java.util.*;

import edu.wisc.cs.sdn.apps.l3routing.L3Routing;
import edu.wisc.cs.sdn.apps.util.SwitchCommands;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.wisc.cs.sdn.apps.util.ArpServer;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch.PortChangeType;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.ImmutablePort;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.internal.DeviceManagerImpl;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.util.MACAddress;

import org.openflow.protocol.*;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.action.OFActionSetField;
import org.openflow.protocol.instruction.OFInstruction;
import org.openflow.protocol.instruction.OFInstructionApplyActions;
import org.openflow.protocol.instruction.OFInstructionGotoTable;

import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceService;

public class LoadBalancer implements IFloodlightModule, IOFSwitchListener,
		IOFMessageListener
{
	public static final String MODULE_NAME = LoadBalancer.class.getSimpleName();

	private static final byte TCP_FLAG_SYN = 0x02;

	private static final short IDLE_TIMEOUT = 20;

	// Interface to the logging system
	private static Logger log = LoggerFactory.getLogger(MODULE_NAME);

	// Interface to Floodlight core for interacting with connected switches
	private IFloodlightProviderService floodlightProv;

	// Interface to device manager service
	private IDeviceService deviceProv;

	// Switch table in which rules should be installed
	private byte table;

	// Set of virtual IPs and the load balancer instances they correspond with
	private Map<Integer,LoadBalancerInstance> instances;

	final static private boolean rewriteSource = true;

	final static private boolean rewriteDestination = false;



	private List<OFInstruction> instructionToController() {
		OFActionOutput action = new OFActionOutput(OFPort.OFPP_CONTROLLER);
		OFInstructionApplyActions instruction = new OFInstructionApplyActions(new ArrayList<OFAction>(Collections.singletonList(action)));
		return new ArrayList<OFInstruction>(Collections.singletonList(instruction));
	}

	private void installConnectionSepcificRules(IOFSwitch sw, IPv4 ip, TCP tcp, boolean type, LoadBalancerInstance instance, int nextHostIP) {
		int srcIP, dstIP;
		short srcPort, dstPort;
		byte[] newMAC;
		int newIP;
		OFOXMFieldType ipSetType, macSetType;

		if (type == rewriteSource) {
			// from server to client
			srcIP = nextHostIP;
			dstIP = ip.getSourceAddress();
			srcPort = tcp.getDestinationPort();
			dstPort = tcp.getSourcePort();

			newIP = instance.getVirtualIP();
			newMAC = instance.getVirtualMAC();

			ipSetType = OFOXMFieldType.IPV4_SRC;
			macSetType = OFOXMFieldType.ETH_SRC;
		} else {
			// from client to loadbalancer
			srcIP = ip.getSourceAddress();
			dstIP = ip.getDestinationAddress();
			srcPort = tcp.getSourcePort();
			dstPort = tcp.getDestinationPort();

			newIP = nextHostIP;
			newMAC = getHostMACAddress(nextHostIP);

			ipSetType = OFOXMFieldType.IPV4_DST;
			macSetType = OFOXMFieldType.ETH_DST;
		}

		OFMatch matchCriteria = new OFMatch();
		matchCriteria.setDataLayerType(OFMatch.ETH_TYPE_IPV4);
		matchCriteria.setNetworkProtocol(OFMatch.IP_PROTO_TCP);

		matchCriteria.setNetworkSource(OFMatch.ETH_TYPE_IPV4, srcIP);
		matchCriteria.setNetworkDestination(OFMatch.ETH_TYPE_IPV4, dstIP);

		matchCriteria.setTransportSource(srcPort);
		matchCriteria.setTransportDestination(dstPort);

		OFActionSetField ipAction = new OFActionSetField(ipSetType, newIP);
		OFActionSetField macAction = new OFActionSetField(macSetType, newMAC);

		OFInstructionApplyActions rewriteField = new OFInstructionApplyActions();
		rewriteField.setActions(new ArrayList<OFAction>(Arrays.asList(ipAction, macAction)));
		OFInstructionGotoTable instructionGoToTable = new OFInstructionGotoTable(L3Routing.table);

		SwitchCommands.installRule(sw, table, SwitchCommands.MAX_PRIORITY, matchCriteria, new ArrayList<OFInstruction>(Arrays.asList(rewriteField, instructionGoToTable)), (short) 0, (short) 20);

	}




	/**
	 * Loads dependencies and initializes data structures.
	 */
	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException
	{
		log.info(String.format("Initializing %s...", MODULE_NAME));

		// Obtain table number from config
		Map<String,String> config = context.getConfigParams(this);
		this.table = Byte.parseByte(config.get("table"));

		// Create instances from config
		this.instances = new HashMap<Integer,LoadBalancerInstance>();
		String[] instanceConfigs = config.get("instances").split(";");
		for (String instanceConfig : instanceConfigs)
		{
			String[] configItems = instanceConfig.split(" ");
			if (configItems.length != 3)
			{
				log.error("Ignoring bad instance config: " + instanceConfig);
				continue;
			}
			LoadBalancerInstance instance = new LoadBalancerInstance(
					configItems[0], configItems[1], configItems[2].split(","));
			this.instances.put(instance.getVirtualIP(), instance);
			log.info("Added load balancer instance: " + instance);
		}

		this.floodlightProv = context.getServiceImpl(
				IFloodlightProviderService.class);
		this.deviceProv = context.getServiceImpl(IDeviceService.class);

		/*********************************************************************/
		/* TODO: Initialize other class variables, if necessary              */

		/*********************************************************************/
	}

	/**
	 * Subscribes to events and performs other startup tasks.
	 */
	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException
	{
		log.info(String.format("Starting %s...", MODULE_NAME));
		this.floodlightProv.addOFSwitchListener(this);
		this.floodlightProv.addOFMessageListener(OFType.PACKET_IN, this);

		/*********************************************************************/
		/* TODO: Perform other tasks, if necessary                           */

		/*********************************************************************/
	}

	/**
	 * Event handler called when a switch joins the network.
	 * @param DPID for the switch
	 */
	@Override
	public void switchAdded(long switchId)
	{
		IOFSwitch sw = this.floodlightProv.getSwitch(switchId);
		log.info(String.format("Switch s%d added", switchId));

		/*********************************************************************/
		/* TODO: Install rules to send:                                      */
		/*       (1) packets from new connections to each virtual load       */
		/*       balancer IP to the controller                               */

		for (Integer virtualIP : instances.keySet()) {
			OFMatch matchCriteria = new OFMatch();
			matchCriteria.setDataLayerType(OFMatch.ETH_TYPE_IPV4);
			matchCriteria.setNetworkDestination(OFMatch.ETH_TYPE_IPV4, virtualIP);
			matchCriteria.setNetworkProtocol(OFMatch.IP_PROTO_TCP);

			SwitchCommands.installRule(sw, table, SwitchCommands.DEFAULT_PRIORITY, matchCriteria, instructionToController());
		}

		/*       (2) ARP packets to the controller, and                      */

		OFMatch matchCriteria = new OFMatch();
		matchCriteria.setDataLayerType(OFMatch.ETH_TYPE_ARP);

		SwitchCommands.installRule(sw, table, SwitchCommands.DEFAULT_PRIORITY, matchCriteria, instructionToController());

		/*       (3) all other packets to the next rule table in the switch  */

		OFMatch _matchCriteria = new OFMatch();
		_matchCriteria.setDataLayerType(OFMatch.ETH_TYPE_IPV4);
		OFInstruction instruction = new OFInstructionGotoTable(L3Routing.table);
		SwitchCommands.installRule(sw, table, (short) (SwitchCommands.DEFAULT_PRIORITY - 1), _matchCriteria, Arrays.asList(instruction));


		/*********************************************************************/
	}

	/**
	 * Handle incoming packets sent from switches.
	 * @param sw switch on which the packet was received
	 * @param msg message from the switch
	 * @param cntx the Floodlight context in which the message should be handled
	 * @return indication whether another module should also process the packet
	 */
	@Override
	public net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx)
	{
		// We're only interested in packet-in messages
		if (msg.getType() != OFType.PACKET_IN)
		{ return Command.CONTINUE; }
		OFPacketIn pktIn = (OFPacketIn)msg;

		// Handle the packet
		Ethernet ethPkt = new Ethernet();
		ethPkt.deserialize(pktIn.getPacketData(), 0,
				pktIn.getPacketData().length);

		/*********************************************************************/

		switch(ethPkt.getEtherType()) {

			/* TODO: Send an ARP reply for ARP requests for virtual IPs; for TCP */

			case Ethernet.TYPE_ARP :
				ARP arp = (ARP) ethPkt.getPayload();

				if (arp.getOpCode() != ARP.OP_REQUEST || arp.getProtocolType() != ARP.PROTO_TYPE_IP) {
					return Command.CONTINUE;
				}

				// See if we known about the device whose MAC address is being requested
				int targetIP = IPv4.toIPv4Address(arp.getTargetProtocolAddress());
				LoadBalancerInstance balancer = instances.get(targetIP);

				// Create ARP reply
				ARP arpReply = new ARP();
				Ethernet etherPacket = new Ethernet();
				byte[] virtualMAC = balancer.getVirtualMAC();

				etherPacket.setPayload(arpReply);
				etherPacket.setEtherType(Ethernet.TYPE_ARP);

				etherPacket.setSourceMACAddress(virtualMAC);
				etherPacket.setDestinationMACAddress(ethPkt.getSourceMACAddress());

				arpReply.setHardwareType(ARP.HW_TYPE_ETHERNET);
				arpReply.setProtocolType(ARP.PROTO_TYPE_IP);
				arpReply.setOpCode(ARP.OP_REPLY);
				arpReply.setHardwareAddressLength((byte) Ethernet.DATALAYER_ADDRESS_LENGTH);
				arpReply.setProtocolAddressLength((byte) 4);
				arpReply.setTargetHardwareAddress(arp.getSenderHardwareAddress());
				arpReply.setTargetProtocolAddress(arp.getSenderProtocolAddress());
				arpReply.setSenderHardwareAddress(virtualMAC);
				arpReply.setSenderProtocolAddress(IPv4.toIPv4AddressBytes(targetIP));

				SwitchCommands.sendPacket(sw, (short) pktIn.getInPort(), etherPacket);

				break;

			/*       SYNs sent to a virtual IP, select a host and install        */
			/*       connection-specific rules to rewrite IP and MAC addresses;  */

			case Ethernet.TYPE_IPv4 :
				
				
				IPv4 ip = (IPv4) ethPkt.getPayload();
				
				System.out.println("----------------------------------------" + ip.getProtocol() + "-------------" + IPv4.PROTOCOL_TCP );				

				if (ip.getProtocol() != IPv4.PROTOCOL_TCP) {
					return Command.CONTINUE;
				}


				System.out.println("=============================================");	

				TCP tcp = (TCP) ip.getPayload();
				if (tcp.getFlags() != TCP_FLAG_SYN) {
					return Command.CONTINUE;
				}

				int virtualIP = ip.getDestinationAddress();
				LoadBalancerInstance instance = instances.get(virtualIP);
				int nextHostIP = instance.getNextHostIP();
				

				installConnectionSepcificRules(sw, ip, tcp, rewriteDestination, instance, nextHostIP);

				installConnectionSepcificRules(sw, ip, tcp, rewriteSource, instance, nextHostIP);


				break;

			/*       ignore all other packets                                    */

		}
		/*********************************************************************/


		// We don't care about other packets
		return Command.CONTINUE;
	}

	/**
	 * Returns the MAC address for a host, given the host's IP address.
	 * @param hostIPAddress the host's IP address
	 * @return the hosts's MAC address, null if unknown
	 */
	private byte[] getHostMACAddress(int hostIPAddress)
	{
		Iterator<? extends IDevice> iterator = this.deviceProv.queryDevices(
				null, null, hostIPAddress, null, null);
		if (!iterator.hasNext())
		{ return null; }
		IDevice device = iterator.next();
		return MACAddress.valueOf(device.getMACAddress()).toBytes();
	}

	/**
	 * Event handler called when a switch leaves the network.
	 * @param DPID for the switch
	 */
	@Override
	public void switchRemoved(long switchId)
	{ /* Nothing we need to do, since the switch is no longer active */ }

	/**
	 * Event handler called when the controller becomes the master for a switch.
	 * @param DPID for the switch
	 */
	@Override
	public void switchActivated(long switchId)
	{ /* Nothing we need to do, since we're not switching controller roles */ }

	/**
	 * Event handler called when a port on a switch goes up or down, or is
	 * added or removed.
	 * @param DPID for the switch
	 * @param port the port on the switch whose status changed
	 * @param type the type of status change (up, down, add, remove)
	 */
	@Override
	public void switchPortChanged(long switchId, ImmutablePort port,
								  PortChangeType type)
	{ /* Nothing we need to do, since load balancer rules are port-agnostic */}

	/**
	 * Event handler called when some attribute of a switch changes.
	 * @param DPID for the switch
	 */
	@Override
	public void switchChanged(long switchId)
	{ /* Nothing we need to do */ }

	/**
	 * Tell the module system which services we provide.
	 */
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices()
	{ return null; }

	/**
	 * Tell the module system which services we implement.
	 */
	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService>
	getServiceImpls()
	{ return null; }

	/**
	 * Tell the module system which modules we depend on.
	 */
	@Override
	public Collection<Class<? extends IFloodlightService>>
	getModuleDependencies()
	{
		Collection<Class<? extends IFloodlightService >> floodlightService =
				new ArrayList<Class<? extends IFloodlightService>>();
		floodlightService.add(IFloodlightProviderService.class);
		floodlightService.add(IDeviceService.class);
		return floodlightService;
	}

	/**
	 * Gets a name for this module.
	 * @return name for this module
	 */
	@Override
	public String getName()
	{ return MODULE_NAME; }

	/**
	 * Check if events must be passed to another module before this module is
	 * notified of the event.
	 */
	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name)
	{
		return (OFType.PACKET_IN == type
				&& (name.equals(ArpServer.MODULE_NAME)
				|| name.equals(DeviceManagerImpl.MODULE_NAME)));
	}

	/**
	 * Check if events must be passed to another module after this module has
	 * been notified of the event.
	 */
	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name)
	{ return false; }
}
