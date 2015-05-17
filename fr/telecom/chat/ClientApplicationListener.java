package fr.telecom.chat;

/**
 * @author Tristan Klempka and Valentin Roussel - 2015
 * 
 * Interface that provides methods implementing actions on event
 *
 */
public interface ClientApplicationListener {
		public abstract void messageReceived(String text, String to);
		public abstract void connected();
		public abstract void disconnected();
		public abstract void listClientsReceived(String[] listClients);
		public abstract void clientConnected(String nickname);
		public abstract void clientDisconnected(String nickname);
}
