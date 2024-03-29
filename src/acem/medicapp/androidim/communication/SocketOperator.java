package acem.medicapp.androidim.communication;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;

import acem.medicapp.androidim.interfaces.IAppManager;
import acem.medicapp.androidim.interfaces.ISocketOperator;
import android.util.Log;

public class SocketOperator implements ISocketOperator {
	// private static final String AUTHENTICATION_SERVER_ADDRESS =
	// "http://192.168.7.5/android-im/"; //TODO change to your WebAPI Address
	//private static final String AUTHENTICATION_SERVER_ADDRESS = "http://10.0.2.2:80/android-im/";
	private static final String AUTHENTICATION_SERVER_ADDRESS = "http://192.168.0.101:80/android-im/"; // TODO
	
	private int listeningPort = 0;

	private static final String HTTP_REQUEST_FAILED = null;

	private final HashMap<InetAddress, Socket> sockets = new HashMap<InetAddress, Socket>();

	private ServerSocket serverSocket = null;

	private boolean listening;

	private IAppManager appManager;

	private class ReceiveConnection extends Thread {
		Socket clientSocket = null;

		public ReceiveConnection(Socket socket) {
			this.clientSocket = socket;
			SocketOperator.this.sockets.put(socket.getInetAddress(), socket);
		}

		@Override
		public void run() {
			try {
				// PrintWriter out = new
				// PrintWriter(clientSocket.getOutputStream(), true);
				BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
				String inputLine;

				while ((inputLine = in.readLine()) != null) {
					if (inputLine.equals("exit") == false) {
						// appManager.messageReceived(inputLine);
					} else {
						clientSocket.shutdownInput();
						clientSocket.shutdownOutput();
						clientSocket.close();
						SocketOperator.this.sockets.remove(clientSocket.getInetAddress());
					}
				}

			} catch (IOException e) {
				Log.e("ReceiveConnection.run: when receiving connection ", "");
			}
		}
	}

	public SocketOperator(IAppManager appManager) {
		this.appManager = appManager;
	}

	@Override
	public String sendHttpRequest(String params) {
		URL url;
		String result = new String();
		try {
			url = new URL(AUTHENTICATION_SERVER_ADDRESS);
			HttpURLConnection connection;
			connection = (HttpURLConnection) url.openConnection();
			connection.setDoOutput(true);

			PrintWriter out = new PrintWriter(connection.getOutputStream());

			out.println(params);
			out.close();

			BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			String inputLine;

			while ((inputLine = in.readLine()) != null) {
				result = result.concat(inputLine);
			}
			in.close();
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		if (result.length() == 0) {
			result = HTTP_REQUEST_FAILED;
		}

		return result;

	}

	@Override
	public int startListening(int portNo) {
		listening = true;

		try {
			serverSocket = new ServerSocket(portNo);
			this.listeningPort = portNo;
		} catch (IOException e) {

			// e.printStackTrace();
			this.listeningPort = 0;
			return 0;
		}

		while (listening) {
			try {
				new ReceiveConnection(serverSocket.accept()).start();

			} catch (IOException e) {
				// e.printStackTrace();
				return 2;
			}
		}

		try {
			serverSocket.close();
		} catch (IOException e) {
			Log.e("Exception server socket", "Exception when closing server socket");
			return 3;
		}

		return 1;
	}

	@Override
	public void stopListening() {
		this.listening = false;
	}

	private Socket getSocket(InetAddress addr, int portNo) {
		Socket socket = null;
		if (sockets.containsKey(addr) == true) {
			socket = sockets.get(addr);
			// check the status of the socket
			if (socket.isConnected() == false || socket.isInputShutdown() == true || socket.isOutputShutdown() == true
					|| socket.getPort() != portNo) {
				// if socket is not suitable, then create a new socket
				sockets.remove(addr);
				try {
					socket.shutdownInput();
					socket.shutdownOutput();
					socket.close();
					socket = new Socket(addr, portNo);
					sockets.put(addr, socket);
				} catch (IOException e) {
					Log.e("getSocket: when closing and removing", "");
				}
			}
		} else {
			try {
				socket = new Socket(addr, portNo);
				sockets.put(addr, socket);
			} catch (IOException e) {
				Log.e("getSocket: when creating", "");
			}
		}
		return socket;
	}

	@Override
	public void exit() {
		for (Iterator<Socket> iterator = sockets.values().iterator(); iterator.hasNext();) {
			Socket socket = iterator.next();
			try {
				socket.shutdownInput();
				socket.shutdownOutput();
				socket.close();
			} catch (IOException e) {
			}
		}

		sockets.clear();
		this.stopListening();
		appManager = null;
		// timer.cancel();
	}

	@Override
	public int getListeningPort() {

		return this.listeningPort;
	}

}
