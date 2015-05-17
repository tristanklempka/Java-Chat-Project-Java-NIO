package fr.telecom.chat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.apache.log4j.Logger;

import fr.telecom.chat.ChannelIO;

/**
 * @author Tristan Klempka and Valentin Roussel - 2015
 * 
 * This class implements the client side of the chat. 
 * It connects with the server and allows to communicate with it by using the Message protocol.
 * 
 * It implements Runnable because it is a service started by the UI
 *
 */
public class ClientApplication implements MessageAnalyzer, Runnable{
	/* Constantes */
	private static final int MAX_MESSAGE_SIZE = ChannelIO.BUFFER_SIZE;
	/* Attributs */
	private SocketChannel _socket;
	private Selector _selector;
	private SelectionKey _key;
	private String _nickname;
	private ByteBuffer _pendingWritingData = ByteBuffer.allocate(2048);
	boolean _pendingWritingDataHasEnoughSpace = true;
	private ByteBuffer _pendingReadingData = ByteBuffer.allocate(2048);
	private Set<String> _clientsConnected = new HashSet<String>();
	private Logger clientLogger;
	
	protected ClientApplicationListener _lsn;
	
	/**
	 * Add ClientApplicationListener
	 * 
	 * @param lsn ClientApplicationListener to be added
	 */
	public void addClientApplicationListener(ClientApplicationListener lsn){
		if(_lsn == null)
			_lsn = lsn;
	}
	
	/**
	 * Remove ClientApplicationListener
	 * 
	 * @param lsn ClientApplicationListener to be removed
	 */
	public void removeClientApplicationListener(ClientApplicationListener lsn){
		if(_lsn == null)
			_lsn = null;
	}
	
	/**
	 *  Setup the client. 
	 *  Open the SocketChannel, set non-blocking mode and register the Selector. 
	 * 
	 *  ServerSocket is set up to listen for connections (OP_CONNECT).

	 * @param iPAdress String containing the IP address
	 * @param port String containing the port
	 * @param nickname String containing the client nickname
	 */
	public void setup(String iPAdress, String port, String nickname) {
		clientLogger = Logger.getLogger(ClientApplication.class.getSimpleName() + "." + nickname);
		try {
			clientLogger.info("Client setup starting");
			_nickname = nickname;
			_socket = SocketChannel.open();
			_selector = Selector.open();
			_socket.configureBlocking(false);
			_socket.connect(new InetSocketAddress(iPAdress, Integer.parseInt(port)));
			_key = _socket.register(_selector, SelectionKey.OP_CONNECT);
			
		} catch (IOException e) {
			clientLogger.fatal("Client setup failed");
		}
		clientLogger.info("Client launched");
	}
	/**
	 * Run the client until thread is interrupted by UI.
	 * Loop start by processing the pending reading data if any. 
	 * After that it iterates over all the SelectionKey to check if events are pending (Connectable, Readable or Writable). Only keys that have event are checked.
	 * Finally low level operations are made : if key is connectable, client finishes connection, if key is readable, client reads and adds pending reading data, if key is writable, 
	 * client write its pending writing data.
	 * 
	 * It is important to notice that we can not write or read directly from channel that is why pending data are used.
	 */
	public void run() {
		try {
			while(!Thread.currentThread().isInterrupted()){				
				if(_key.isValid());
					processPendingReadingData(_key);

				int readyChannels = _selector.select();
				if(readyChannels == 0) continue;
				
				
				Set<SelectionKey> selectedKeys = _selector.selectedKeys();
				Iterator<SelectionKey> keyIterator = selectedKeys.iterator();
				while(keyIterator.hasNext()) {

					SelectionKey key = keyIterator.next();
					
				    if(key.isValid() && key.isConnectable() ) {
				        // a connection was accepted by a ServerSocketChannel.
				    	connectClient();
				    }
				    else if (key.isValid() && key.isReadable()) {
				        // a channel is ready for reading
				    	SocketChannel channel = (SocketChannel)key.channel();
				    	ByteBuffer dataToBeRead = ChannelIO.readDataFromChannel(channel);
				    	if(dataToBeRead.capacity() == 0) {
							clientLogger.fatal("Unexpected disconnection from server");
				    		ChannelIO.forceCloseConnection((SocketChannel) key.channel());
				    	}
				    	addPendingReadingData(dataToBeRead);
				    }				    
				    else if (key.isValid() && key.isWritable()) {
				    	// a channel is ready for writing
				    	_pendingWritingData.flip();
				    	int dataRemaining =  _pendingWritingData.remaining();
				    	int nbBytesWritten = 
				    			ChannelIO.writeDataToChannel(_pendingWritingData, (SocketChannel) key.channel());
				    	
		    			if(nbBytesWritten == dataRemaining) {
				    		key.interestOps(SelectionKey.OP_READ);
				    		_pendingWritingData.clear();
		    			}
		    			else
		    				_pendingWritingData.compact();
		    			
		    			if(_pendingWritingData.remaining() >= 1024) {
				    		_pendingWritingDataHasEnoughSpace= true;
		    			}
		    			
				    }
				    keyIterator.remove();
				}
			}
		} catch (IOException e) {
			clientLogger.fatal("Connection with the server lost");
		}
		
	}
	/**
	 * Connect the client on the server.
	 * It finishes its connection if any. Then send an acknowledgment to the server and set up its key in reading mode.
	 * 
	 */
	private void connectClient() {
		clientLogger.info("Trying to connect to server");
		if(_socket.isConnectionPending()){
			try {
				if(_socket.finishConnect()){
					_key.interestOps(SelectionKey.OP_READ);
					clientLogger.info("Connected to server");
					sendConnectionMessage();
				}
			} catch (IOException e) {
				clientLogger.info("Unable to connect to server");
			}
		}
	}
	
	/**
	 * Send connection acknowledgment message to the server.
	 */
	private void sendConnectionMessage() {		
		Message msg = new Message("", _nickname, "Server", Message.Type.CONNECTION);
		addToPendingWritingData(msg.constructByteBuffer());
		_key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
		clientLogger.info("Connection message sent to server");
	}
	
	/**
	 * Send disconnection message to the server.
	 */
	public void sendDisconnectionMessage(){
		Message msg = new Message("", _nickname, "Server", Message.Type.DISCONNECTION);
		addToPendingWritingData(msg.constructByteBuffer());
		_key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
		clientLogger.info("Disconnection message sent to server");
		_selector.wakeup();
	}
	
	/**
	 * Send a message through the server at a user of the chat.
	 * 
	 * @param str String containing the message content
	 * @param toString containing the nickname of the receiver
	 */
	public void sendStandardMessage(String str, String to) {
		Message msg = new Message(str, _nickname, to, Message.Type.STANDARD);
		addToPendingWritingData(msg.constructByteBuffer());
		_key.interestOps(SelectionKey.OP_WRITE);
		clientLogger.info("Message sent to " + to + " : " + str);
		_selector.wakeup();
	}
	
	/**
	 * Method to analyze message and take actions from it. This method implements how to deal with Messages received from server.
	 * Client connected : Register nickname of the client, fire client connected event to the UI
	 * Client disconnected : Unregister nickname of the client, fire client disconnected event to the UI
	 * Connection : Fire connection event to the UI, fetch clients already connected list, fire list received event to UI 
	 * Standard : Fire message received event to UI
	 * 
	 * @param msg Message to be read
	 * @param key SelectionKey that contains the sender channel
	 */
	public void performActionFromReceivedMessage(Message msg, SelectionKey key) {
		switch(msg.getType()) {
		case CLIENT_CONNECTED:
			_clientsConnected.add(msg.getFrom());
			_lsn.clientConnected(msg.getFrom());
			clientLogger.info(msg.getFrom() + " is connected");
			break;
		case CLIENT_DISCONNECTED:
			_clientsConnected.remove(msg.getFrom());
			_lsn.clientDisconnected(msg.getFrom());
			clientLogger.info(msg.getFrom() + " is disconnected");
			break;
		case CONNECTION:
			_lsn.connected();
			String[] connectedClients = msg.getContent().split(",");
			for(String nickname : connectedClients) {
				_clientsConnected.add(nickname);
			}
			_lsn.listClientsReceived(connectedClients);
			clientLogger.info("Client registered to server");
			break;
		case DISCONNECTION:
			_lsn.disconnected();
			break;
		case STANDARD:
			_lsn.messageReceived(msg.getContent(), msg.getFrom());
			clientLogger.info("Message received from " + msg.getFrom() + " :" + msg.getContent());
			break;
		case BAD_MESSAGE:
		default:
			break;	
		}
	}

	/**
	 * Add data to the pending writing buffer.
	 * 
	 * SelectionKey is set to write mode if the buffer is full. 
	 * It allows to free the buffer by actually write data on the ChannelSocket during the server loop.
	 * 
	 * 
	 * @param buffer ByteBuffer to be added
	 */
	private void addToPendingWritingData(ByteBuffer buffer){
		_pendingWritingData.put(buffer);
		_key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
		if (_pendingWritingData.remaining() < MAX_MESSAGE_SIZE) {
			_pendingWritingDataHasEnoughSpace = false;
			_key.interestOps(SelectionKey.OP_WRITE);
		}
	}
	
	/**
	 * Add data to the pending reading buffer.
	 * 
	 * SelectionKey is set to write mode if the buffer is full. 
	 * It allows to free the buffer by actually write data on the ChannelSocket during the server loop.
	 * 
	 * @param buffer ByteBuffer to be added
	 */
	
	private void addPendingReadingData(ByteBuffer buffer){
		_pendingReadingData.put(buffer);
		if (_pendingReadingData.remaining() < MAX_MESSAGE_SIZE) {
			_key.interestOps(SelectionKey.OP_WRITE);
		}
	}
	
	/**
	 * Process pending reading data.
	 * 
	 * This methods tries to construct messages from the pending data.
	 * If the message constructed is valid, it also called performActionFromReceivedMessage automatically.
	 * 
	 * @param key SelectionKey
	 */
	private void processPendingReadingData(SelectionKey key) {
		boolean msgValid = true;
		do {
			Message newMsg = new Message();
			_pendingReadingData.flip();
			newMsg.ConstructFromByteBuffer(_pendingReadingData);
			_pendingReadingData.compact();

			if (newMsg.isValid()) {
				performActionFromReceivedMessage(newMsg, key);
			} else {
				msgValid = false;
			}
		} while (_pendingReadingData.hasRemaining() && msgValid && _pendingWritingDataHasEnoughSpace);
	}
	
	/**
	 *  Simple method to stop the server
	 */
	public void stop(){
		try {
			clientLogger.info("Client Application stopped");
			if(_socket.isOpen()){
				_socket.close();
			}
				
		} catch (IOException e) {
			clientLogger.fatal(e);
		}
	}
}
