package fr.telecom.chat;

import java.util.Arrays;

import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.StatusLineManager;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.jface.window.ApplicationWindow;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.wb.swt.SWTResourceManager;


/**
 * @author Tristan Klempka and Valentin Roussel - 2015
 * 
 * This class provides an User Interface to the ClientApplication. It also listens for various clientsApplication's events
 * such as connection status, a new client connecting / disconneting from server or an incomming message. 
 *
 */
public class ClientApplicationUI extends ApplicationWindow {
	protected Text _textToSend;
	private Text _textIP;
	private Text _textPort;
	private Text _textPseudo;
	protected ClientApplicationListener _lsn;
	private ClientApplication _clientApplication;
	private boolean _isConnected;
	private List _listClientsConnected;
	private Text txtTest;
	private CTabFolder _tabFolderConversations;
	private Thread _clientApplicationThread;
	private Button _btnSend;
	private Button _btnConnection;
	/**
	 * Create the application window. Also implements methods of a ClientApp listener:
	 *  - Connected / Disconnected when the connections's status changes
	 *  - Message received when there is an incoming message from an other client
	 *  - ClientConnected / ClientDisconnected when an other client's connections's status changes
	 *  - listClientsReceived when, at connection, the clientApp receives the list of clients already connected to the server
	 */
	public ClientApplicationUI() {
		super(null);
		setShellStyle(SWT.BORDER | SWT.CLOSE);
		addToolBar(SWT.FLAT | SWT.WRAP);
		addMenuBar();
		addStatusLine();
		
		_clientApplication = new ClientApplication();
		_lsn = new ClientApplicationListener() {
			public void messageReceived(final String text, final String from) {
				Display.getDefault().asyncExec(new Runnable() {
					public void run() {
						for(CTabItem item : _tabFolderConversations.getItems()){
							if(item.getText().equals(from)){
								Text txt = (Text)(item.getControl());
								txt.append(from + ": " + text + "\n");
								return;
							}
						}
						createConversationTab(from);
						Text txt = (Text)(_tabFolderConversations.getSelection().getControl());
						txt.append(from + ": " + text + "\n");
					}
				});
			}

			public void connected() {
				Display.getDefault().asyncExec(new Runnable() {

					public void run() {
						_isConnected = true;
						ClientApplicationUI.this.setStatus("Connected");
						_btnConnection.setText("Disconnection");
						_textIP.setEnabled(false);
						_textPort.setEnabled(false);
						_textPseudo.setEnabled(false);
					}
				});
			}

			public void disconnected() {
				Display.getDefault().asyncExec(new Runnable() {
					public void run() {
						_isConnected = false;
						_clientApplication.stop();
						_clientApplicationThread.interrupt();
						ClientApplicationUI.this.setStatus("Disconnected");
						_btnConnection.setText("Connection");
						_btnSend.setEnabled(false);
						_textIP.setEnabled(true);
						_textPort.setEnabled(true);
						_textPseudo.setEnabled(true);
						_listClientsConnected.removeAll();
					}
				});
			}

			public void listClientsReceived(final String[] listClients) {
				Display.getDefault().asyncExec(new Runnable() {
					public void run() {
						for(String client : listClients) {
							if(!client.isEmpty())
								_listClientsConnected.add(client);
						}
						if(isNicknameConnected(getCurrentConversationNickname())) {
							_btnSend.setEnabled(true);
						}
					}
				});
			}

			public void clientConnected(final String nickname) {
				Display.getDefault().asyncExec(new Runnable() {
					public void run() {
						_listClientsConnected.add(nickname);
						if(getCurrentConversationNickname().equals(nickname)) {
							_btnSend.setEnabled(true);
						}
					}
				});
			}

			public void clientDisconnected(final String nickname) {
				Display.getDefault().asyncExec(new Runnable() {
					public void run() {
						_listClientsConnected.remove(nickname);
						if(getCurrentConversationNickname().equals(nickname)) {
							_btnSend.setEnabled(false);
						}
					}
				});
			}
		};
	}

	/**
	 * Create contents of the application window.
	 * @param parent
	 */
	@Override
	protected Control createContents(Composite parent) {
		setStatus("Disconnected");
		Composite container = new Composite(parent, SWT.NONE);
		container.setLayout(new FormLayout());
		
		//List of clients connected to the server, located on the right on the UI
		_listClientsConnected = new List(container, SWT.BORDER | SWT.V_SCROLL);
		FormData fd_list = new FormData();
		fd_list.bottom = new FormAttachment(100);
		fd_list.left = new FormAttachment(75);
		fd_list.top = new FormAttachment(12);
		fd_list.right = new FormAttachment(100);
		_listClientsConnected.setLayoutData(fd_list);
		_listClientsConnected.addSelectionListener(new SelectionAdapter() {
			
			// Click on a client's nickname on the list
			@Override
			public void widgetSelected(SelectionEvent e) {
				
				for (CTabItem item : _tabFolderConversations.getItems()){
					// If a tab is already open for this conversation, we switch to it
					if(item.getText().equals(_listClientsConnected.getSelection()[0])) {
						_tabFolderConversations.setSelection(item);
						return;
					}
				}
				// Else a new tab is created
				if (_listClientsConnected.getSelection().length > 0)
					createConversationTab(_listClientsConnected.getSelection()[0]);
			}
			
		});
		
		// Tab's bar, one tab = one conversation with another client
		_tabFolderConversations = new CTabFolder(container, SWT.NONE | SWT.CLOSE);
		FormData fd_tabFolder = new FormData();
		fd_tabFolder.top = new FormAttachment(12);
		fd_tabFolder.right = new FormAttachment(75);
		fd_tabFolder.left = new FormAttachment(0);
		_tabFolderConversations.setLayoutData(fd_tabFolder);
		_tabFolderConversations.addSelectionListener(new SelectionAdapter() {
			
			//When user opens an existing conversation
			@Override
			public void widgetSelected(SelectionEvent e) {
				String textItemSelected = getCurrentConversationNickname();
				java.util.List<String> listTextClientsConnected = Arrays.asList(_listClientsConnected.getItems());
				
				//If the other client is disconnected, the send button is disabled
				if (listTextClientsConnected.contains(textItemSelected)) {
					_btnSend.setEnabled(true);
				}
				else
					_btnSend.setEnabled(false);			}
		});
		

		// Editable text area used to write messages
		_textToSend = new Text(container, SWT.BORDER  | SWT.WRAP | SWT.V_SCROLL);
		_textToSend.setTextLimit(2048 - 28);
		fd_tabFolder.bottom = new FormAttachment(_textToSend);
		FormData fd__textToSend = new FormData();
		fd__textToSend.left = new FormAttachment(0);
		fd__textToSend.top = new FormAttachment(75);
		fd__textToSend.bottom = new FormAttachment(100);
		fd__textToSend.right = new FormAttachment(65);
		_textToSend.setLayoutData(fd__textToSend);
		
		//Add possibility to send messages with enter key
		_textToSend.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if(e.keyCode == 13 && _btnSend.isEnabled()) // ASCII code for enter
					_btnSend.notifyListeners(SWT.Selection, new Event());
				
			}
		});
		
		// SWT Group containing the informations needed to connect to the server (IP, Port, Nickname)
		Group grpServer = new Group(container, SWT.NONE);
		grpServer.setText("Server");
		grpServer.setLayout(new RowLayout(SWT.HORIZONTAL));
		FormData fd_grpServer = new FormData();
		fd_grpServer.bottom = new FormAttachment(_tabFolderConversations, -6);
		fd_grpServer.right = new FormAttachment(0, 455);
		fd_grpServer.top = new FormAttachment(0);
		fd_grpServer.left = new FormAttachment(0);
		grpServer.setLayoutData(fd_grpServer);
		//Add possibility to start connection with the enter key
		KeyAdapter enterKeyAdapter = new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if(e.keyCode == 13) // ASCII code for enter
					_btnConnection.notifyListeners(SWT.Selection, new Event());
				
			}
		};
		
		Label lblNewLabel = new Label(grpServer, SWT.NONE);
		lblNewLabel.setLayoutData(new RowData(50, SWT.DEFAULT));
		lblNewLabel.setText("IP Adress");
		
		_textIP = new Text(grpServer, SWT.BORDER);
		_textIP.setLayoutData(new RowData(80, SWT.DEFAULT));
		_textIP.setTextDirection(0);
		_textIP.addKeyListener(enterKeyAdapter);
		
		Label lblNewLabel_1 = new Label(grpServer, SWT.NONE);
		lblNewLabel_1.setText("Port");
		
		_textPort = new Text(grpServer, SWT.BORDER);
		_textPort.setLayoutData(new RowData(38, SWT.DEFAULT));
		_textPort.addKeyListener(enterKeyAdapter);
		
		Label lblPseudo = new Label(grpServer, SWT.NONE);
		lblPseudo.setText("Pseudo");
		
		_textPseudo = new Text(grpServer, SWT.BORDER);
		_textPseudo.setLayoutData(new RowData(60, SWT.DEFAULT));
		_textPseudo.addKeyListener(enterKeyAdapter);
		
		_btnConnection = new Button(grpServer, SWT.NONE);
		_btnConnection.setLayoutData(new RowData(100, SWT.DEFAULT));
		_btnConnection.setFont(SWTResourceManager.getFont("Segoe UI", 9, SWT.NORMAL));
		_btnConnection.setText("Connect");
		_btnConnection.addSelectionListener(new SelectionAdapter(){
			public void widgetSelected(SelectionEvent e) {
				if(!_isConnected){
					final String ip = _textIP.getText();
					final String port = _textPort.getText();
					final String nickname = _textPseudo.getText();
					// Check if connection informations provided by the user is correct
					if(
							ip.isEmpty() 
							|| port.isEmpty() 
							// Port is a number
							|| !port.matches("\\d+") 
							|| nickname.isEmpty() 
							// Nickname is between 3 and 12 characters
							|| !nickname.matches("\\p{ASCII}{3,12}")
							){
						MessageBox mb=new MessageBox(Display.getCurrent().getActiveShell(),SWT.ICON_ERROR | SWT.OK);
						mb.setText("Wrong connection informations");
						mb.setMessage("Please check IP adress, port and your pseudo");
						mb.open();
					//If everything is correct, connection is started
					}else{
						_clientApplication = new ClientApplication();
						_clientApplication.addClientApplicationListener(_lsn);
						_clientApplication.setup(ip, port, nickname);
						_clientApplicationThread = new Thread(_clientApplication);
						_clientApplicationThread.start();
					}
				}
				else if(_isConnected){
					_clientApplication.sendDisconnectionMessage();
				}
			}
		});
		
		// Simple button to send messages
		_btnSend = new Button(container, SWT.NONE);
		_btnSend.setEnabled(false);
		_btnSend.setSelection(true);
		FormData fd_btnSend = new FormData();
		fd_btnSend.right = new FormAttachment(75);
		fd_btnSend.bottom = new FormAttachment(100);
		fd_btnSend.top = new FormAttachment(75);
		fd_btnSend.left = new FormAttachment(65);
		_btnSend.setLayoutData(fd_btnSend);
		_btnSend.setText("Send");
		
		//Send the message and add it to the conversation's text area
		_btnSend.addSelectionListener(new SelectionAdapter(){
			public void widgetSelected(SelectionEvent e) {
				if(_tabFolderConversations.getSelection() == null)
					return;
				_clientApplication.sendStandardMessage(_textToSend.getText(), _tabFolderConversations.getSelection().getText());
				
				Text txt = (Text)(_tabFolderConversations.getSelection().getControl());
				txt.append(_textPseudo.getText() + ": " + _textToSend.getText() + "\n");
				
				_textToSend.setText("");
		}
		});

		return container;
	}

	/**
	 * Creates a new tab for a new conversation
	 * 
	 * @param nickname String containing the nickname of the other client
	 */
	private void createConversationTab(String nickname) {
		CTabItem tabItem = new CTabItem(_tabFolderConversations, SWT.NONE);
		tabItem.setText(nickname);
		txtTest = new Text(_tabFolderConversations, SWT.READ_ONLY | SWT.WRAP | SWT.V_SCROLL);
		//txtTest.setEditable(false);
		txtTest.setBackground(new Color(Display.getCurrent(), 255, 255, 255));
		tabItem.setControl(txtTest);
		
		_tabFolderConversations.setSelection(tabItem);
		_btnSend.setEnabled(true);
	}

	/**
	 * Create the menu manager.
	 * @return the menu manager
	 */
	@Override
	protected MenuManager createMenuManager() {
		MenuManager menuManager = new MenuManager("menu");
		return menuManager;
	}

	/**
	 * Create the toolbar manager.
	 * @return the toolbar manager
	 */
	@Override
	protected ToolBarManager createToolBarManager(int style) {
		ToolBarManager toolBarManager = new ToolBarManager(SWT.NONE);
		return toolBarManager;
	}

	/**
	 * Create the status line manager.
	 * @return the status line manager
	 */
	@Override
	protected StatusLineManager createStatusLineManager() {
		StatusLineManager statusLineManager = new StatusLineManager();
		return statusLineManager;
	}

	/**
	 * Launch the application.
	 * @param args
	 */
	public static void main(String args[]) {
		try {
			ClientApplicationUI window = new ClientApplicationUI();
			window.setBlockOnOpen(true);
			window.open();
			Display.getCurrent().dispose();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Configure the shell in order to interrupts the clientApplication's thread if the shell (the window) is closed
	 * @param newShell
	 */
	@Override
	protected void configureShell(Shell newShell) {
		newShell.addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent e) {
				if(_clientApplicationThread != null)
					_clientApplicationThread.interrupt();
			}
		});
		super.configureShell(newShell);
		newShell.setText("Chat");
	}

	/**
	 * Return the initial size of the window.
	 */
	@Override
	protected Point getInitialSize() {
		return new Point(700, 500);
	}
	
	/**
	 * Check if a client is connected with his nickname
	 * 
	 * @param client String containing the nickname of the client
	 * @return true if this client is connected
	 */
	private boolean isNicknameConnected(String nickname) {
		return Arrays.asList(_listClientsConnected.getItems()).contains(nickname);
	}
	
	/**
	 * Get the client's nickname of the current conversation
	 * 
	 * @return the name (nickname of the client) of the current conversation
	 */
	private String getCurrentConversationNickname() {
		CTabItem selection = _tabFolderConversations.getSelection();
		if(selection != null) {
			return selection.getText();
		}
		else
			return "";
	}
}
