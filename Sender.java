import java.io.IOException;

/**
 * This object retransmits messages on a thread
 * @author carlo
 *
 */
public class Sender extends Thread {

	private int sequenceNumber;
	private Packet p;
	private httpAbstract server;
	private int delay;

	public Sender(int sequenceNumber, Packet p, httpAbstract server) {
		this.sequenceNumber = sequenceNumber;
		this.p = p;
		this.server = server;
		this.delay = server.TIMEOUT_DUR;
	}

	public void run() {
		try {
			while (true) {
				synchronized (this) {
					this.wait(delay);
				}
				if (server.acked.get(sequenceNumber) == null) {
					System.out.println("--==Sequence " + sequenceNumber + " not acked==--");
					server.channel.send(p.toBuffer(), server.routerAddress);
				} else {
					System.out.println("--==Sequence " + sequenceNumber + " acked==--");
					break;
				}
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
