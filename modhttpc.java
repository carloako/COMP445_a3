
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URL;
import java.nio.channels.DatagramChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedList;

public class modhttpc extends httpc {
	
	public Request r; // contains http request variables

	public modhttpc(Request r, int windowSize, InetSocketAddress source, InetSocketAddress dest,
			InetSocketAddress router, DatagramChannel channel) throws Exception {
		super(channel);
		this.r = r;
		this.sourceAddress = source;
		this.destinationAddress = dest;
		this.routerAddress = router;
		this.windowSize = windowSize;
	}

	/**
	 * send http request
	 * @param rMsg http request
	 * @throws Exception
	 */
	public void sendHTTPRequest(String rMsg) throws Exception {

		Packet resp = null; // reply packet from server
		String reply = null; // reply message from server

		int rmsgSize = rMsg.getBytes().length;
		// if payload is bigger than max
		if (rmsgSize > 1013) {
			System.out.println("Message size greater than 1KB");
			sendPacket(REL, RELACK);
			String partitions[] = divideMessage(rMsg);
			relSend(partitions);
			sendPacket(RELFIN, RELFINACK);
			sendPacket(ACK, ACK);
		} 
		// if payload is less than max
		else 
			sendPacket(createPacket(MSG, rMsg), ACK);
		
		//	wait reply from server
		while (true) {
			resp = recvPacket(REC, ANY);
			System.out.println("Receieved " + protocolToString(resp.getType()));
			if (resp.getType() == MSG || resp.getType() == REL) {
				sq++;
				break;
			}
		}

		// set sq for synchronization
		sq = (int) resp.getSequenceNumber();
		if (resp.getType() == MSG) {
			sendPacket(ACK, ACK, sq + 1);
			sendPacket(ACK, ACK, sq);
			reply = new String(resp.getPayload(), StandardCharsets.UTF_8);
		} else if (resp.getType() == REL) {
			relRecv();
			sendPacket(RELFINACK, ACK, sq + 1);
			reply = readBuffer();
			sq++;
		}

		// finalize
		sendPacket(FIN, FINACK);
		System.out.println("Connection closed");
		System.out.println("Server reply:");

		System.out.println(reply);

	}

	/**
	 * TCP handshake simulation
	 * @param routerAddr
	 * @param serverAddr
	 * @return
	 * @throws Exception
	 */
	public boolean handshake(SocketAddress routerAddr, InetSocketAddress serverAddr) throws Exception {
		print('-');
		System.out.println("Sending SYN");
		sendPacket(SYN, SYNACK);
		System.out.println("Received SYNACK");
		print('-');
		System.out.println("Sending SYNACK ACK");
		sendPacket(ACK, ACK);
		System.out.println("Received ACK");
		print('-');
		System.out.println("3-way handshake success");
		return true;
	}

	public static void main(String[] args) throws IOException {
		String arrCommand[] = args;

		// check commands
		if (!checkCommand(arrCommand))
			System.exit(0);

		// check help command
		boolean containsHelp = checkHelp(arrCommand);

		// check v command
		boolean containsV = checkV(arrCommand);

		// check method commands
		String method = checkMethod(arrCommand);

		// check headers
		String header = "";
		LinkedList<String> hArgs = checkH(arrCommand);
		if (!hArgs.isEmpty())
			for (String h : hArgs)
				header += h + "\n";

		// check data
		String data = "";
		LinkedList<String> dArgs = checkD(arrCommand);
		if (!dArgs.isEmpty()) {
			for (String d : dArgs)
				data += d;
			String headers[] = header.split("\n");
			for (int i = 0; i < headers.length; i++) {
				if (headers[i].length() > 10 && headers[i].substring(0, 11).equals("Content-Type"))
					break;
			}
			header += "Content-Length:" + data.length() + "\n";
		}

		// check for files
		File file = null;
		LinkedList<String> fArgs = checkF(arrCommand);
		if (!fArgs.isEmpty()) {
			for (String f : fArgs)
				file = new File(f);
			data += new String(Files.readAllBytes(file.toPath()));
			header += "Content-Length:" + data.length() + "\n";
		}

		// check for url
		URL url = null;
		if (!containsHelp) {
			url = checkURL(arrCommand);
		}

		Request req = new Request(containsHelp, containsV, method, header, data, url);

		String sourceHost = check(arrCommand, "client-host");
		int sourcePort = Integer.parseInt(check(arrCommand, "client-port"));

		// Router address
		String routerHost = check(arrCommand, "router-host");
		int routerPort = Integer.parseInt(check(arrCommand, "router-port"));

		// Server address
		String serverHost = check(arrCommand, "server-host");
		int serverPort = Integer.parseInt(check(arrCommand, "server-port"));

		DatagramChannel channel = DatagramChannel.open();
		channel.bind(new InetSocketAddress(sourcePort));

		int windowSize = Integer.parseInt(check(arrCommand, "window"));

		InetSocketAddress sourceAddress = new InetSocketAddress(sourceHost, sourcePort);
		InetSocketAddress routerAddress = new InetSocketAddress(routerHost, routerPort);
		InetSocketAddress destinationAddress = new InetSocketAddress(serverHost, serverPort);

		modhttpc client = null;

		try {
			client = new modhttpc(req, windowSize, sourceAddress, destinationAddress, routerAddress, channel);
			client.start();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	@Override
	public void start() throws Exception {
		boolean connected = handshake(routerAddress, destinationAddress);

		if (!connected) {
			System.out.println("Not connected");
			System.exit(0);
		}

		System.out.println("Sending a msg");

		System.out.println("---------------------------");

		System.out.println("registered: " + channel.isRegistered());
		if (!r.containsHelp) {
			String rMsg = null;
			if (r.method.equals("get")) {
				String pathName = r.url.getPath();
				rMsg = getRMsg(pathName, r.url.getHost(), r.header);
				System.out.println("Request Message");
				System.out.println(rMsg);
				System.out.println("---------------------------");
				System.out.println("Response Message");
			} else if (r.method.equals("post")) {
				String pathName = r.url.getPath();
				rMsg = postRMsg(pathName, r.url.getHost(), r.header, r.data);
				System.out.println("Request Message");
				System.out.println(rMsg);
				System.out.println("---------------------------");
				System.out.println("Response Message");
			}
			sendHTTPRequest(rMsg);
		} else {
			if (r.method.equals("get")) {
				System.out.println("usage: httpc get [-v] [-h key:value] URL");
				System.out.println("Get executes a HTTP GET request for a given URL");
				System.out.println(" -v Prints the detail of the response such as protocol, status, and headers.");
				System.out.println(" -h key:value Associates headers to HTTP Request with the format 'key:value'.");
			} else if (r.method.equals("post")) {
				System.out.println("usage: httpc post [-v] [-h key:value] [-d inine-data] [-f file] URL");
				System.out.println("Post executes a HTTP POST request for a given URL with inline data or from file.");
				System.out.println(" -v Prints the detail of the response such as protocol, status, and headers.");
				System.out.println(" -h key:value Associates headers to HTTP Request with the format 'key:value'.");
				System.out.println(" -d string associates an inline data to the body HTTP POST request.");
				System.out.println(" -f file Associates the content of a file to the body HTTP POST request.");
				System.out.println("Either [-d] or [-f] can be use but not both.");
			} else {
				System.out.println("httpc is a curl-like application but supports HTTP protocol only.");
				System.out.println("Usage:");
				System.out.println(" httpc command [arguments]");
				System.out.println("The commands are:");
				System.out.println(" get executes a HTTP GET request and prints the response.");
				System.out.println(" post executes a HTTP POST request and prints the response.");
				System.out.println(" help prints this screen.");
				System.out.println("Use \"httpc help [command]\" for more information about a command.");
			}
		}
	}
}
