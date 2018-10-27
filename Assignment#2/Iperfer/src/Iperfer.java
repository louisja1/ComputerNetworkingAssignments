import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;

public class Iperfer {
    public static String getHostname(List<String> argsList) {
        return argsList.get(argsList.indexOf("-h") + 1);
    }
    public static Integer getPort(List<String> argsList) {
        Integer port = Integer.parseInt(argsList.get(argsList.indexOf("-p") + 1));
        if (port < 1024 || port > 65535) {
            System.out.println("Error: port number must be in the range 1024 to 65535");
            System.exit(2);
        }
        return port;
    }
    public static Integer getTime(List<String> argsList) {
        return Integer.parseInt(argsList.get(argsList.indexOf("-t") + 1));
    }
    public static void main(String[] args) throws Exception {
        List argsList = Arrays.asList(args);
        byte[] data = new byte[1000];
        byte[] buffer = new byte[1000];

        if (argsList.contains("-c")) {
            // Client Mode
            System.out.println("=============== Client Mode ===============");

            if (!argsList.contains("-h") || !argsList.contains("-p") || !argsList.contains("-t") || argsList.size() != 7) {
                System.out.println("Error: missing or additional arguments");
                System.exit(1);
            }

            String serverHostname = getHostname(argsList);
            Integer serverPort = getPort(argsList);
            Integer time = getTime(argsList);

            //System.out.println(serverHostname + " " + serverPort + " " + time);

            Socket clientSocket = null;
            try {
                clientSocket = new Socket(serverHostname, serverPort);
            } catch (IOException e) {
                System.out.println("Exception : " + e.getMessage());
                System.exit(3);
            }

            if (clientSocket == null) {
                System.out.println("Error : fail to establish a client socket");
                System.exit(4);
            }

            try {
                OutputStream output = clientSocket.getOutputStream();
                double startTime = System.currentTimeMillis() / 1000.0;
                int iteration = 0;


                while (System.currentTimeMillis() / 1000.0 - startTime <= time) {
                    iteration = iteration + 1;
                    output.write(data);
                    output.flush();
                }

                double duration = System.currentTimeMillis() / 1000.0 - startTime;

                output.close();
                clientSocket.close();
                System.out.println("===== Summary ======");
                System.out.printf("received = %d KB  rate = %.3f Mbps\n", iteration, iteration / 125.0 / duration);
            } catch (IOException e) {
                System.out.println("Exception : " + e.getMessage());
                System.exit(5);
            }

        } else if (argsList.contains("-s")) {
            // Server Mode
            System.out.println("=============== Server Mode ===============");

            if (!argsList.contains("-p") || argsList.size() != 3) {
                System.out.println("Error: missing or additional arguments");
                System.exit(1);
            }

            Integer listenPort = getPort(argsList);

            ServerSocket serverSocket = null;

            try {
                serverSocket = new ServerSocket(listenPort);
            } catch (IOException e) {
                System.out.println("Exception : " + e.getMessage());
                System.exit(3);
            }

            if (serverSocket == null) {
                System.out.println("Error : fail to establish a server socket");
                System.exit(4);
            }

            try {
                Socket connectionSocket = serverSocket.accept();
                InputStream input = connectionSocket.getInputStream();
                double startTime = System.currentTimeMillis() / 1000.0;
                long total = 0;

                while (true) {
                    int current = input.read(buffer);
                    if (current == -1) break;
                   //System.out.println("!!!" + current);
                    total += current;
                }

                double duration = System.currentTimeMillis() / 1000.0 - startTime;
                System.out.println(duration);
                System.out.println(total);

                input.close();
                serverSocket.close();
                connectionSocket.close();

                System.out.println("===== Summary ======");
                System.out.printf("received = %d KB  rate = %.3f Mbps\n", (long) (total / 1000.0), total / 1000.0 / 125.0 / duration);

            } catch (IOException e) {
                System.out.println("Exception : " + e.getMessage());
                System.exit(5);
            }

        } else {
            // Stupid Mode
            System.out.println("Error: missing or additional arguments");
            System.exit(1);
        }

    }
}
