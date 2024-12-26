package Conexion;

import java.io.*;
import java.net.*;
import java.util.*;

public class Servidor {
    private static final int PORT = 5000;
    private static Map<String, String> canales = new HashMap<>();
    private static Map<String, List<GestionCliente>> canalHilos = new HashMap<>();
    private static Set<String> nombresDeUsuarios = new HashSet<>();

    // Códigos ANSI para colores
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_RESET = "\u001B[0m";
    /**
     * Método principal del servidor. Carga los canales desde un archivo y
     * escucha conexiones de clientes. Crea un nuevo hilo por cada cliente conectado.
     * 
     * @param args argumentos de línea de comandos (no utilizados).
     * @throws IOException si ocurre un error al cargar los canales o manejar conexiones.
     */
    public static void main(String[] args) throws IOException {
        cargarCanales();
        ServerSocket servidor = new ServerSocket(PORT);
        System.out.println(ANSI_RED + "SERVIDOR >> Esperando conexiones..." + ANSI_RESET);

        while (true) {
            Socket cliente = servidor.accept();
            System.out.println(ANSI_RED + "SERVIDOR >> Cliente conectado desde " + cliente.getInetAddress().getHostAddress() + ANSI_RESET);
            new Thread(new GestionCliente(cliente)).start();
        }
    }

    /**
     * Carga la lista de canales desde el archivo "canals.txt".
     * Cada línea del archivo debe tener el formato: número-canal.
     * 
     * @throws IOException si ocurre un error al leer el archivo.
     */
    private static void cargarCanales() throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader("canals.txt"));
        String linea;
        while ((linea = reader.readLine()) != null) {
            String[] partes = linea.split("-");
            if (partes.length == 2) {
                String numeroCanal = partes[0].trim();
                String nombreCanal = partes[1].trim();
                canales.put(numeroCanal, nombreCanal);
                canalHilos.put(nombreCanal, new ArrayList<>()); // Inicializar lista de hilos para cada canal
            }
        }
        reader.close();
    }
    /**
     * Clase interna que gestiona la conexión de un cliente específico.
     * Implementa Runnable para ejecutarse en un hilo separado.
     */
    private static class GestionCliente implements Runnable {
        private Socket cliente;
        private String canalSeleccionado;
        private String nombreUsuario;
        private ObjectOutputStream out;
        private ObjectInputStream in;
        private List<GestionCliente> hilosDelCanal; // Lista de hilos en el canal actual
        /**
         * Constructor que inicializa el cliente.
         * 
         * @param cliente socket del cliente conectado.
         */
        public GestionCliente(Socket cliente) {
            this.cliente = cliente;
        }
        /**
         * Método principal del hilo. Maneja la interacción con el cliente,
         * incluyendo selección de canal, selección de nombre de usuario
         * y envío/recepción de mensajes.
         */
        @Override
        public void run() {
            try {
                out = new ObjectOutputStream(cliente.getOutputStream());
                in = new ObjectInputStream(cliente.getInputStream());

                // Enviar lista de canales al cliente
                out.writeObject(new ArrayList<>(canales.values()));

                // Selección de canal
                while (true) {
                    String numeroSeleccionado = (String) in.readObject();
                    if (!canales.containsKey(numeroSeleccionado)) {
                        out.writeObject("ERROR: Canal no válido.");
                    } else {
                        canalSeleccionado = canales.get(numeroSeleccionado);
                        synchronized (canalHilos) {
                            hilosDelCanal = canalHilos.get(canalSeleccionado);
                            hilosDelCanal.add(this); // Agregar este hilo a la lista del canal
                        }
                        out.writeObject("OK");
                        System.out.println(ANSI_RED + "SERVIDOR >> El cliente ha seleccionado el canal: " + canalSeleccionado + ANSI_RESET);
                        break;
                    }
                }

                // Selección de nombre de usuario
                while (true) {
                    nombreUsuario = (String) in.readObject();
                    synchronized (nombresDeUsuarios) {
                        if (nombresDeUsuarios.contains(nombreUsuario)) {
                            out.writeObject("ERROR: El nombre de usuario ya está en uso.");
                        } else if (nombreUsuario.contains(" ")) {
                            out.writeObject("ERROR: El nombre no puede contener espacios.");
                        } else {
                            nombresDeUsuarios.add(nombreUsuario);
                            out.writeObject("OK");
                            System.out.println(ANSI_RED + "SERVIDOR >> Usuario " + nombreUsuario + " se ha unido al canal " + canalSeleccionado + ANSI_RESET);
                            break;
                        }
                    }
                }

                // Procesar mensajes del cliente
                procesarMensajes();

            } catch (IOException | ClassNotFoundException e) {
                System.out.println(ANSI_RED + "SERVIDOR >> Error al procesar cliente: " + e.getMessage() + ANSI_RESET);
            } finally {
                cerrarConexion();
            }
        }
        /**
         * Procesa los mensajes enviados por el cliente.
         * Permite comandos como "whois", "channels", "@canal mensaje", y "exit".
         * 
         * @throws IOException si ocurre un error de comunicación.
         * @throws ClassNotFoundException si ocurre un error al leer objetos.
         */
        private void procesarMensajes() throws IOException, ClassNotFoundException {
            while (true) {
                String mensaje = (String) in.readObject();
                if (mensaje.equals("whois")) {
                    // Enviar lista de usuarios conectados
                    String usuarios = String.join(", ", nombresDeUsuarios);
                    out.writeObject("Usuarios conectados: " + usuarios);
                    System.out.println(ANSI_RED + "SERVIDOR >> El usuario " + nombreUsuario + " pidió la lista de usuarios." + ANSI_RESET);
                } else if (mensaje.equals("channels")) {
                    // Enviar lista de canales
                    String listaCanales = String.join(", ", canales.values());
                    out.writeObject("Canales disponibles: " + listaCanales);
                    System.out.println(ANSI_RED + "SERVIDOR >> El usuario " + nombreUsuario + " pidió la lista de canales." + ANSI_RESET);
                } else if (mensaje.equals("exit")) {
                    // Salir del chat
                    out.writeObject("Desconectado del servidor.");
                    System.out.println(ANSI_RED + "SERVIDOR >> Usuario " + nombreUsuario + " ha salido del canal " + canalSeleccionado + ANSI_RESET);
                    break;
                } else if (mensaje.startsWith("@")) {
                    // Mensaje a canal específico
                    String[] partes = mensaje.split(" ", 2);
                    if (partes.length == 2) {
                        String canalDestino = partes[0].substring(1);
                        String contenido = partes[1];
                        enviarMensajeACanal(canalDestino, nombreUsuario + ": " + contenido);
                        System.out.println(ANSI_RED + "SERVIDOR >> Usuario " + nombreUsuario + " envió un mensaje al canal @" + canalDestino + ": " + contenido + ANSI_RESET);
                    } else {
                        out.writeObject("Formato incorrecto. Usa: @canal mensaje");
                    }
                } else {
                    // Mensaje global al canal actual
                    enviarMensajeACanal(canalSeleccionado, nombreUsuario + ": " + mensaje);
                    System.out.println(ANSI_RED + "SERVIDOR >> Usuario " + nombreUsuario + " envió un mensaje al canal " + canalSeleccionado + ": " + mensaje + ANSI_RESET);
                }
            }
        }
        /**
         * Envía un mensaje a todos los usuarios de un canal específico.
         * 
         * @param canal nombre del canal al que enviar el mensaje.
         * @param mensaje contenido del mensaje.
         */
        private void enviarMensajeACanal(String canal, String mensaje) {
            synchronized (canalHilos) {
                List<GestionCliente> hilos = canalHilos.get(canal);
                if (hilos != null) {
                    for (GestionCliente hilo : hilos) {
                        try {
                            hilo.out.writeObject(mensaje);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                } else {
                    try {
                        out.writeObject("El canal no existe.");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        /**
         * Cierra la conexión del cliente, eliminando al usuario de las listas globales.
         */
        private void cerrarConexion() {
            try {
                synchronized (nombresDeUsuarios) {
                    nombresDeUsuarios.remove(nombreUsuario);
                }
                synchronized (canalHilos) {
                    if (hilosDelCanal != null) {
                        hilosDelCanal.remove(this); // Eliminar este hilo de la lista del canal
                    }
                }
                if (nombreUsuario != null && canalSeleccionado != null) {
                    System.out.println(ANSI_RED + "SERVIDOR >> Usuario " + nombreUsuario + " desconectado del canal " + canalSeleccionado + ANSI_RESET);
                }
                if (out != null) out.close();
                if (in != null) in.close();
                if (cliente != null) cliente.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
