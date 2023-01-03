import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.ForkJoinPool;

public abstract class httpfs extends httpAbstract {

	protected String directory;
	protected boolean v;

	public httpfs(String directory, boolean v, DatagramChannel channel) throws Exception{
		super(channel);
		this.directory = directory;
		this.v = v;
	}

	/**
	 * Serve http method
	 */
	private void serveHTTP(SocketChannel socket) {
		try (SocketChannel client = socket) {

			// read and store http request
			String requestLines[] = readSocket(client);

			// get and post method
			if (requestLines[0].substring(0, 3).equals("GET")) {
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
				sendSocket(client, sendStr, httpCode);
			} else if (requestLines[0].substring(0, 4).equals("POST")) {
				post(client, requestLines);
			}
			client.close();
		} catch (IOException e) {
			if (v)
				e.printStackTrace();
		}
	}
	
	private void post(SocketChannel client, String requestLines[]) throws IOException {
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

			sendSocket(client, "", httpCode);
		} else {
			if (v)
				System.out.println("User input an invalid file name");
			httpCode = "403 Forbidden";
			sendSocket(client, "", httpCode);
		}
	}

	/**
	 * Read from socket
	 */
	private String[] readSocket(SocketChannel client) throws IOException {
		String temp[] = {};
//		String returnStr = "";
		String rawStr = "";

		ByteBuffer buf = ByteBuffer.allocate(1024);
		for (;;) {
			int nr = client.read(buf);

			if (nr == -1)
				break;

			if (nr > 0) {
				buf.flip();
				rawStr = StandardCharsets.UTF_8.decode(buf).toString();
				if (v)
					System.out.println(rawStr + "\n");
				temp = rawStr.split("\r\n");
//				returnStr += temp[0];
				buf.clear();
				break;
			}
		}
		return temp;
	}

	/**
	 * Send to socket
	 */
	private void sendSocket(SocketChannel client, String msg, String code) throws IOException {
		String sendMsg = "";
		if (v) {
			System.out.println("Sent a message to " + client.getRemoteAddress());
			sendMsg = "HTTP/1.1 " + code + "\r\n" + "Content-Type: text/html\r\n" + "Content-Length: " + msg.length()
					+ "\r\n" + "\r\n" + msg;
			System.out.println(sendMsg);
		}
		ByteBuffer buf = ByteBuffer.allocate(1024);
		buf = StandardCharsets.UTF_8.encode(sendMsg);
		client.write(buf);
		buf.clear();
	}

	/**
	 * Listen for connection requests and serve http
	 */
	private void listenAndServe(int port) {
		try (ServerSocketChannel server = ServerSocketChannel.open()) {
			server.bind(new InetSocketAddress(port));
			if (v) {
				System.out.println("Server is listening at port " + port);
				System.out.println("Server will post in the directory " + directory);
			}
			for (;;) {
				SocketChannel client = server.accept();
				System.out.println();
				System.out.println("==================================");
				if (v)
					System.out.println("New client with address " + client.getRemoteAddress());
				// We may use a custom Executor instead of ForkJoinPool in a real-world
				// application
				ForkJoinPool.commonPool().submit(() -> serveHTTP(client));
			}
		} catch (IOException e) {
			if (v) {
				System.out.println("listenAndServer error");
				e.printStackTrace();
			}
		}
	}

	public boolean checkMethodDirectory(String fileName) {
		String temp = fileName.substring(1);
		if (temp.contains("..") || temp.contains("/"))
			return false;
		else
			return true;
	}

	public static boolean checkV(String arrCommand[]) {
		for (int i = 0; i < arrCommand.length; i++)
			if (arrCommand[i].equals("-v") || arrCommand[i].equals("--v"))
				return true;
		return false;
	}

	public static int checkPort(String arrCommand[]) throws Exception {
		for (int i = 0; i < arrCommand.length; i++) {
			if (arrCommand[i].equals("-p") || arrCommand[i].equals("--p"))
				return Integer.parseInt(arrCommand[i + 1]);
		}
		return 8080;
	}

	public static String checkDirectory(String arrCommand[]) {
		for (int i = 0; i < arrCommand.length; i++) {
			if (arrCommand[i].equals("-d") || arrCommand[i].equals("--d"))
				return arrCommand[i + 1];
		}
		return ".";
	}

	public static void main(String[] args) {
		boolean v = checkV(args);
		try {
		} catch (Exception e) {
			if (v) {
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
		}
	}
}