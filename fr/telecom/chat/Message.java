package fr.telecom.chat;

import java.nio.ByteBuffer;

/**
 * @author Tristan Klempka and Valentin Roussel - 2015
 * 
 * Class that represent our new communication protocol called "MESSAGE".
 * Structure of the protocol : [size (2 bytes), type (2 bytes), sender (12 bytes), receiver (12 bytes), content (1024 - 28 = 996 bytes)]
 *
 */
public class Message {

	public enum Type {
		CONNECTION,
		DISCONNECTION,
		STANDARD,
		CLIENT_CONNECTED,
		CLIENT_DISCONNECTED,
		BAD_MESSAGE
	}

	/**
	 * Convert Message type to short 
	 *	 
	 */
	private static short MessageTypetoShort(Type type) {
		switch(type){
			case CONNECTION:
			return 1;
			case DISCONNECTION:
			return 2;
			case STANDARD :
			return 3;
			case CLIENT_CONNECTED :
			return 4;
			case CLIENT_DISCONNECTED :
			return 5;
			case BAD_MESSAGE:
			default:
			return 99;
		}
	}
	
	/**
	 * Convert short to Message type 
	 *	 
	 */
	private static Type ShortToTypeMessage(short nb) {
		switch(nb) {
			case 1:
			return Type.CONNECTION;
			case 2:
			return Type.DISCONNECTION;
			case 3 :
			return Type.STANDARD;
			case 4 :
			return Type.CLIENT_CONNECTED;
			case 5 :
			return Type.CLIENT_DISCONNECTED;
			case 99:
			default:
			return Type.BAD_MESSAGE;
		}
	}
	
	private static final int SHORT_SIZE = 2;
	private static final int HEADER_SIZE = 28;
	private static final int HEADER_NICKNAME_SIZE = 12;
	private String content;
	private String from;
	private String to;
	private short size;
	private short type;
	private boolean isValid = false;
	
	/**
	 * Allows user to construct a ready to be sent ByteBuffer from Message
	 * 
	 * @return ByteBuffer representing the message
	 */
	public ByteBuffer constructByteBuffer() {
		ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE);
		header.putShort(size);
		header.putShort(type);
		header.put(from.getBytes());
		header.put(to.getBytes());
		header.flip();
		ByteBuffer bufferContent = ByteBuffer.wrap(content.getBytes());
		ByteBuffer msg = ByteBuffer.allocate(header.capacity() + bufferContent.capacity()).put(header).put(bufferContent);
		msg.flip();
		return msg;
	}
	
	/**
	 * Allows user to construct a Message directly from a ByteBuffer. This method verifies if the ByteBuffer contains a valid message. If not, it rewinds to allow data to be treated after.
	 * 
	 * @param buffer ByteBuffer containing data to construct the Message
	 * @return true if the Message constructed is valid
	 */
	
	public boolean ConstructFromByteBuffer(ByteBuffer buffer) {
		if(buffer.remaining() < HEADER_SIZE) {
			isValid = false;
			return isValid;
		}	
		this.size = buffer.getShort();
		if (buffer.remaining() < size - SHORT_SIZE) {
			buffer.rewind();
			isValid = false;
			return isValid;
		}
		this.type = buffer.getShort();
		byte[] byteArrayFrom = new byte[HEADER_NICKNAME_SIZE];
		buffer.get(byteArrayFrom, 0, HEADER_NICKNAME_SIZE);
		this.from = new String(byteArrayFrom);
		byte[] byteArrayTo = new byte[HEADER_NICKNAME_SIZE];
		buffer.get(byteArrayTo, 0, HEADER_NICKNAME_SIZE);
		this.to = new String(byteArrayTo);
		byte[] byteArrayContent = new byte[this.size - HEADER_SIZE];
		buffer.get(byteArrayContent, 0, this.size - HEADER_SIZE);
		this.content = new String(byteArrayContent);
		isValid = true;
		return isValid;
		
	}
	
	/**
	 *  Simple constructor. Message is always non-valid
	 */
	public Message() {
		isValid = false;
	}
	
	/**
	 * Constructor to create a full valid Message
	 * 
	 * @param messageContent 
	 * @param from
	 * @param to
	 * @param type
	 */
	public Message(String messageContent, String from, String to, Message.Type type) {
		this.content = messageContent.substring(0, 996);
		this.from = formatToHeaderString(from);
		this.to = formatToHeaderString(to);
		this.type = MessageTypetoShort(type);
		size = (short)(HEADER_SIZE + messageContent.length()); 
		isValid = true;
		
	}

	/**
	 * Format a string to header format
	 * 
	 * @param str String to be formated
	 * @return string with correct format for Message header 
	 */
	private String formatToHeaderString(String str) {
		int nbMissingCharacters = HEADER_NICKNAME_SIZE - str.length();
		if(str.length() < HEADER_NICKNAME_SIZE) 
			for(int i = 0; i < nbMissingCharacters; i++)
				str += '\0';
		return str.substring(0, 12);
	}
	
	/**
	 * @param str String to be formated
	 * @return string that can be read by humans. Example : [Valentin   ] => [Valentin] 
	 */
	private String formatToReadableString(String str) {
		return str.replace("\0", "");
	}
	
	/**
	 * Get Message content
	 * 
	 * @return Message content
	 */
	public String getContent() {
		return content;
	}
	/**
	 * Get Message sender
	 * 
	 * @return Message sender
	 */
	public String getFrom() {
		return formatToReadableString(from);
	}
	/**
	 * Get Message receiver
	 * 
	 * @return Message receiver
	 */
	public String getTo() {
		return formatToReadableString(to);
	}
	/**
	 * Get Message type
	 * 
	 * @return Message type
	 */
	public Type getType() {
		return ShortToTypeMessage(type);
	}
	/**
	 * Message is valid ?
	 * 
	 * @return true if Message is valid
	 */
	public boolean isValid() {
		return isValid;
	}
	
	/**
	 * Set new receiver
	 * 
	 * @param to New receiver
	 */
	public void setTo(String to) {
		this.to = formatToHeaderString(to);
	}
	/**
	 * Set new receiver
	 * 
	 * @param from New sender
	 */
	public void setFrom(String from) {
		this.from = formatToHeaderString(from); 
	}
	/**
	 * Set new type
	 * 
	 * @param type New Message type
	 */
	public void setType(Type type) {
		this.type = MessageTypetoShort(type);
	}
	
	@Override
	public String toString() {
		return "[" + this.size + ", " + this.type + ", " + this.from + ", " + this.to + ", " + this.content + "]";
	}
	
}
