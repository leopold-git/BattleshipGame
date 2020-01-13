import java.io.*;
import java.net.*;

public class BattleshipClient {

	public static void main(String[] args) throws IOException {

		if (args.length != 2) {
			System.err.println("Usage: java EchoClient <host name> <port number>");
			System.exit(1);
		}

		String hostName = args[0]; // get host name
		int portNumber = Integer.parseInt(args[1]); // get port number
		try (Socket echoSocket = new Socket(hostName, portNumber); // Connect socket to given host name and port number
				PrintWriter out = new PrintWriter(echoSocket.getOutputStream(), true); // Get output stream of socket
																						// pass it into a PrintWriter so
																						// we can write to the server
				BufferedReader stdIn = // Get input stream from standard in (Keyboard input from console)
						new BufferedReader(new InputStreamReader(System.in))) {
			String userInput;
			String serverInput;
			ServerListener serverListener = new ServerListener(echoSocket); // Initialize a thread to listen for input
																			// from server
			serverListener.start();
			while ((userInput = stdIn.readLine()) != null) { // Wait for input from standard in
				out.println(userInput);
			}

		} catch (UnknownHostException e) {
			System.err.println("Don't know about host " + hostName);
			System.exit(1);
		} catch (IOException e) {
			System.err.println("Couldn't get I/O for the connection to " + hostName);
			System.exit(1);
		}
	}
}

/*
 * Extended thread class to handle a while loop waiting for input from server"
 */
class ServerListener extends Thread {
	Socket socket;

	ServerListener(Socket socket) {
		this.socket = socket;
	}

	public void run() {
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream())); // Input stream from
																									// server
			String serverInput;
			while ((serverInput = in.readLine()) != null) {
				System.out.println(serverInput); // Print input from server into console
			}
		} catch (IOException ie) {

		}
	}
}
