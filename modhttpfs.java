import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class modhttpfs extends httpfs {

	public modhttpfs(String directory, boolean v, int windowSize, InetSocketAddress source, InetSocketAddress dest,
			InetSocketAddress router, DatagramChannel channel) throws Exception {
		super(directory, v, channel);
		this.windowSize = windowSize;
		this.sourceAddress = source;
		this.destinationAddress = dest;
		System.out.println("dest = " + destinationAddress);
		this.routerAddress = router;
		System.out.println("router =  " + routerAddress);
	}
	
	/**
	 * TCP accept simulation
	 * @param port
	 * @param router
	 * @return
	 * @throws Exception
	 */
	private boolean accept(int port, SocketAddress router) throws Exception {
		print('-');
		System.out.println("Waiting for SYN.");
		Packet req;
		while ((req = recvPacket(SYN)) == null);
		print('-');
		System.out.println("Received syn. Responding with synack.");

		print('-');
		Packet p = req.toBuilder().setType(SYNACK).setSequenceNumber(req.getSequenceNumber()).create();
		sendPacket(p, ACK, (int)req.getSequenceNumber() + 1);
		System.out.println("Received SYNACK ACK");
		print('-');
		System.out.println("Connection success");
		return true;
	}

	/**
	 * Listen for message from client
	 * @throws Exception
	 */
	public void listen() throws Exception {
		print('=');
		print('-');
		System.out.println("Listening for packets.");
		Packet p;
		
		// wait MSG or REL msg from client
		while (true) {
			Packet a = createPacket(2, ACK);
			p = recvPacket(a, ANY);
			System.out.println("Receieved " + protocolToString(p.getType()));
			if (p.getType() == MSG || p.getType() == REL)  {
				sq++;
				break;
			}
		}

		print('-');
		System.out.println("Received " + protocolToString(p.getType()));
		System.out.println("Processing");
		if (p.getType() == MSG) {
			this.sq = (int) p.getSequenceNumber();
			System.out.println("sq = " + sq);
			String msg = new String(p.getPayload(), StandardCharsets.UTF_8);
			Packet ack = createPacket(sq, ACK);
			print('-');
			sendPacket(ack, REC, sq + 1);
			System.out.println("Received REC. Sending MSG.");
			print('+');
			System.out.println("sq = " + sq);
			String request[] = extractHTTPRequest(msg);
			if (request[0].substring(0, 3).equals("GET")) {
				get(request);
			} else
				post(request);
			print('+');
		} else if (p.getType() == REL) {
			sq = (int) p.getSequenceNumber();
			relRecv();
			print('-');
			System.out.println("Sending RELFINACK");
			Packet a = createPacket(sq, RELFINACK);
			sendPacket(a, ACK, sq + 1);
			System.out.println("Recived RELFINACK ACK");
			print('-');
			System.out.println("Sending ACK");
			Packet ack = createPacket(sq, ACK);
			sendPacket(ack, REC, sq + 1);
			System.out.println("Received REC");
			print('+');
			String request[] = extractHTTPRequest(readBuffer());
			if (request[0].substring(0, 3).equals("GET")) {
				get(request);
			} else
				post(request);
			print('+');
		}

		print('=');
		print('=');
		System.out.println("Finish sending http reply");
		System.out.println("Waiting for fin");
		print('-');
		System.out.println("Sending ACK");
		Packet ack = createPacket(sq, ACK);
		sendPacket(ack, FIN, sq + 1);
		System.out.println("Received FIN");
		print('-');
		System.out.println("Sending FINACK");
		Packet finack = createPacket(sq, FINACK);
		sendPacketOnly(finack);
		ByteBuffer b;
		// Send finack and close connection after 10 secs if client does not respont
		do {
			if (!timeout(10000)) {
				System.out.println("Waited 10 Seconds. Ending Connection");
				break;
			}
			b = ByteBuffer.allocate(Packet.MAX_LEN);
			channel.receive(b);
			b.flip();
			p = Packet.fromBuffer(b);

			if (p.getType() == FIN) {
				sendPacketOnly(finack);
			} else {
				System.out.println("Received " + protocolToString(p.getType()));
				sendPacketOnly(finack);
			}
		} while (true);
	}

	/**
	 * Process get request
	 * @param requestLines
	 * @throws Exception
	 */
	private void get(String requestLines[]) throws Exception {
		String sendStr = "";
		String httpCode = "";
		File folder = new File(directory); // get folder
		File file[] = folder.listFiles(); // get files from foler

		String afterMethodString = requestLines[0].substring(4).trim(); // string after http method
		String inputString = afterMethodString.substring(0, afterMethodString.indexOf(" ")); // string of
																								// directory in
																								// http method
																								// line
		String filePath = directory + inputString; // java file path to the file

		if (v)
			System.out.println("GET method");

		// if file name is provided
		if (inputString.length() > 1) {

			if (v) {
				System.out.println("Reading from " + filePath);
			}

			String fn = inputString.substring(1); // file name

			boolean validFN = checkMethodDirectory(fn); // check if file name is valid

			// read file if valid
			if (validFN) {
				for (File f : file) {
					if (f.isFile() && f.getName().equals(fn)) {
						Scanner reader = new Scanner(f);
						while (reader.hasNextLine()) {
							String line = reader.nextLine();
							sendStr += line + "\n";
						}
						reader.close();
						httpCode = "200 OK";
						break;
					}
					httpCode = "400 Bad Request";
				}
			} else {
				sendStr = "";
				httpCode = "403 Forbidden";
			}
		}
		// if file name is not provided
		else {
			if (v)
				System.out.println("Listing files in " + directory);
			// list all file names
			for (File f : file)
				if (f.isFile()) {
					sendStr += f.getName() + "\n";
				}
			httpCode = "200 OK";
		}
		sendSocket(sendStr, httpCode);
	}

	/**
	 * Process post request
	 * @param requestLines
	 * @throws Exception
	 */
	private void post(String requestLines[]) throws Exception {
		String httpCode = "";
		String afterMethodString = requestLines[0].substring(5).trim();
		String filePath = afterMethodString.substring(0, afterMethodString.indexOf(" "));

		boolean validFN = checkMethodDirectory(filePath);
		if (validFN) {
			String fileName = directory + filePath;
			try {
				FileWriter writer = new FileWriter(fileName, true);
				String data = requestLines[requestLines.length - 1] + "\n	";
				if (v) {
					System.out.println("Writing the following string to " + filePath + ":");
					System.out.println(data + "\n");
				}
				writer.write(data);
				writer.close();
				httpCode = "200 OK";
			} catch (IOException e) {
				httpCode = "400 Bad Request";
			}
		} else {
			if (v)
				System.out.println("User input an invalid file name");
			httpCode = "403 Forbidden";
		}
		sendSocket(httpCode);
	}

	/**
	 * Send http reponse. Used by post and get.
	 * @param msg
	 * @param code
	 * @throws Exception
	 */
	private void sendSocket(String msg, String code) throws Exception {
		String sendMsg = "";
		if (v) {
			System.out.println("Sending message: ");
			sendMsg = "HTTP/1.1 " + code + "\r\n" + "Content-Type: text/html\r\n" + "Content-Length: " + msg.length()
					+ "\r\n" + "\r\n" + msg;
			System.out.println(sendMsg);
		}
		
		if (sendMsg.getBytes().length > 1013) {
			sendPacket(REL, RELACK);
			String partitions[] = divideMessage(msg);
			relSend(partitions);
			sendPacket(RELFIN, RELFINACK, sq);
		} else {
			sendPacket(createPacket(MSG, sendMsg), ACK, sq);
		}
	}

	/**
	 * Send http response. Used by post and get.
	 * @param code
	 * @throws Exception
	 */
	private void sendSocket(String code) throws Exception {
		sendSocket("", code);
	}

	/**
	 * Return lines of http request in an array
	 * @param request
	 * @return
	 */
	public String[] extractHTTPRequest(String request) {
		return request.split("\r\n");
	}

	public static void main(String args[]) {
		String dir = checkDirectory(args);
		boolean v = checkV(args);

		int windowSize = Integer.parseInt(check(args, "window"));

		String serverHost = check(args, "server-host");
		int serverPort = Integer.parseInt(check(args, "server-port"));
		String routerHost = check(args, "router-host");
		int routerPort = Integer.parseInt(check(args, "router-port"));
		String clientHost = check(args, "client-host");
		int clientPort = Integer.parseInt(check(args, "client-port"));

		InetSocketAddress clientAddress = new InetSocketAddress(clientHost, clientPort);
		InetSocketAddress serverAddress = new InetSocketAddress(serverHost, serverPort);
		InetSocketAddress routerAddress = new InetSocketAddress(routerHost, routerPort);
		DatagramChannel channel = null;
		try {
			channel = DatagramChannel.open();
			channel.bind(new InetSocketAddress(serverPort));
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}

		modhttpfs server = null;
		try {
			server = new modhttpfs(dir, v, windowSize, serverAddress, clientAddress, routerAddress, channel);
			server.start();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void start() throws Exception {
		System.out.println("modhttpc running");
		try {
			if (accept(4000, routerAddress)) {
				System.out.println("Accepting messages");
				System.out.println("registered + " + channel.isRegistered());
				listen();
			} else {
				System.out.println("Connection failed");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
