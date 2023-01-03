
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.nio.channels.DatagramChannel;
import java.util.LinkedList;
import java.util.Scanner;

public abstract class httpc extends httpAbstract {
	
	public httpc(DatagramChannel channel) throws Exception {
		super(channel);
	}
	
	/**
	 * http method get
	 * 
	 * @param host
	 * @param port
	 * @param rMsg
	 *            contains method and headers
	 * @param v
	 *            if verbose is needed
	 * @throws IOException
	 */
	public static void get(String host, int port, String rMsg, boolean v)
			throws IOException {

		Socket socket = new Socket(host, port);

		// writer to socket
		OutputStream output = socket.getOutputStream();
		PrintWriter writer = new PrintWriter(output, true);

		// send every content of headers from rMsg
		String aLine[] = rMsg.split("\n");
		for (int i = 0; i < aLine.length; i++)
			writer.println(aLine[i]);
		writer.println();

		// reader from socket
		InputStream input = socket.getInputStream();
		BufferedReader reader = new BufferedReader(
				new InputStreamReader(input));

		String outputLines; // input from socket
		String details = ""; // contains the details(header and response code)
								// of the response message
		String content = ""; // contains the data of the response message

		// read response message
		boolean checkDetails = true;
		while ((outputLines = reader.readLine()) != null) {
			if (outputLines.isBlank()) {
				checkDetails = false;
			} else if (checkDetails) {
				details += outputLines + "\n";
			} else {
				content += outputLines + "\n";
			}
		}

		// print verbose or not verbose
		if (v)
			System.out.println(details + "\n" + content);
		else
			System.out.println(content);

		output.close();
		writer.close();
		input.close();
		reader.close();
		socket.close();
	}

	public static void post(String host, int port, String rMsg, boolean v)
			throws IOException {
		Socket socket = new Socket(host, port);

		// writer to socket
		OutputStream output = socket.getOutputStream();
		PrintWriter writer = new PrintWriter(output, true);

		// send every content of rMsg to socket
		String aLine[] = rMsg.split("\n");
		for (int i = 0; i < aLine.length; i++)
			writer.println(aLine[i]);
		writer.println();

		// reader from socket
		InputStream input = socket.getInputStream();
		BufferedReader reader = new BufferedReader(
				new InputStreamReader(input));

		String outputLines; // input from socket
		String details = ""; // contains the headers and response code
		String content = ""; // contains the data of the response message

		// read response message
		boolean checkDetails = true;
		while ((outputLines = reader.readLine()) != null) {
			if (outputLines.isBlank()) {
				checkDetails = false;
			} else if (checkDetails) {
				details += outputLines + "\n";
			} else {
				content += outputLines + "\n";
			}
		}

		// print verbose or not verbose
		if (v)
			System.out.println(details + "\n" + content);
		else
			System.out.println(content);

		output.close();
		writer.close();
		input.close();
		reader.close();
		socket.close();
	}

	/**
	 * creates a post request message
	 * 
	 * @param pathName
	 * @param hostName
	 * @param headers
	 * @param data
	 * @return a post request message
	 */
	public static String postRMsg(String pathName, String hostName,
			String headers, String data) {
		String line = "POST " + pathName + " HTTP/1.0\r\n" + "Host: " + hostName
				+ "\r\n" + headers + "\r\n" + data + "\r\n";
		return line;
	}

	public static boolean checkCommand(String arrCommand[]) {
		boolean containsHelp = false;
		boolean containsGet = false;
		boolean containsPost = false;
		boolean containsD = false;
		boolean containsF = false;

		for (int i = 0; i < arrCommand.length; i++) {
			String com = arrCommand[i];
			if (com.equals("help"))
				containsHelp = true;
			if (com.equals("get"))
				containsGet = true;
			if (com.equals("post"))
				containsPost = true;
			if (com.equals("-d") || com.equals("--d"))
				containsD = true;
			if (com.equals("-f") || com.equals("--f"))
				containsF = true;
		}

		if (!(containsGet || containsPost)) {
			if (!containsHelp) {
				System.out.println("No methods. Exiting.");
				return false;
			}
		}
		if (containsGet && containsPost) {
			System.out.println("Method error. Exiting");
			return false;
		}
		if (containsGet && (containsD || containsF)) {
			System.out
					.println("Get method cannot use -d or -f command. Exiting");
			return false;
		}
		return true;
	}

	public static boolean checkHelp(String arrCommand[]) {
		boolean containsHelp = false;
		for (int i = 0; i < arrCommand.length; i++)
			if (arrCommand[i].equals("help")) {
				containsHelp = true;
				break;
			}
		return containsHelp;
	}

	public static String checkMethod(String arrCommand[]) {
		String method = "";
		for (int i = 0; i < arrCommand.length; i++)
			if (arrCommand[i].equals("get") || arrCommand[i].equals("post")) {
				method = arrCommand[i];
				break;
			}
		return method;
	}

	public static boolean checkV(String arrCommand[]) {
		for (int i = 0; i < arrCommand.length; i++)
			if (arrCommand[i].equals("-v") || arrCommand[i].equals("--v"))
				return true;
		return false;
	}

	public static LinkedList<String> checkH(String arrCommand[]) {
		LinkedList<String> temp = new LinkedList<>();
		for (int i = 0; i < arrCommand.length; i++)
			if (arrCommand[i].equals("-h") || arrCommand[i].equals("--h"))
				temp.add(arrCommand[i + 1]);
		return temp;
	}

	public static LinkedList<String> checkD(String arrCommand[]) {
		LinkedList<String> temp = new LinkedList<>();
		for (int i = 0; i < arrCommand.length; i++)
			if (arrCommand[i].equals("-d") || arrCommand[i].equals("--d"))
				temp.add(arrCommand[i + 1].replace("'", ""));
		return temp;
	}

	public static LinkedList<String> checkF(String arrCommand[]) {
		LinkedList<String> temp = new LinkedList<>();
		for (int i = 0; i < arrCommand.length; i++)
			if (arrCommand[i].equals("-f") || arrCommand[i].equals("--f"))
				temp.add(arrCommand[i + 1]);
		return temp;
	}

	public static URL checkURL(String arrCommand[])
			throws MalformedURLException {
		String sUrl = "";
		for (int i = 0; i < arrCommand.length; i++) {
			if (arrCommand[i].length() > 3
					&& arrCommand[i].subSequence(0, 4).equals("http"))
				sUrl = arrCommand[i];
		}
		sUrl = sUrl.replaceAll("'", "");
		URL temp = new URL(sUrl);
		return temp;
	}

	/**
	 * creates a get request message
	 * 
	 * @param pathName
	 * @param hostName
	 * @param header
	 * @return a get request message
	 */
	public String getRMsg(String pathName, String hostName, String header) {
		String line = "GET " + pathName + " HTTP/1.0\r\n"
				+ "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8\r\n"
				+ "User-Agent: concordia\r\n" + "Host: " + hostName + "\r\n"
				+ "Accept-Language: en-US,en;q=0.5\r\n"
				+ "Accept-Encoding: gzip, deflate, br\r\n" + "Dnt: 1\r\n"
				+ "Connection: Keep-Alive\r\n" + header + "\r\n" + "\r\n";
		return line;
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
				if (headers[i].length() > 10
						&& headers[i].substring(0, 11).equals("Content-Type"))
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
			Scanner reader = new Scanner(file);
			while (reader.hasNextLine())
				data += reader.nextLine();
			reader.close();
			header += "Content-Length:" + data.length() + "\n";
		}

		// check for url
		URL url = null;
		String host = "";
		int port = 80;
		if (!containsHelp) {
			url = checkURL(arrCommand);
			host = url.getHost();
		}

		System.out.println("---------------------------");

		if (!containsHelp) {
			if (method.equals("get")) {
				String rMsg = null;
//				String pathName = url.getPath() + "?" + url.getQuery();
//				rMsg = getRMsg(pathName, url.getHost(), header);
				System.out.println("Request Message");
				System.out.println(rMsg);
				System.out.println("---------------------------");
				System.out.println("Response Message");
				get(host, port, rMsg, containsV);
			} else if (method.equals("post")) {
				String rMsg = null;
				String pathName = url.getPath();
				rMsg = postRMsg(pathName, url.getHost(), header, data);
				System.out.println("Request Message");
				System.out.println(rMsg);
				System.out.println("---------------------------");
				System.out.println("Response Message");
				post(host, port, rMsg, containsV);
			}
		} else {
			if (method.equals("get")) {
				System.out.println("usage: httpc get [-v] [-h key:value] URL");
				System.out.println(
						"Get executes a HTTP GET request for a given URL");
				System.out.println(
						" -v Prints the detail of the response such as protocol, status, and headers.");
				System.out.println(
						" -h key:value Associates headers to HTTP Request with the format 'key:value'.");
			} else if (method.equals("post")) {
				System.out.println(
						"usage: httpc post [-v] [-h key:value] [-d inine-data] [-f file] URL");
				System.out.println(
						"Post executes a HTTP POST request for a given URL with inline data or from file.");
				System.out.println(
						" -v Prints the detail of the response such as protocol, status, and headers.");
				System.out.println(
						" -h key:value Associates headers to HTTP Request with the format 'key:value'.");
				System.out.println(
						" -d string associates an inline data to the body HTTP POST request.");
				System.out.println(
						" -f file Associates the content of a file to the body HTTP POST request.");
				System.out.println(
						"Either [-d] or [-f] can be use but not both.");
			} else {
				System.out.println(
						"httpc is a curl-like application but supports HTTP protocol only.");
				System.out.println("Usage:");
				System.out.println(" httpc command [arguments]");
				System.out.println("The commands are:");
				System.out.println(
						" get executes a HTTP GET request and prints the response.");
				System.out.println(
						" post executes a HTTP POST request and prints the response.");
				System.out.println(" help prints this screen.");
				System.out.println(
						"Use \"httpc help [command]\" for more information about a command.");
			}
		}
	}
}
