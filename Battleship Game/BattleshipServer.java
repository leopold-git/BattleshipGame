import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/* BATTLESHIP 
  General Notes: 
  LEGEND FOR MAP
  "#" indicates a field on the map where part of a ship is 
  "0" indicates a field on the map with water
  "X" indicates that part of a ship has been hit
  "-" indicates a field that has been hit, but no part of a ship was there 

	RULES
  - placing a ship uses coordinates with the form "row1,col1 row2,col2"
  - when placing one's ships, one places a ship of length 2, then 
  - firing at opponent uses coordinates with the form "rowGuess,colGuess"
*/

public class  BattleshipServer {
	public static void main(String[] args) throws IOException {
		ArrayList<Socket> clients = new ArrayList<>();
		ArrayList<Client> players = new ArrayList<>();
		boolean readyToStart = false;
		if (args.length != 1) {
			System.err.println("Usage: java EchoServer <port number>");
			System.exit(1);
		}
		int portNumber = Integer.parseInt(args[0]);
		try (ServerSocket serverSocket = new ServerSocket(Integer.parseInt(args[0])); // Listen to port on Server for
																						// incoming connections
		) {
			// maintain client size as 2
			while (clients.size() < 2) { // Loop for continuously listening for new incoming connections
				Socket clientSocket = serverSocket.accept(); // Connection is handed over from listening port to a
																// different port
				BufferedReader in = new BufferedReader( // Grab input stream from socket
						new InputStreamReader(clientSocket.getInputStream()));

				clients.add(clientSocket); // Add socket to arraylist of clients
				Client client = new Client(clientSocket, clients); // Initialize Client thread which will handle
																	// listening to incoming messages from client
				players.add(client);
				if (players.size() == 2) {
					// this means game is full. 2 players/clients have been established
					// print -- "2 players are connected. Ready to start placing ships"
					System.out.println(" Note : 2 players are connected already--no more players can be accepted.");
					readyToStart = true;
				}
				// game begins with 2 clients
				if (readyToStart == true) {
					if (players.get(1) != null) {
						// set opponents from clients
						players.get(0).opponent = players.get(1);
						players.get(1).opponent = players.get(0);

						// first player in list gets to go frrist
						players.get(0).turn = true;
					}
					// game begins
					players.get(0).start();
					players.get(1).start();
				}

			}
		} catch (IOException e) {
			System.out.println(
					"Exception caught when trying to listen on port " + portNumber + " or listening for a connection");
			System.out.println(e.getMessage());
		}
	}
}

/*
 * Extended thread class to handle a while loop waiting for input from client
 */
class Client extends Thread {
	String name;
	Socket socket;
	ArrayList<Socket> clients;
	boolean hasWon = false;
	boolean firstTurn = false;
	boolean turn = false;
	Map ownMap = new Map();
	Client opponent;
	Map enemyMap = new Map();
	boolean doneChoosing;
	int ownShipsNotHit = 3;
	Ship[] ships = new Ship[3];
	int placedShips = 0;
	PrintWriter out;

	Client(Socket socket, ArrayList<Socket> clients) {
		this.socket = socket;
		this.clients = clients;
		doneChoosing = false;
	}

	public void run() {
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			PrintWriter out2 = new PrintWriter(this.socket.getOutputStream(), true);
			out2.println("Welcome to Battleship Game! To start placing your ships, press enter.");

			String inputLine;
			HashSet<String> allCoordinates = new HashSet<String>();
			while ((inputLine = in.readLine()) != null) { // Wait for incoming message from client

				Socket curSocket = this.socket;
				out = new PrintWriter(curSocket.getOutputStream(), true);

				// keeps going until ships are done placed
				if (doneChoosing == false) {
					out.println("Place your three ships.");
					out.println(
							"Enter coordinates for first ship in the following form: row1,col1 row2,col2. First pair of coordinates must be closer to the origin than the second pair of coordinates");
					int shipSize = 2;
					int holder = 0;
					// placing appropriate # of ships with appropriate sizes
					while (this.placedShips < 3 && shipSize <= 4) {
						holder = this.placedShips;

						// user picks ship with size 2, then ship with size 3, then ship with size 4
						out.println("Type in coordinates for ship number " + (placedShips + 1) + " with length "
								+ shipSize + "."); // user
						String coordIn = in.readLine();

						// if not valid, try again
						while (!validInputSetShip(coordIn)) {
							out.println("Coordinates are not valid. Please choose again");
							coordIn = in.readLine();
						}
						// if not valid, try again
						while (!validInputSetShip(coordIn)) {
							out.println("Coordinates are not valid. Please choose again");
							coordIn = in.readLine();
						}

						boolean exists = false;
						String coords[] = coordIn.split(" ");
						for (int i = 0; i < coords.length; i++) {
							if (allCoordinates.contains(coords[i])) {
								exists = true;
								break;
							}
						}

						while (exists == true) {
							out.println("Ship exists there! Please choose again");
							coordIn = in.readLine();
							while (!validInputSetShip(coordIn)) {
								out.println("Coordinates are not valid. Please choose again");
								coordIn = in.readLine();
							}
							String newCoords [] = coordIn.split(" ");
							exists = false;
							for (int i = 0; i < newCoords.length; i++) {
								if (allCoordinates.contains(newCoords[i])) {
									exists = true;
									break;
								}
							}
						}

						for (int i = 0; i < coords.length ; i++) {
							allCoordinates.add(coords[i]);
						}
						this.setShip(coordIn, shipSize);
						placedShips++;
						if (this.placedShips != holder) {
							shipSize++;
						}
					}
					out.println("Here is your map with your placed ships: ");
					this.showOwnMap();
					this.doneChoosing = true;
				}

				// if opponent is not ready, game is on pause/loading until he/she is done
				while (this.opponent.doneChoosing == false) {
					out.println("Opponent is not done placing ships, please hold.");
					TimeUnit.SECONDS.sleep(10);
				}

				// BEGIN OF ACTUAL GAME PLAY
				if (this.doneChoosing == true && this.opponent.doneChoosing == true) {

					this.enemyMap = this.opponent.ownMap;
					while (this.hasWon == false && this.opponent.hasWon == false) {

						// loading while waiting for opponent
						if (this.turn == false) {
							out.println("Opponent is attacking! ");
							TimeUnit.SECONDS.sleep(8);
						}

						// your turn
						if (this.turn == true) {
							out.println(" ");
							out.println("Your turn to attack: ");
							String reader = in.readLine();

							// check for valid placements
							while (!validInputFire(reader)) {
								out.println("Coordinates are not valid. Please choose again");
								reader = in.readLine();
							}

							// fire coordinates
							this.fire(reader);

							// if winner, game is over
							if (this.checkifWinner() == true) {
								out.println("You WON!");
								this.opponent.out.println("You LOST");
								break;
							}

							// if game is not done, your opponent's turn
							out.println(" ");
							out.println("Your turn is over. Opponent will fire next. ");
							out.println(" ");
							this.opponent.turn = true;
							this.turn = false;

						}
					}
					out.println("Game is over!");
				}
			}
		} catch (IOException ie) {
			ie.printStackTrace();
		} catch (InterruptedException ex) {
			ex.printStackTrace();
		}
	}

	/**
	 * checks if coordinates for setting ship are valid
	 * 
	 * @param coord
	 *            given coordinates
	 * @return boolean of valid placement
	 */
	public boolean validInputSetShip(String coord) {
		if (!(coord.contains(",")) || !(coord.contains(" "))) {
			return false;
		} else {
			String[] splitter = coord.split(" ");
			if (splitter.length != 2)
				return false;
			if (!(splitter[0].contains(",")) || !(splitter[1].contains(",")))
				return false;
			String[] firstCoord = splitter[0].split(",");
			String[] secondCoord = splitter[1].split(",");
			if (firstCoord.length != 2 && secondCoord.length != 2)
				return false;
			for (int i = 0; i < firstCoord.length; i++) {
				for (int j = 0; j < firstCoord[i].length(); j++) {
					if (!Character.isDigit(firstCoord[i].charAt(j)))
						return false;
				}
			}
			for (int i = 0; i < secondCoord.length; i++) {
				for (int j = 0; j < secondCoord[j].length(); j++) {
					if (!Character.isDigit(secondCoord[i].charAt(j)))
						return false;
				}
			}
			int x1 = Integer.parseInt(firstCoord[0]);
			int y1 = Integer.parseInt(firstCoord[1]);
			int x2 = Integer.parseInt(secondCoord[0]);
			int y2 = Integer.parseInt(secondCoord[1]);
			if (x1 >= 10 || y1 >= 10 || x2 >= 10 || y2 >= 10)
				return false;
			if ((x1 == x2 && y1 > y2) || (y1 == y2 && x1 > x2)) {
				out.println(
						"First pair of coordinates must be closer to the origin than the second pair of coordinates!!!");
				out.println("Try again!");
				return false;
			}
		}
		return true;
	}

	/**
	 * checks if input for attacking is valid
	 * 
	 * @param coord
	 *            given coordinates
	 * @return boolean of attack coordinates
	 */
	public boolean validInputFire(String coord) {
		if (!coord.contains(",")) {
			return false;
		} else {
			String[] splitter = coord.split(",");
			if (splitter.length != 2)
				return false;
			for (int i = 0; i < splitter.length; i++) {
				for (int j = 0; j < splitter[i].length(); j++) {
					if (!Character.isDigit(splitter[i].charAt(j)))
						return false;
				}
			}
			int x = Integer.parseInt(splitter[0]);
			int y = Integer.parseInt(splitter[1]);
			if (x >= 10 || y >= 10)
				return false;
		}
		return true;
	}

	/**
	 * Places ships
	 * 
	 * @param coordinates
	 *            given coordinates for placement
	 * @param length
	 *            ship size
	 */
	public void setShip(String coordinates, int length) {
		String splitUp[] = coordinates.split(" ");
		String pair1 = splitUp[0];
		String pair2 = splitUp[1];

		String firstPair[] = pair1.split(",");
		String secondPair[] = pair2.split(",");

		int row1 = Integer.parseInt(firstPair[0]);
		int col1 = Integer.parseInt(firstPair[1]);

		int row2 = Integer.parseInt(secondPair[0]);
		int col2 = Integer.parseInt(secondPair[1]);

		if (row1 - row2 == 0 && Math.abs(col1 - col2) == length - 1) { // horizontal ship
			this.ownMap.map[row1][col1] = "#";
			this.ownMap.map[row2][col2] = "#";
			ships[this.placedShips] = new Ship(this.ownMap);
			ships[this.placedShips].coordinates = coordinates;
			ships[this.placedShips].fields.add(pair1);
			ships[this.placedShips].fields.add(pair2);

			for (int j = col1 + 1; j < col2; j++) {
				this.ownMap.map[row1][j] = "#";
				ships[this.placedShips].members++;
				String newAdd = row1 + "," + j;
				ships[this.placedShips].fields.add(newAdd);
			}
		}

		if (col1 - col2 == 0 && Math.abs(row2 - row1) == length - 1) { // vertical ship
			this.ownMap.map[row1][col1] = "#";
			this.ownMap.map[row2][col2] = "#";
			ships[this.placedShips] = new Ship(this.ownMap);
			ships[this.placedShips].coordinates = coordinates;
			//ships[this.placedShips].fields.add(coordinates);
			ships[this.placedShips].fields.add(pair1);
			ships[this.placedShips].fields.add(pair2);

			for (int k = row1 + 1; k < row2; k++) {
				this.ownMap.map[k][col1] = "#";
				ships[this.placedShips].members++;
				String newAdd = k + "," + col1;
				ships[this.placedShips].fields.add(newAdd);

			}
		}

		if (col1 - col2 != 0 && row1 - row2 != 0) {
			out.println("Diagonal ship is not possible. Please re-enter: ");
			this.placedShips--;

		}
		if ((col1 - col2 == 0 && Math.abs(row1 - row2) != length - 1)
				|| (row1 - row2 == 0 && Math.abs(col1 - col2) != length - 1)) {
			out.println("Ship length entered is invalid. Try again: ");
			this.placedShips--;

		}

	}

	/**
	 * shows a visual of the map--both own map and a map of what is known about
	 * opponent
	 */
	public void showMaps() {
		for (int i = 0; i < 10; i++) {
			this.out.println(" ");
			for (int j = 0; j < 10; j++) {
				this.out.print(this.ownMap.map[i][j] + " ");

			}
		}
		this.out.println("");
		this.out.println("");
		this.out.println("Currently Known Opponent's Map: ");
		for (int i = 0; i < 10; i++) {
			this.out.println(" ");
			for (int j = 0; j < 10; j++) {
				this.out.print(this.enemyMap.map[i][j] + " ");
			}
		}
	}

	/**
	 * shows a visual of one's own map
	 */
	public void showOwnMap() {
		for (int i = 0; i < 10; i++) {
			this.out.println(" ");
			for (int j = 0; j < 10; j++) {
				this.out.print(this.ownMap.map[i][j] + " ");
			}
		}
		this.out.println(" ");
		this.out.println(" ");
	}

	/**
	 * shows your opponent a visual of their own map when they get attacked
	 */
	public void showOpponentTheirOwnMap() {
		for (int i = 0; i < 10; i++) {
			this.opponent.out.println(" ");
			for (int j = 0; j < 10; j++) {
				this.opponent.out.print(this.opponent.ownMap.map[i][j] + " ");
			}
		}
		this.opponent.out.println(" ");
		this.opponent.out.println(" ");
	}

	/**
	 * Checks if there is a winner or all opponent's ships have been sunk
	 * 
	 * @return boolean of winner
	 */
	public boolean checkifWinner() {
		if (this.opponent.ships[0].sunk == true && this.opponent.ships[1].sunk == true
				&& this.opponent.ships[2].sunk == true) {
			this.hasWon = true;
			return true;
		}
		return false;
	}

	/**
	 * Fire attack
	 * 
	 * @param fireCoord
	 *            given attack coordinates
	 */
	public void fire(String fireCoord) { // fireCoord have form: "rowGuess,colGuess"
		String splitUp[] = fireCoord.split(",");
		String rowString = splitUp[0];
		String colString = splitUp[1];

		int[] realCoord = new int[2];
		realCoord[0] = Integer.parseInt(rowString);
		realCoord[1] = Integer.parseInt(colString);

		int rowGuess = realCoord[0];
		int colGuess = realCoord[1];

		if (this.enemyMap.map[rowGuess][colGuess].equals("#")) {
			out.println("YOU HIT SOMETHING");
			this.opponent.out.println();
			this.opponent.out.println("You got hit!");
			this.opponent.out.println();
			this.opponent.ownMap.map[rowGuess][colGuess].equals("X");
			this.opponent.out.println("Current state of your map: ");
			this.enemyMap.map[rowGuess][colGuess] = "X";
			this.showOpponentTheirOwnMap();
			for (int i = 0; i < this.opponent.ships.length; i++) {
				this.opponent.ships[i].checkShipSunk();
				String message = this.opponent.ships[i].ifSunkMessage();
				if (!(message == null)) {
					out.println(message);
					this.opponent.out.println(message);
				}
			}
		} else if (this.enemyMap.map[rowGuess][colGuess].equals("-")
				|| this.enemyMap.map[rowGuess][colGuess].equals("X")) {
			out.println("You've already explored this field. Too bad.");
			this.opponent.out.println("Opponent selected already explored field. Current state of your map: ");
			this.showOpponentTheirOwnMap();
		} else if (this.enemyMap.map[rowGuess][colGuess].equals("0")) {
			out.println("You did not hit anything.");
			this.opponent.out.println("You didn't get hit by the opponent.");
			this.opponent.out.println("Current state of your map: ");
			this.opponent.ownMap.map[rowGuess][colGuess].equals("-");
			this.enemyMap.map[rowGuess][colGuess] = "-";
			this.showOpponentTheirOwnMap();
		}

	}

}

class Map {
	String[][] map = new String[10][10];

	public Map() { // 10x10 map
		this.map = new String[10][10];
		for (int i = 0; i < 10; i++) {
			System.out.println(" ");
			for (int j = 0; j < 10; j++) {
				this.map[i][j] = "0";
			}
		}
	}

}

class Ship {
	String coordinates = " ";
	Map map;
	ArrayList<String> fields = new ArrayList<>();
	int members = 1;
	boolean sunk = false;
	boolean hasBeenSaid = false;
	public Ship(Map map) {
		this.map = map;

	}

	/**
	 * checks if ship is sunk
	 */
	public void checkShipSunk() {
		int marks = 0;
		for (int i = 0; i < this.fields.size(); i++) {
			String coord = this.fields.get(i);
			String[] splitter = coord.split(",");
			int x = Integer.parseInt(splitter[0]);
			int y = Integer.parseInt(splitter[1]);
			if (map.map[x][y].equals("X")) {
				marks++;
			}
			sunk = (this.fields.size() == marks);
		}
	}

	/**
	 * if ship is sunk, then given sunk message
	 * 
	 * @return String of sunk message if sunk
	 */
	public String ifSunkMessage() {
		if (this.sunk == true && hasBeenSaid == false){
			hasBeenSaid = true;
			return "Ship is destroyed!";
		}
		return null;
	}

}