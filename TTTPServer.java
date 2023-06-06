import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.*;

public class TTTPServer {
  static final int BUFFERSIZE = 1024;
  static final int DEFAULT_PORT = 3116;
  static int PORT = DEFAULT_PORT;

  static HashMap<String, ArrayList<Object>> playerContacts = new HashMap<>();
  static int nextAvailableSession = 0;
  static HashMap<Integer, Game> games = new HashMap<>();
  static int nextAvailableGID = 0;
  static boolean log = true;

  public static void main(String[] args) {
    if (args.length > 0) {
      try {
        PORT = Integer.parseInt(args[0]);
      } catch (Exception e) {
        throw new IllegalArgumentException("Port must be an int");
      }
    }
    if (args.length > 1) {
      if (args[1].equals("NOLOG")) {
        log = false;
      }
    }

    new Thread(new tcpServer()).start();
    new Thread(new udpServer()).start();
  }

  static class tcpServer implements Runnable{
    public void run() {
      try (
        ServerSocket server = new ServerSocket(PORT);
      ) {
        ExecutorService executorService = Executors.newFixedThreadPool(5);
        while (true) {
          Socket client = server.accept();
          if (log) {
            System.out.println("New client connected: " + client.getInetAddress().getHostAddress());
          }
          executorService.submit(() -> handleClient(client));
        }
      } catch(Exception e) {
        if (log) {
          e.printStackTrace(System.out);
        }
      }
    }

    private static void handleClient(Socket client) {
      try (
        PrintWriter out = new PrintWriter(client.getOutputStream(), true);
        BufferedReader in = new BufferedReader(
          new InputStreamReader(client.getInputStream()));
      ) {
        String inputLine;
        while ((inputLine = in.readLine()) != null) {
          ArrayList<Object> contactInfo = new ArrayList<>();
          contactInfo.add("tcp");
          contactInfo.add(client);
          String returnMessage = getReturnMessage(inputLine, contactInfo);
          if (returnMessage.isEmpty()) {
            continue;
          }
          out.println(returnMessage);
        }
      } catch (Exception e) {
        if (log) {
          e.printStackTrace(System.out);
        }
      }
    }
  }

  static class udpServer implements Runnable {
    public void run() {
      try (
        DatagramSocket sock = new DatagramSocket(PORT);
        ) {
          ExecutorService executorService = Executors.newFixedThreadPool(5);
          while(true) {
            byte[] receiveData = new byte[BUFFERSIZE];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            sock.receive(receivePacket);
            if (log) {
              System.out.println("New datagram packet received");
            }
            executorService.submit(() -> handleClient(sock, receivePacket));
          }
      } catch (Exception e) {
        if (log) {
          e.printStackTrace(System.out);
        }
      }
    }

    private static void handleClient(DatagramSocket sock, DatagramPacket pack) {
      InetAddress address = pack.getAddress();
      int port = pack.getPort();
      String sentence = new String(pack.getData());
      ArrayList<Object> contactInfo = new ArrayList<>();
      contactInfo.add("udp");
      contactInfo.add(sock);
      contactInfo.add(pack);
      String returnMessage = getReturnMessage(sentence, contactInfo);
      if (returnMessage.isEmpty()) {
        return;
      }
      byte[] buf = (returnMessage).getBytes();
      try {
        sock.send(new DatagramPacket(buf, buf.length, address, port));
      } catch (Exception e) {
        if (log) {
          e.printStackTrace(System.out);
        }
      }
    }
  }

  public static void contactPlayer(String CID, String msg) {
    ArrayList<Object> contactInfo = playerContacts.get(CID);
    if (contactInfo.get(0).equals("tcp")) {
      tcpContactPlayer((Socket) contactInfo.get(1), msg);
    } else if (contactInfo.get(0).equals("udp")) {
      udpContactPlayer((DatagramSocket) contactInfo.get(1), (DatagramPacket) contactInfo.get(2), msg);
    }
  }

  public static void tcpContactPlayer(Socket client, String msg) {
    try {
      PrintWriter out = new PrintWriter(client.getOutputStream(), true);
      out.println(msg);
    } catch (Exception e) {
      if (log) {
        e.printStackTrace(System.out);
      }
    }
  }

  public static void udpContactPlayer(DatagramSocket sock, DatagramPacket pack, String msg) {
    InetAddress address = pack.getAddress();
    int port = pack.getPort();
    byte[] buf = (msg).getBytes();
    try {
      sock.send(new DatagramPacket(buf, buf.length, address, port));
    } catch (Exception e) {
      if (log) {
        e.printStackTrace(System.out);
      }
    }
  }

  public static String getReturnMessage(String incomingMessage, ArrayList<Object> contactInfo) {
    incomingMessage = incomingMessage.replaceAll("\u0000", "");
    if (log) {
      System.out.println(incomingMessage);
    }
    String[] splitMessage = incomingMessage.split("\\s+");
    if (splitMessage.length <= 0) {
      return "";
    }
    int GID;
    String returnMessage = "";
    switch (splitMessage[0]) {
      case "CREA":
        if (splitMessage.length < 1) {
          break;
        }
        if (!playerContacts.containsKey(splitMessage[1])) {
          break;
        }

        // CID
        returnMessage = "JOND " + splitMessage[1] + " " + Integer.toString(createNewGame(splitMessage[1]));

        break;
      case "GDBY":
        if (splitMessage.length < 1) {
          break;
        }
        if (!playerContacts.containsKey(splitMessage[1])) {
          break;
        }

        // CID
        ArrayList<Integer> delete = new ArrayList<>();
        for (Game game : games.values()) {
          if (game.p1.equals(splitMessage[1]) || game.p2.equals(splitMessage[2])) {
            String winner = game.p1.equals(splitMessage[1]) ? game.p2 : game.p1;
            contactPlayer(winner, "TERM " + Integer.toString(game.GID) + " " + winner);
            delete.add(game.GID);
          }
        }
        for (Integer gid : delete) {
          games.remove(gid);
        }
        playerContacts.remove(splitMessage[1]);

        break;
      case "HELO":
        if (splitMessage.length < 2) {
          break;
        }
        if (playerContacts.containsKey(splitMessage[2])) {
          break;
        }

        // VERSION CID
        returnMessage = "SESS 1 " + Integer.toString(nextAvailableSession);
        nextAvailableSession++;
        playerContacts.put(splitMessage[2], contactInfo);

        break;
      case "JOIN":
        if (splitMessage.length < 2) {
          break;
        }
        try {
          GID = Integer.parseInt(splitMessage[1]);
        } catch (Exception e) {
          break;
        }
        if (!games.containsKey(GID)) {
          break;
        }
        if (!playerContacts.containsKey(splitMessage[2])) {
          break;
        }
        Game joiningGame = games.get(GID);
        if (!joiningGame.p2.isEmpty()) {
          break;
        }

        // GID CID
        contactPlayer(splitMessage[2], "JOND " + splitMessage[2] + " " + Integer.toString(joiningGame.GID));
        String yrmv = "YRMV " + joiningGame.GID + " " + joiningGame.p1;
        returnMessage = yrmv;
        contactPlayer(joiningGame.p1, yrmv);
        joiningGame.p2 = splitMessage[2];

        break;
      case "LIST":
        returnMessage = "GAMS" + getGames();
        break;
      case "MOVE":
        if (splitMessage.length < 2) {
          break;
        }
        try {
          GID = Integer.parseInt(splitMessage[1]);
        } catch (Exception e) {
          break;
        }
        int move = 0;
        boolean useCoords = false;
        int[] coords = new int[2];
        try {
          move = Integer.parseInt(splitMessage[2]);
        } catch (Exception e) {
          useCoords = true;
          String[] coordsSplit = splitMessage[2].split(",");
          try {
            coords[0] = Integer.parseInt(coordsSplit[0]);
            coords[1] = Integer.parseInt(coordsSplit[1]);
          } catch (Exception ex) {
            break;
          }
        }
        if (!games.containsKey(GID)) {
          break;
        }
        if (!playerContacts.containsKey(splitMessage[3])) {
          break;
        }

        // GID MOVE CID
        Game playingGame = games.get(GID);
        if (!playingGame.isTurn(splitMessage[3])) {
          break;
        }
        if (playingGame.p2.isEmpty()) {
          break;
        }
        boolean wasValidMove;
        if (useCoords) {
          wasValidMove = playingGame.playMove(coords[0], coords[1]);
        } else {
          wasValidMove = playingGame.playMove(move);
        }
        if (playingGame.isStalemate()) {
          contactPlayer(playingGame.p1, "TERM KTHXBYE");
          contactPlayer(playingGame.p2, "TERM KTHXBYE");
          games.remove(GID);
          break;
        } else if (playingGame.isOver()) {
          String winner = playingGame.p1Turn ? playingGame.p2 : playingGame.p1;
          contactPlayer(playingGame.p1, "TERM " + winner);
          contactPlayer(playingGame.p2, "TERM " + winner);
          games.remove(GID);
        } else {
          contactPlayer(playingGame.p1, playingGame.toString());
          contactPlayer(playingGame.p2, playingGame.toString());
        }
        if (wasValidMove) {
          String s = "YRMV " + playingGame.GID + " " + (playingGame.p1Turn ? playingGame.p1 : playingGame.p2);
          contactPlayer(playingGame.p1, s);
          contactPlayer(playingGame.p2, s);
        }

        break;
      case "QUIT":
        if (splitMessage.length < 1) {
          break;
        }
        try {
          GID = Integer.parseInt(splitMessage[1]);
        } catch (Exception e) {
          break;
        }
        if (!playerContacts.containsKey(splitMessage[2])) {
          break;
        }
        Game g = games.get(GID);
        if (!splitMessage[2].equals(g.p1) && !splitMessage[2].equals(g.p2)) {
          break;
        }

        // GID CID
        String winner = g.p1.equals(splitMessage[2]) ? g.p2 : g.p1;
        contactPlayer(g.p1, "TERM " + winner);
        contactPlayer(g.p2, "TERM " + winner);
        games.remove(GID);

        break;
      case "STAT":
        if (splitMessage.length < 1) {
          break;
        }
        try {
          GID = Integer.parseInt(splitMessage[1]);
        } catch (Exception e) {
          break;
        }

        // GID
        returnMessage = games.get(GID).toString();

        break;
    }
    return returnMessage;
  }

  public static void messagePlayer(String CID) {
    
  }

  public static int createNewGame(String CID) {
    int cGUID = nextAvailableGID;
    TTTPServer s = new TTTPServer();
    Game g = s.new Game(CID, cGUID);
    games.put(cGUID, g);
    nextAvailableGID++;
    return cGUID;
  }

  public class Game {
    public int GID;
    public String p1;
    public String p2;

    boolean p1Turn;
    String[] board;

    public Game(String p1, int GID) {
      this.GID = GID;
      this.p1 = p1;
      this.p2 = "";
      this.p1Turn = true;
      this.board = new String[9];
      for (int i = 0; i < this.board.length; i++) {
        this.board[i] = "*";
      }
    }

    public boolean isTurn(String player) {
      if (p1Turn && p1.equals(player)) {
        return true;
      } else if (!p1Turn && p2.equals(player)) {
        return true;
      }
      return false;
    }
    
    public boolean playMove(int x, int y) {
      return playMove((y - 1) * 3 + x);
    }

    public boolean playMove(int place) {
      if (!isMoveLegal(place)) {
        return false;
      }

      board[place - 1] = p1Turn ? "X" : "O";

      p1Turn = !p1Turn;
      return true;
    }

    public boolean isMoveLegal(int place) {
      if (place < 1 || place > 9) {
        return false;
      }
      if (!board[place - 1].equals("*")) {
        return false;
      }
      return true;
    }

    public boolean isStalemate() {
      if (areSame(0, 1, 2) || areSame(3, 4, 5) || areSame(6, 7, 8) || areSame(0, 3, 6) || areSame(1, 4, 7) || areSame(2, 5, 8)) {
        return false;
      }
      for (String string : board) {
        if (string.equals("*")) {
          return false;
        }
      }
      return true;
    }

    public boolean isOver() {
      if (areSame(0, 1, 2) || areSame(3, 4, 5) || areSame(6, 7, 8) || areSame(0, 3, 6) || areSame(1, 4, 7) || areSame(2, 5, 8)) {
        return true;
      }
      for (String string : board) {
        if (string.equals("*")) {
          return false;
        }
      }
      return true;
    }

    private boolean areSame(int... places) {
      String type = null;
      for (int i : places) {
        if (board[i] == "*") {
          return false;
        }
        if (type == null) {
          type = board[i];
        }
        if (!type.equals(board[i])) {
          return false;
        }
      }
      return true;
    }

    public String toString() {
      StringBuilder boardString = new StringBuilder();
      boardString.append("BORD ");
      boardString.append(GID);
      boardString.append(" " + p1);
      if (p2.isEmpty()) {
        return boardString.toString();
      }
      boardString.append(" " + p2 + " ");
      boardString.append(p1Turn ? p1 : p2);
      boardString.append(" ");
      boardString.append("|");
      for (int i = 0; i < board.length; i++) {
        boardString.append(board[i]);
        boardString.append("|");
      }
      return boardString.toString();
    }
  }

  public static String getGames() {
    StringBuilder gameIDs = new StringBuilder();
    for (Integer GID : games.keySet()) {
      gameIDs.append(" ");
      gameIDs.append(GID);
    }
    return gameIDs.toString();
  }
}
