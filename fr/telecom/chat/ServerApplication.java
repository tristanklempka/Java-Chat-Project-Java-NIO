package fr.telecom.chat;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;

import fr.telecom.chat.ChannelIO;

/**
 * @author Tristan Klempka and Valentin Roussel - 2015
 * 
 * This class implements the server side of the chat. 
 * It handles clients connections and allows them to communicate using the Message protocol.
 * Server is running on a single Thread.
 *
 */
public class ServerApplication implements MessageAnalyzer{
	private static final int MAX_MESSAGE_SIZE = ChannelIO.BUFFER_SIZE;
	private static final int MAX_PENDING_DATA_SIZE = 2048;
	/* Constantes */
	private static final int PORT = 1234;
	/* Attributs */
	private ServerSocketChannel _serverSocket;
	private Selector _selector;
	private Map<String, SelectionKey> mapNicknameKey = new HashMap<String, SelectionKey>();
	private Map<SelectionKey, ByteBuffer> pendingWritingData = new HashMap<SelectionKey, ByteBuffer>();
	private boolean pendingWritingDataHasEnoughSpace = true;
	private Map<SelectionKey, ByteBuffer> pendingReadingData = new HashMap<SelectionKey, ByteBuffer>();
	private static Logger serverLogger = Logger.getLogger(ServerApplication.class.getSimpleName());

	

	/**
	 *  Setup the server. 
	 *  Open the ServerSocket, set non-blocking mode and register the Selector. 
	 *  Port is by default 1234. 
	 *  ServerSocket is set up to listen for incoming connections (OP_ACCEPT).
	 */
	public void setup(){
		serverLogger.info("Server setup starting");
		try {		
			_serverSocket = ServerSocketChannel.open();
			_selector = Selector.open();
			_serverSocket.configureBlocking(false);
			_serverSocket.socket().bind(new InetSocketAddress(PORT));
			_serverSocket.register(_selector, SelectionKey.OP_ACCEPT);

		} catch (IOException e) {
			serverLogger.fatal("Server setup failed");
		}
		serverLogger.info("Server launched");
	}

	/**
	 * Run the server.
	 * Loop start by processing the pending reading data if any. 
	 * After that it iterates over all the SelectionKey to check if events are pending (Acceptable, Readable or Writable). Only keys that have event are checked.
	 * Finally low level operations are made : if key is acceptable, server connects client, if key is readable, server reads it and adds pending reading data to the client, if key is writable, 
	 * server writes its pending writing data to the client socket.
	 * 
	 * It is important to notice that we can not write or read directly from channel that is why pending data are used.
	 */
	public void run(){

		try {			
			while(true){
				for(SelectionKey key : pendingReadingData.keySet()) {
					if(key.isValid())
						processPendingReadingData(key);
				}
				
				int readyChannels = _selector.select(); // wait for connection
				if(readyChannels == 0) continue;

				Set<SelectionKey> selectedKeys = _selector.selectedKeys();
				Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

				while(keyIterator.hasNext()) {
					SelectionKey key = keyIterator.next();
					if(key.isAcceptable()) {
						serverLogger.info("New incomming connection");
						acceptClient();

					} else {
						if (key.isReadable()) {
							// a channel is ready for reading
							SocketChannel channel = (SocketChannel)key.channel();
							try {
								ByteBuffer dataToBeRead = ChannelIO.readDataFromChannel(channel);
								addPendingReadingData(key, dataToBeRead);
							} catch (IOException e) {
								String clientDisconnected = getNicknameWithKey(mapNicknameKey, key);
								mapNicknameKey.remove(clientDisconnected);
								pendingWritingData.remove(key);
								pendingReadingData.remove(key);
								serverLogger.warn("Unexpected disconnection from client " + clientDisconnected);
								broadcast(new Message("", clientDisconnected, "Broadcast", Message.Type.CLIENT_DISCONNECTED), key);
							}
													
						} else if (key.isWritable()) {
							// a channel is ready for writing
							SocketChannel channel = (SocketChannel) key.channel();
							ByteBuffer dataToBeWritten = pendingWritingData.get(key);
							dataToBeWritten.flip();
							int dataRemaining =  dataToBeWritten.remaining();
							int nbBytesWritten = 
									ChannelIO.writeDataToChannel(dataToBeWritten, channel);

							if(nbBytesWritten == dataRemaining) {
								key.interestOps(SelectionKey.OP_READ);
								dataToBeWritten.clear();
							}
							else
								dataToBeWritten.compact();
						}
					}
						keyIterator.remove();
				}
			}
		} catch (Exception e) {
			serverLogger.fatal("Error has occured, server will shut down");
			return;
		}
	}

	/**
	 * Process pending reading data of a client.
	 * 
	 * This methods tries to construct messages from the pending data of the client.
	 * If the message constructed is valid, it also called performActionFromReceivedMessage automatically.
	 * 
	 * @param key Selection key of the client
	 */
	private void processPendingReadingData(SelectionKey key) {
		ByteBuffer pendingData = pendingReadingData.get(key);

		boolean msgValid = true;
		do {
			Message newMsg = new Message();
			pendingData.flip();
			newMsg.ConstructFromByteBuffer(pendingData);
			pendingData.compact();

			if (newMsg.isValid()) {
				performActionFromReceivedMessage(newMsg, key);
			} else {
				msgValid = false;
			}
		} while (pendingData.hasRemaining() && msgValid && pendingWritingDataHasEnoughSpace);
	}

	/**
	 *  Simple method to stop the server
	 */
	public void stop(){
		try {

			serverLogger.info("Server stopped");
			_serverSocket.close();

		} catch (IOException e) {
			serverLogger.fatal(e);
		}
	}

	/**
	 * Method to analyze message and take actions from it. This method implements how to deal with Messages received from clients.
	 * Disconnection : Send acknowledgment, remove client from server, broadcast that a client has disconnected.
	 * Connection : Register nickname of client in the server, broadcast that a client has connected, send to the new client a list containing clients already connected.
	 * Standard : Send message to the corresponding receiver .
	 * 
	 * @param msg Message to be read
	 * @param key SelectionKey that contains the sender channel
	 */
	public void performActionFromReceivedMessage(Message msg, SelectionKey key) {
		switch(msg.getType()) {
			case DISCONNECTION:
				//Send acknowledgement for the disconnection message from the client
				serverLogger.info("Client " + msg.getFrom() + " is disconnected");
				addPendingWritingData(key, new Message("","Server",msg.getFrom(), Message.Type.DISCONNECTION).constructByteBuffer());
				mapNicknameKey.remove(msg.getFrom());
				msg.setType(Message.Type.CLIENT_DISCONNECTED);
				broadcast(msg, key);
				break;
			//Connection message from new client, allow server to get his nickname
			case CONNECTION:
				//Nickname is already used
				if(mapNicknameKey.get(msg.getFrom()) != null) {
						ChannelIO.forceCloseConnection((SocketChannel)key.channel());
						serverLogger.info("Client " + msg.getFrom() + "has tried to connect but this nickname is already used");
				}
				else {
					mapNicknameKey.put(msg.getFrom(), key);
					msg.setType(Message.Type.CLIENT_CONNECTED);
					broadcast(msg, key);
					
					List<String> nicknameList= new ArrayList<String>();
					for (String nickname : mapNicknameKey.keySet()) {
						if (!nickname.equals(msg.getFrom()))
							nicknameList.add(nickname);				
					}
					String listConnectedClients = String.join(",", nicknameList);
					Message listConnectedClientsMessage = new Message(listConnectedClients, "Server", msg.getFrom(), Message.Type.CONNECTION);
					addPendingWritingData(key, listConnectedClientsMessage.constructByteBuffer());
					serverLogger.info("Client " + msg.getFrom() + " is registered to the Server");
				}
				break;
			case STANDARD:
				SelectionKey keyTo = mapNicknameKey.get(msg.getTo());
				if(mapNicknameKey.values().contains(keyTo))
					addPendingWritingData(keyTo, msg.constructByteBuffer());
					serverLogger.info("Message from " + msg.getFrom() + " to " + msg.getTo() + " : " + msg.getContent());
				break;
			case BAD_MESSAGE:
			default:
				serverLogger.warn("Non readable message received from " + msg.getFrom());
				break;	
		}

	}

	/**
	 * Accept a client on the server.
	 * 
	 * Firstly it accept and create a new socket that is set up in non-blocking mode.
	 * A SelectionKey is also registered.
	 * Client buffers are allocated.
	 * 
	 * @throws IOException if connection has failed
	 * @throws ClosedChannelException if channel is closed
	 */
	private void acceptClient() throws IOException, ClosedChannelException {
		SocketChannel socket = _serverSocket.accept();
		socket.configureBlocking(false);
		SelectionKey clientKey = socket.register(_selector, SelectionKey.OP_READ);	
		pendingWritingData.put(clientKey,ByteBuffer.allocate(MAX_PENDING_DATA_SIZE));
		pendingReadingData.put(clientKey,ByteBuffer.allocate(MAX_PENDING_DATA_SIZE));
		serverLogger.info("Incomming connection accepted");
	}

	/**
	 * Send a Message to all the connected clients excepted to the sender.
	 * 
	 * @param msg Message to be broadcasted
	 * @param key SelectionKey of the sender
	 */
	private void broadcast(Message msg, SelectionKey key) {
		for (String nickname : mapNicknameKey.keySet()) {
			SelectionKey selectionKey = mapNicknameKey.get(nickname);
			if (selectionKey.isValid() && key != selectionKey) {
				msg.setTo(nickname);
				addPendingWritingData(selectionKey, msg.constructByteBuffer());
			}
		}
	}

	/**
	 * Add data to the pending writing buffer of a client.
	 * 
	 * SelectionKey is set to write mode if the buffer is full. 
	 * It allows to free the buffer by actually write data on the ChannelSocket during the server loop.
	 * 
	 * @param key SelectionKey of the client
	 * @param buffer ByteBuffer to be added
	 */
	private void addPendingWritingData(SelectionKey key, ByteBuffer buffer){
		pendingWritingData.get(key).put(buffer);
		key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
		if (pendingWritingData.get(key).remaining() < MAX_MESSAGE_SIZE) {
			pendingWritingDataHasEnoughSpace = false;
			key.interestOps(SelectionKey.OP_WRITE);
		}
	}
	
	/**
	 * Add data to the pending reading buffer of a client.
	 * 
	 * SelectionKey is set to read mode if the buffer is full. 
	 * It blocks key from reading data.
	 * 
	 * @param key SelectionKey of the client
	 * @param buffer ByteBuffer to be added
	 */
	private void addPendingReadingData(SelectionKey key, ByteBuffer buffer){
		pendingReadingData.get(key).put(buffer);
		if (pendingReadingData.get(key).remaining() < MAX_MESSAGE_SIZE) {
			key.interestOps(SelectionKey.OP_WRITE);
		}
	}
	
	/**
	 * Allows user to get a nickname from a SelectionKey
	 * 
	 * @param map Map to search in
	 * @param value SelectionKey of the researched nickname
	 * @return string containing nickname associated with SelectionKey
	 */
	private String getNicknameWithKey(
			Map<String, SelectionKey> map, SelectionKey value) {
        String nickname = "";
		for(Map.Entry<String,SelectionKey> entry: map.entrySet()){
            if(value.equals(entry.getValue())){
                nickname = entry.getKey();
            }
        }
		return nickname;
	}
	
	/**
	 * Launch application
	 * 
	 * @param args
	 */
	static public void main(String args[]){
		ServerApplication server = new ServerApplication();
		server.setup();		
		server.run();
	}
}
