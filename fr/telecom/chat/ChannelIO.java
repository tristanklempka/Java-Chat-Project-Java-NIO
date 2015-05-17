
package fr.telecom.chat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import org.apache.log4j.Logger;


/**
 * @author Tristan Klempka and Valentin Roussel - 2015
 *
 *	This class provide methods to simplify reading or writing operations with SocketChannel.
 *
 */
public class ChannelIO {
	public static final int BUFFER_SIZE = 1024;
	public static final int DISCONNECTED_BY_USER_CODE = -1;
	private static Logger channelIOLogger = Logger.getLogger(ChannelIO.class.getSimpleName());
	
	/**
	 * Allows the user to read data from a SocketChannel. 
	 * 
	 * Returned buffer size is limited by the constant BUFFER_SIZE (default : 1024).
	 * ByteBuffer is automatically switched to reading mode.
	 *  
	 * @param channel Channel you want to read from
	 * @return Buffer filled with data. Empty if channel is closed (End Of Stream received)
	 * @throws IOException if the reading has failed
	 */
	public static ByteBuffer readDataFromChannel(SocketChannel channel) throws IOException{
		ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);		
		int nbBytesRead = 0, b = 0;		
			do {
				try {
					b = channel.read(buffer);
				} catch (IOException e) {
					channelIOLogger.fatal("Error while reading from a channel");
					forceCloseConnection(channel);
					throw e;
				}
				nbBytesRead += b;
			} while (b > 0 && buffer.hasRemaining());
		
		if(b == DISCONNECTED_BY_USER_CODE){
			ByteBuffer disconnectedByteBuffer = ByteBuffer.allocate(0);
			forceCloseConnection(channel);
			return disconnectedByteBuffer;
		}
		buffer.flip();
		channelIOLogger.info(nbBytesRead + " bytes read from a channel");
		return buffer;
	}
	
	/**
	 * Allows the user to write data on a SocketChannel.
	 * 
	 * @param dataBuffer Buffer that will be filled with data
	 * @param channel Channel you want to write on
	 * @return Number of bytes written
	 * @throws IOException if the writing has failed 
	 */
	public static int writeDataToChannel(ByteBuffer dataBuffer, SocketChannel channel) throws IOException{	
		int nbBytesWritten = 0, b = 0;
		try {
			do {
				b = channel.write(dataBuffer);
				nbBytesWritten += b;
			} while (dataBuffer.hasRemaining() && b != 0);
			
		} catch (IOException e) {

			forceCloseConnection(channel);
			throw e;
			channelIOLogger.fatal("Error while writing to a channel");
			e.printStackTrace();
		}
		dataBuffer.clear();
		channelIOLogger.info(nbBytesWritten + " bytes written to a channel");
		return nbBytesWritten;
	}

	/**
	 * Close a SocketChannel
	 * 
	 * @param channel Channel that you want to close
	 */
	public static void forceCloseConnection(SocketChannel channel) {
		try {
			//channel's key is canceled implicitly
			channel.close(); 
			
		} catch (IOException e1) {
			channelIOLogger.fatal("Error while closing channel : " + e1);
		}
	}

	private ChannelIO() {
	}

}
