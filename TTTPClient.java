import java.net.*;
import java.io.*;
import java.util.concurrent.*;

public class TTTPClient {
  static final int BUFFERSIZE = 1024;
  static final int DEFAULT_PORT = 3116;
  
  public static void main(String[] args) {
    if (args.length < 1) {
      throw new IllegalArgumentException("Must include hostname and port arguments respectively");
    }

    int port = DEFAULT_PORT;
    if (args.length >= 2) {
      try {
        port = Integer.parseInt(args[1]);
      } catch(Exception e) {
        throw new IllegalArgumentException("Port must be an integer");
      }
    }

    String[] splitHostname = args[0].split("://");
    if (splitHostname.length < 2) {
      throw new IllegalArgumentException("Hostname must include scheme of t3tcp or t3udp. For example: t3tcp://localhost");
    }

    String scheme = splitHostname[0];
    String hostname = splitHostname[1];
    if (scheme.equals("t3tcp")) {
      tcpRequest(hostname, port);
    } else if (scheme.equals("t3udp")) {
      udpRequest(hostname, port);
    } else {
      throw new IllegalArgumentException("Hostname must include scheme of t3tcp or t3udp. For example: t3tcp://localhost");
    }
  }

  public static void tcpRequest(String hostname, int port) {
    try (
      Socket echoSocket = new Socket(hostname, port);
      PrintWriter out =
        new PrintWriter(echoSocket.getOutputStream(), true);
      BufferedReader in =
        new BufferedReader(
          new InputStreamReader(echoSocket.getInputStream()));
      BufferedReader stdIn =
        new BufferedReader(
          new InputStreamReader(System.in))
    ) {
      out.println(getHelo(stdIn));

      ExecutorService executorService = Executors.newFixedThreadPool(5);
      executorService.submit(() -> tcpReader(in));
      String userInput;
      while (true) {
        if ((userInput = stdIn.readLine()) == null) {
          break;
        }
        out.println(userInput);
      }
    } catch (Exception e) {
      e.printStackTrace(System.out);
    }
  }

  public static void tcpReader(BufferedReader in) {
    try {
      while (true) {
        String nextLine = in.readLine();
        System.out.println("From Server: " + nextLine);
      }
    } catch (Exception e) {
      e.printStackTrace(System.out);
    }
  }

  public static void udpRequest(String hostname, int port) {
    try (
      BufferedReader stdIn = new BufferedReader(
        new InputStreamReader(System.in));
      DatagramSocket clientSocket = new DatagramSocket();
    ) {
      byte[] sendBytes = new byte[BUFFERSIZE];
      InetAddress ipAddress = InetAddress.getByName(hostname);
      
      String helo = getHelo(stdIn);
      sendBytes = helo.getBytes();
      clientSocket.send(new DatagramPacket(sendBytes, sendBytes.length, ipAddress, port));

      ExecutorService executorService = Executors.newFixedThreadPool(5);
      executorService.submit(() -> udpReader(clientSocket));

      String userInput;
      while (true) {
        if ((userInput = stdIn.readLine()) == null) {
          break;
        }
        sendBytes = userInput.getBytes();
        DatagramPacket sendPacket = new DatagramPacket(sendBytes, sendBytes.length, ipAddress, port);
        clientSocket.send(sendPacket);
        
        sendBytes = new byte[BUFFERSIZE];
      }
    } catch (Exception e) {
      e.printStackTrace(System.out);
    }
  }

  public static void udpReader(DatagramSocket clientSocket) {
    try {
      byte[] receiveBytes = new byte[BUFFERSIZE];
      while (true) {
        DatagramPacket receivePacket = new DatagramPacket(receiveBytes, receiveBytes.length);
        clientSocket.receive(receivePacket);
        byte[] msgBuffer = receivePacket.getData(); 
        int length = receivePacket.getLength(); 
        int offset = receivePacket.getOffset();
        String replayString = new String(msgBuffer, offset, length);
        System.out.println("From Server: " + replayString);
    
        receiveBytes = new byte[BUFFERSIZE];
      }
    } catch (Exception e) {
      e.printStackTrace(System.out);
    }
  }

  public static String getHelo(BufferedReader stdIn) {
    try {
      String userInput;
      while (true) {
        System.out.print("Enter user ID: ");
        userInput = stdIn.readLine();
        if (userInput.length() == 0) {
          System.out.println("ID must not be blank");
        } else {
          break;
        }
      }
      return "HELO 1 " + userInput;
    } catch (Exception e) {
      e.printStackTrace(System.out);
    }
    return null;
  }
}