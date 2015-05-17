package fr.telecom.chat;

import java.nio.channels.SelectionKey;

/**
 * @author Tristan Klempka and Valentin Roussel - 2015
 * 
 * Simple interface that provides one method to allows implementing classes to read and take actions from messages
 *
 */
public interface MessageAnalyzer {
	public abstract void performActionFromReceivedMessage(Message msg, SelectionKey key);

}
