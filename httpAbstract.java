import static java.nio.channels.SelectionKey.OP_READ;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeMap;

public abstract class httpAbstract {

	public final int MSG = 0; // contains http msg
	public final int SYN = 1; // handshake
	public final int SYNACK = 2; // handshake
	public final int ACK = 3; // for ack synack or msgs
	public final int ANY = 5;
	public final int FIN = 6; // for termination
	public final int FINACK = 7; // for termination
	public final int REL = 8; // for reliable send/recv
	public final int RELACK = 9; // for reliable send/recv
	public final int RELFIN = 10; // for reliable send/recv
	public final int RELFINACK = 11; // for reliable send/recv
	public final int REC = 12; // for notify receiving

	public final int TIMEOUT_DUR = 200; // timeout for retransmission

	public DatagramChannel channel;

	public InetSocketAddress sourceAddress;
	public InetSocketAddress routerAddress;
	public InetSocketAddress destinationAddress;

	public int windowSize; // windowsize
	public int sq = 1;

	public TreeMap<Integer, Packet> buffer = new TreeMap<>(); // msg buffer
	public TreeMap<Integer, Packet> acked = new TreeMap<>(); // contains ack msgs

	Selector selector;

	public httpAbstract(DatagramChannel channel) throws Exception {
		this.channel = channel;
		channel.configureBlocking(false);
		selector = Selector.open();
		channel.register(selector, OP_READ);
	}

	/**
	 * Returns the string from buffer
	 * 
	 * @return
	 */
	public String readBuffer() {
		System.out.println("buffer size = " + buffer.size());
		for (Packet p : buffer.values()) {
			System.out.println(new String(p.getPayload(), StandardCharsets.UTF_8));
		}
		String temp = "";
		while (!buffer.isEmpty()) {
			Packet p = buffer.remove(buffer.firstKey());
			temp += new String(p.getPayload(), StandardCharsets.UTF_8);
		}
		return temp;
	}

	/**
	 * Asynchronous send
	 * 
	 * @param p
	 * @throws IOException
	 */
	public void sendPacketOnly(Packet p) throws IOException {
		System.out.println("------------------------");
		System.out.println(
				"Sending packet with sq " + p.getSequenceNumber() + " and type " + protocolToString(p.getType()));
		channel.send(p.toBuffer(), routerAddress);
		System.out.println("------------------------");
	}

	/**
	 * Synchronous send
	 * 
	 * @param p
	 * @param recvType exptected type
	 * @param recvSq   expected sequence number
	 * @throws IOException
	 */
	public void sendPacket(Packet p, int recvType, int recvSq) throws IOException {
		System.out.println("--------=Sending-" + protocolToString(p.getType()) + "=------");
		int replyType;
		Packet resp;
		while (true) {
			channel.send(p.toBuffer(), routerAddress);

			if (!timeout())
				continue;

			ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
			channel.receive(buf);
			buf.flip();

			resp = Packet.fromBuffer(buf);
			replyType = resp.getType();
			if (resp.getType() != recvType) {
				System.out.println(
						"Expected: " + protocolToString(recvType) + "; Received: " + protocolToString(resp.getType()));
				System.out.println("p.sq: " + p.getSequenceNumber() + "; recv.sq " + resp.getSequenceNumber());
				continue;
			}
			if (resp.getSequenceNumber() != recvSq) {
				System.out.println("Expected sq: " + recvSq + "; Received: " + resp.getSequenceNumber());
				continue;
			}
			System.out.println("Received: ");
			System.out.println(new String(resp.getPayload(), StandardCharsets.UTF_8));
			break;
		}
		System.out.println("----=Received-" + protocolToString(replyType) + "=----");
		sq++;
	}

	/**
	 * Synchronous send
	 * 
	 * @param p
	 * @param recvType expected type
	 * @throws Exception
	 */
	public void sendPacket(Packet p, int recvType) throws Exception {
		sendPacket(p, recvType, (int) p.getSequenceNumber());
	}

	/**
	 * Synchronous send
	 * 
	 * @param sendType
	 * @param recvType expected type
	 * @throws Exception
	 */
	public void sendPacket(int sendType, int recvType) throws Exception {
		sendPacket(createPacket(sendType), recvType);
	}

	/**
	 * Synchronous send
	 * 
	 * @param sendType
	 * @param recvType         expected type
	 * @param expectedSequence expected sequence
	 * @throws Exception
	 */
	public void sendPacket(int sendType, int recvType, int expectedSequence) throws Exception {
		sendPacket(createPacket(sendType), recvType, expectedSequence);
	}

	/**
	 * Reliable send using selective repeat
	 * 
	 * @param msg
	 * @throws IOException
	 */
	public void relSend(String[] msg) throws IOException {
		System.out.println("========================");
		System.out.println("Sending message");
		for (int i = 0; i < msg.length; i++) {
			System.out.println((i + 1) + ": " + msg[i]);
		}
		System.out.println("------------------------");
		int SQMax = msg.length + sq;
		int SQMin = sq;

		Sender senders[] = new Sender[msg.length];
		int numSends = (int) Math.ceil(msg.length / (double) windowSize);
		for (int i = 0; i < numSends; i++) {
			// send msgs based on the window size
			for (int j = i * windowSize; j < (i * windowSize) + windowSize; j++) {
				if (j == msg.length)
					break;
				Packet p = new Packet.Builder().setType(MSG).setSequenceNumber(sq)
						.setPortNumber(destinationAddress.getPort()).setPeerAddress(destinationAddress.getAddress())
						.setPayload(msg[j].getBytes()).create();
				channel.send(p.toBuffer(), routerAddress);
				senders[i] = new Sender(sq, p, this);
				senders[i].start();
				sq++;
			}

			// ack msgs until the last packet send is acked or the buffer is full
			while (true) {
				if (!timeout())
					continue;
				Packet resp = recvPacket(ANY);
				// break if every msgs is acked
				if (acked.size() == msg.length) {
					System.out.println("Reliable send finished");
					System.out.println(SQMax);
					System.out.println(SQMin);
					System.out.println(acked.size() + " == " + msg.length);
					for (Packet p : acked.values())
						System.out.println(p.getSequenceNumber());
					break;
				}
				if (resp.getType() != ACK) {
					System.out.println(
							"Expected: " + protocolToString(ACK) + "; Received: " + protocolToString(resp.getType()));
					continue;
				}
				// break and ack the msg if last message send is received
				if (resp.getSequenceNumber() == sq - 1 && i != numSends - 1) {
					acked.put((int) resp.getSequenceNumber(), resp);
					System.out.println("Sequence " + resp.getSequenceNumber() + " acked");
					System.out.println("Sending next set of packets");
					break;
				}
				// ack msgs if not acked
				if (!acked.containsKey((int) resp.getSequenceNumber()) && (int) resp.getSequenceNumber() < SQMax
						&& (int) resp.getSequenceNumber() >= SQMin) {
					acked.put((int) resp.getSequenceNumber(), resp);
					System.out.println("Sequence " + resp.getSequenceNumber() + " acked");
					continue;
				} else {
					System.out.println("Sequence " + resp.getSequenceNumber() + " already acked");
					continue;
				}
			}
		}
		System.out.println("Message acked");
		System.out.println("========================");
	}

	/**
	 * Synchronous send
	 * 
	 * @param send
	 * @param recvType         expected type
	 * @param expectedSequence
	 * @return the received packet
	 * @throws Exception
	 */
	public Packet recvPacket(Packet send, int recvType, int expectedSequence) throws Exception {
		System.out.println("----=Receiving-" + protocolToString(recvType) + "=----");
		int replyType;
		Packet resp = null;
		while (true) {
			channel.send(send.toBuffer(), routerAddress);

			if (!timeout())
				continue;

			ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
			channel.receive(buf);
			buf.flip();

			resp = Packet.fromBuffer(buf);
			replyType = resp.getType();
			if (recvType == ANY)
				return resp;
			if (resp.getType() != recvType) {
				System.out.println(
						"Expected: " + protocolToString(recvType) + "; Received: " + protocolToString(resp.getType()));
				continue;
			}
			if ((int) resp.getSequenceNumber() != expectedSequence) {
				System.out.println("Expected sq: " + expectedSequence + "; Received: " + resp.getSequenceNumber());
				continue;
			}
			System.out.println("Received: ");
			System.out.println(new String(resp.getPayload(), StandardCharsets.UTF_8));
			break;
		}
		System.out.println("----=Received-" + protocolToString(replyType) + "=----");
		sq++;

		return resp;
	}

	/**
	 * Synchronous send
	 * 
	 * @param send
	 * @param recvType expected type
	 * @return the received packet
	 * @throws Exception
	 */
	public Packet recvPacket(Packet send, int recvType) throws Exception {
		return recvPacket(send, recvType, (int) send.getSequenceNumber());
	}

	/**
	 * Synchronous send
	 * 
	 * @param sendType
	 * @param recvType expected type
	 * @return the received packet
	 * @throws Exception
	 */
	public Packet recvPacket(int sendType, int recvType) throws Exception {
		return recvPacket(createPacket(sendType), recvType);
	}

	/**
	 * Synchronous send
	 * 
	 * @param sendType
	 * @param recvType         expected type
	 * @param expectedSequence
	 * @return the received packet
	 * @throws Exception
	 */
	public Packet recvPacket(int sendType, int recvType, int expectedSequence) throws Exception {
		return recvPacket(createPacket(sendType), recvType, expectedSequence);
	}

	/**
	 * Reliable receive using selective repeat
	 * 
	 * @throws IOException
	 */
	public void relRecv() throws IOException {
		print('=');
		System.out.println("Reliable receiving");
		Packet relack = createPacket(sq, RELACK);
		sendPacketOnly(relack);
		Packet req = null;
		// resend relack until a message is received
		while (true) {
			sendPacketOnly(relack);
			if (!timeout())
				continue;
			ByteBuffer b = ByteBuffer.allocate(Packet.MAX_LEN);
			channel.receive(b);
			b.flip();
			req = Packet.fromBuffer(b);

			// resend relack if REL is received
			if (req.getType() == REL) {
				System.out.println("Received REL. Resending RELACK.");
				sendPacketOnly(relack);
				continue;
			}

			if (req.getType() == MSG && req.getSequenceNumber() == sq + 1) {
				System.out.println("Receieved ACK. Collecting packets.");
				sq++;
				break;
			}
		}

		buffer.put((int) req.getSequenceNumber(), req);

		boolean receivedRELFIN = false;
		int firstItemSQ = (int) req.getSequenceNumber(); // sq of first msg
		int expectedSize = -2; // expected number of msgs

		Packet relfin = null; // RELFIN packet

		// ack msgs until relfin is receieved and buffer is full
		while (!receivedRELFIN || expectedSize != buffer.size()) {
			System.out.println("sq = " + sq);

			// ack msgs received here
			Packet ack = createPacket(req.getSequenceNumber(), ACK);
			sendPacketOnly(ack);
			if (!timeout())
				continue;

			ByteBuffer b = ByteBuffer.allocate(Packet.MAX_LEN);
			channel.receive(b);
			b.flip();
			req = Packet.fromBuffer(b);

			System.out.println(
					"Received sequence " + req.getSequenceNumber() + " with type " + protocolToString(req.getType()));
			
			// place every msg received in buffer
			if (req.getType() == MSG && req.getSequenceNumber() > sq
					&& !buffer.containsKey((int) req.getSequenceNumber())) {
				System.out.println("Placed to buffer sequence " + req.getSequenceNumber());
				buffer.put((int) req.getSequenceNumber(), req);
			}
			if (req.getType() == RELFIN) {
				relfin = req;
				receivedRELFIN = true;
				expectedSize = (int) req.getSequenceNumber() - firstItemSQ;
				System.out.println("exptectedSize = " + expectedSize);
			}
		}
		
		// set sequence to the received RELFIN 
		sq = (int) relfin.getSequenceNumber();
		System.out.println("Received " + protocolToString(relfin.getType()) + " and buffer is "
				+ (buffer.size() == expectedSize ? "full" : "not full"));
		print('=');
	}

	/**
	 * Synchronous send
	 * @param recvType expected type
	 * @return expected type or null if none was received when timeout is reached
	 * @throws IOException
	 */
	public Packet recvPacket(int recvType) throws IOException {
		if (!timeout())
			return null;
		ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
		SocketAddress router = channel.receive(buf);
		buf.flip();
		Packet resp = Packet.fromBuffer(buf);
		System.out.println("Packet: " + resp);
		System.out.println("Router: " + router);

		if (recvType == ANY)
			return resp;

		if (resp.getType() != recvType) {
			System.out.println("Received " + resp.getType() + ": Expected " + recvType);
			return null;
		}
		return resp;
	}

	/**
	 * Timeout used for retransmission
	 * @param dur duration of timeout
	 * @return True if packet is in channel before timeout. Else it returns false.
	 * @throws IOException
	 */
	public boolean timeout(int dur) throws IOException {
		synchronized (this) {
			boolean temp;
			System.out.println("\tTimeout: Waiting for response for " + dur + "ms.");
			selector.select(dur);

			Set<SelectionKey> keys = selector.selectedKeys();
			if (keys.isEmpty()) {
				System.out.println("\tTimeout: Channel empty after timeout");
				temp = false;
			} else {
				System.out.println("\tTimeout: Channel contains packet before timeout");
				temp = true;
			}
			keys.clear();
			System.out.println("\tExit timeout");
			return temp;
		}
	}

	/**
	 * Timeout used for retransmission using the default timeout duration
	 * @return True if packet is in channel before timeout. Else it returns false.
	 * @throws IOException
	 */
	public boolean timeout() throws IOException {
		return timeout(TIMEOUT_DUR);
	}

	/**
	 * Divide strings into 1013 byte strings
	 * @param rMsg
	 * @return the array that contains the divided strings
	 */
	public String[] divideMessage(String rMsg) {
		System.out.println("Size of rMsg = " + rMsg.length());
		int numIterations = (int) Math.ceil(rMsg.length() / 1013.0);
		String temp[] = new String[numIterations];
		for (int i = 0; i < numIterations; i++) {
			if (i != numIterations - 1)
				temp[i] = rMsg.substring(i * 1013, (i + 1) * 1013);
			else
				temp[i] = rMsg.substring(i * 1013);
		}
		int sizeTemp = 0;
		for (int i = 0; i < temp.length; i++) {
			sizeTemp += temp[i].length();
		}
		System.out.println("Size of rMsg in array = " + sizeTemp);
		return temp;
	}

	public Packet createPacket(int type) {
		return createPacket(this.sq, type);
	}

	public Packet createPacket(long sq, int type) {
		return createPacket(sq, type, "");
	}

	public Packet createPacket(int type, String msg) {
		return createPacket(this.sq, type, msg);
	}

	public Packet createPacket(long sq, int type, String msg) {
		return new Packet.Builder().setType(type).setSequenceNumber(sq).setPortNumber(destinationAddress.getPort())
				.setPeerAddress(destinationAddress.getAddress()).setPayload(msg.getBytes()).create();
	}

	public static String check(String arrCommand[], String command) {
		for (int i = 0; i < arrCommand.length; i++)
			if (arrCommand[i].equals("-" + command) || arrCommand[i].equals("--" + command))
				return arrCommand[i + 1];

		if (command.equals("router-host"))
			return "localhost";
		else if (command.equals("router-port"))
			return "3000";
		else if (command.equals("server-host"))
			return "localhost";
		else if (command.equals("server-port"))
			return "4000";
		else if (command.equals("client-host"))
			return "localhost";
		else if (command.equals("client-port"))
			return "5000";
		else if (command.equals("window"))
			return "2";
		return "";
	}

	public String protocolToString(int protocol) {
		switch (protocol) {
		case MSG:
			return "MSG";
		case SYN:
			return "SYN";
		case SYNACK:
			return "SYNACK";
		case ACK:
			return "ACK";
		case ANY:
			return "ANY";
		case FIN:
			return "FIN";
		case FINACK:
			return "FINACK";
		case REL:
			return "REL";
		case RELACK:
			return "RELACK";
		case RELFIN:
			return "RELFIN";
		case RELFINACK:
			return "RELFINACK";
		case REC:
			return "REC";
		default:
			return "ERROR";
		}
	}

	public void print(char sym) {
		String temp = "-------------------------------------------";
		for (int i = 0; i < temp.length(); i++) {
			System.out.print(sym);
		}
		System.out.println();
	}

	public abstract void start() throws IOException, Exception;
}
