package Conexion;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class Cliente {
	private static final String HOST = "localhost";
	private static final int PORT = 5000;
	private static final SimpleDateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("HH:mm:ss");

	private Socket cliente;
	private ObjectOutputStream out;
	private ObjectInputStream in;

	private JFrame frame;
	private JTextArea textArea;
	private JTextField inputField;
	private JButton sendButton;
	/**
     * Método principal que inicia la aplicación del cliente.
     * @param args argumentos pasados por línea de comandos (no se utilizan en este programa).
     */
	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			try {
				new Cliente().start();
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
	}

	/**
     * Configura la conexión con el servidor y la interfaz gráfica del usuario.
     * Administra el flujo principal de la aplicación, incluyendo la selección del canal 
     * y la configuración del nombre de usuario.
     * 
     * @throws IOException si ocurre un error en la conexión o en la transmisión de datos.
     * @throws ClassNotFoundException si ocurre un error al deserializar los objetos recibidos.
     */
	public void start() throws IOException, ClassNotFoundException {
		cliente = new Socket(HOST, PORT);
		out = new ObjectOutputStream(cliente.getOutputStream());
		in = new ObjectInputStream(cliente.getInputStream());

		// Configuración de la interfaz gráfica
		configurarInterfazGrafica();

		// Recibir lista de canales
		List<String> canales = (List<String>) in.readObject();
		mostrarMensaje("Canales disponibles: " + String.join(", ", canales));

		// Seleccionar canal
		String canalSeleccionado = JOptionPane.showInputDialog(frame, "Selecciona un canal (número):");
		out.writeObject(canalSeleccionado);
		String respuesta = (String) in.readObject();
		while (!respuesta.equals("OK")) {
			canalSeleccionado = JOptionPane.showInputDialog(frame, "Canal no válido. Selecciona otro:");
			out.writeObject(canalSeleccionado);
			respuesta = (String) in.readObject();
		}
		mostrarMensaje("Te has unido al canal: " + canalSeleccionado);

		// Introducir nombre de usuario
		String nombreUsuario = JOptionPane.showInputDialog(frame, "Introduce tu nombre de usuario:");
		out.writeObject(nombreUsuario);
		respuesta = (String) in.readObject();
		while (!respuesta.equals("OK")) {
			nombreUsuario = JOptionPane.showInputDialog(frame, "Nombre de usuario no válido. Intenta con otro:");
			out.writeObject(nombreUsuario);
			respuesta = (String) in.readObject();
		}
		mostrarMensaje("Te has unido como: " + nombreUsuario);

		// Hilo para recibir mensajes del servidor
		new Thread(() -> {
			try {
				while (true) {
					String mensaje = (String) in.readObject();
					mostrarMensaje(mensaje);
				}
			} catch (IOException | ClassNotFoundException e) {
				mostrarMensaje("Conexión cerrada con el servidor.");
			}
		}).start();
	}
	/**
     * Configura la interfaz gráfica del cliente.
     * Incluye un área de texto para mostrar mensajes, un campo de entrada y un botón para enviar mensajes.
     */
	private void configurarInterfazGrafica() {
		frame = new JFrame("Cliente Chat");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setSize(500, 400);
		frame.setLayout(new BorderLayout());

		// Área de texto para mostrar mensajes
		textArea = new JTextArea();
		textArea.setEditable(false);
		textArea.setLineWrap(true);
		textArea.setWrapStyleWord(true);
		textArea.setFont(new Font("Arial", Font.PLAIN, 14));
		textArea.setBackground(Color.WHITE);
		textArea.setForeground(Color.DARK_GRAY);
		JScrollPane scrollPane = new JScrollPane(textArea);
		frame.add(scrollPane, BorderLayout.CENTER);

		// Panel inferior para entrada de mensajes
		JPanel bottomPanel = new JPanel(new BorderLayout());
		inputField = new JTextField();
		inputField.setFont(new Font("Arial", Font.PLAIN, 14));
		inputField.setBackground(Color.LIGHT_GRAY);
		inputField.setForeground(Color.BLACK);
		bottomPanel.add(inputField, BorderLayout.CENTER);

		// Botón de enviar mensaje
		sendButton = new JButton("Enviar");
		sendButton.setFont(new Font("Arial", Font.BOLD, 14));
		sendButton.setBackground(new Color(59, 89, 182));
		sendButton.setForeground(Color.WHITE);
		sendButton.setFocusPainted(false);
		sendButton.addActionListener((ActionEvent e) -> {
			enviarMensaje(inputField.getText());
			inputField.setText("");
		});
		bottomPanel.add(sendButton, BorderLayout.EAST);

		frame.add(bottomPanel, BorderLayout.SOUTH);

		// Hacer visible la ventana
		frame.setVisible(true);
	}
	/**
     * Envía un mensaje al servidor.
     * Si el mensaje es "exit", cierra la conexión y termina la aplicación.
     * 
     * @param mensaje el mensaje a enviar al servidor.
     */
	private void enviarMensaje(String mensaje) {
		try {
			if (mensaje == null || mensaje.trim().isEmpty()) {
				return; // No enviar mensajes vacíos
			}
			out.writeObject(mensaje);
			mostrarMensaje("Yo: " + mensaje);
			if (mensaje.equalsIgnoreCase("exit")) {
				cerrarConexion();
				System.exit(0);
			}
		} catch (IOException e) {
			mostrarMensaje("Error al enviar mensaje: " + e.getMessage());
		}
	}
	 /**
     * Muestra un mensaje en el área de texto del cliente, incluyendo una marca de tiempo.
     * 
     * @param mensaje el mensaje a mostrar.
     */
	private void mostrarMensaje(String mensaje) {
		SwingUtilities.invokeLater(() -> {
			String timestamp = TIMESTAMP_FORMAT.format(new Date());
			textArea.append("[" + timestamp + "] " + mensaje + "\n");
			textArea.setCaretPosition(textArea.getDocument().getLength());
		});
	}
	/**
     * Cierra la conexión con el servidor y libera los recursos asociados.
     */
	private void cerrarConexion() {
		try {
			if (out != null)
				out.close();
			if (in != null)
				in.close();
			if (cliente != null)
				cliente.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
