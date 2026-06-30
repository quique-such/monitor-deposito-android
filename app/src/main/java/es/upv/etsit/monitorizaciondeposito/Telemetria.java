package es.upv.etsit.monitorizaciondeposito;

import android.app.Activity;
import android.view.View;
import android.widget.EditText;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.Timer;
import java.util.TimerTask;

public class Telemetria {

    // Clase anidada cuya intención es representar un objeto con la información recibida
    static public class Info {
        public float nivel;
        public String mensajeEstadoBoya;
        public boolean estadoGrifoEntrada;
        public boolean estadoGrifoSalida;
    }

    // Periodo de petición de información al simulador en ms
    final int T_MONITOR_TELEMETRIA_MS = 250;

    /** Datagramas UDP */
    DatagramSocket socket;
    DatagramPacket DP_send, DP_rec;

    // IP y puerto, getters y setters
    String host_telemetria = "";
    public String getHost_telemetria() {
        return host_telemetria;
    }
    public int getPuerto_servidor_telemetria() {
        return puerto_servidor_telemetria;
    }
    int puerto_servidor_telemetria;
    public void setHost_telemetria(String host_telemetria) {
        this.host_telemetria = host_telemetria;
    }
    public void setPuerto_servidor_telemetria(int puerto_servidor_telemetria) {
        this.puerto_servidor_telemetria = puerto_servidor_telemetria;
    }

    // Actividad invocante
    Activity actividad;

    // control
    boolean primera_vez = true;
    Timer timer;

    // Constructor
    public Telemetria(Activity actividad) {

        // Se guarda la actividad llamante (para poder escribir en ella)
        this.actividad = actividad;

        // Preparación de datagramas de transmisión y recepción
        try {
            socket = new DatagramSocket();
            socket.setSoTimeout(3000);  // tiempo de espera máximo hasta recibir respuesta; si no, ¡timeout!

            byte[] array4Bytes_send = new byte[1400];
            DP_send = new DatagramPacket(array4Bytes_send, array4Bytes_send.length);

            byte[] array4Bytes_rec = new byte[1400];
            DP_rec = new DatagramPacket(array4Bytes_rec, array4Bytes_rec.length);

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(actividad, "Problemas con los sockets UDP", Toast.LENGTH_SHORT).show();
        }

        // Click en botón: carga de ip y puerto y temporización
        ((Button) actividad.findViewById(R.id.botonActualizar)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // lectura de host y puerto de los EditText
                host_telemetria = ((EditText) actividad.findViewById(R.id.ip)).getText().toString();
                String puerto_string = ((EditText) actividad.findViewById(R.id.puerto)).getText().toString();
                puerto_servidor_telemetria = Integer.parseInt(puerto_string);

                // Se hacen peticiones periódicas
                arrancarPeticiones();

            }
        });

    } // Telemetria constructor

    // Prepara peticiones periódicas de información al servidor
    public void arrancarPeticiones() {
        try {

            // Escritura de IP-host:puerto en pantalla
            ((TextView) actividad.findViewById(R.id.ip_puerto)).setText(host_telemetria + " : " + puerto_servidor_telemetria);

            // Ajuste de IP-host:puerto en los datagramas de envío.
            InetSocketAddress IP_PUERTO_socket_address = new InetSocketAddress(host_telemetria, puerto_servidor_telemetria);
            DP_send.setSocketAddress(IP_PUERTO_socket_address);

            if (primera_vez) {
                primera_vez = false; // Este bloque solo se realizará una vez
                /************** CODIGO ****************/
                // Se debe programar la invocación al método pedirInfo cada T_MONITOR_TELEMETRIA_MS
                timer = new Timer();
                timer.scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {
                        pedirInfo();
                    }
                }, 0, T_MONITOR_TELEMETRIA_MS);
                /************** CODIGO ****************/
            }
        } catch (Exception e) {
            Toast.makeText(actividad, "Problemas de sintaxis de IP:puerto", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    // Pide información al servidor de telemetria
    private void pedirInfo() {

        // Petición de datos de telemetría: carga de datagrama con "datos"
        DP_send.setData("{ \"op\": \"info\"}".getBytes());

        boolean recibido = false;
        do {
            try {
                socket.send(DP_send); // envia datagrama de petición de información
                try {
                    socket.receive(DP_rec);  // recibe datagrama con información
                    recibido = true;
                } catch (SocketTimeoutException e) { // demasiado tiempo esperando...
                    // Esta tarea fue arrancada por el temporizador en un hilo distinto al principal,
                    // luego no se puede escribir directamente en la interfaz de usuario (UI).
                    // Una posible alternativa: uso de runOnUiThread() ejecutado sobre la
                    // actividad en la que se quiere escribir en su UI
                    actividad.runOnUiThread(new Runnable() {
                        @Override
                        public void run() { // Una tostada
                            Toast.makeText(actividad, "Sin respuesta", Toast.LENGTH_SHORT).show();
                        }
                    });
                    /* Reintentos: espera de 1 segundo entre reintento y reintento */
                    for (int i = 6; i > 0; i--) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ex) {
                            ex.printStackTrace();
                        }
                    } // for
                } // catch
            } catch (IOException e) {
                return;
            }
        } while (!recibido);

        // Los bytes recibidos se codifican en un string supuestamente con formato JSON
        String strJson = new String(DP_rec.getData(), 0, DP_rec.getLength());

        // Se imprime la información del JSON
        printInfo(strJson);

    } // pedirInfo

    void printInfo(String strJSON) {
        Gson gson = new Gson();
        Info info = gson.fromJson(strJSON, Info.class);
        actividad.runOnUiThread(new Runnable() { // No estamos en el hilo de la UI, luego uso de runOnUiThread
            @Override
            public void run() {
                /***************** CODIGO ****************/
                // Escritura de los atributos del objeto info en pantalla.
                ((TextView) actividad.findViewById(R.id.nivel)).setText("" + info.nivel);
                ((TextView) actividad.findViewById(R.id.mensajeEstadoBoya)).setText(info.mensajeEstadoBoya);
                ((TextView) actividad.findViewById(R.id.estadoGrifoEntrada)).setText(info.estadoGrifoEntrada ? "Abierto" : "Cerrado");
                ((TextView) actividad.findViewById(R.id.estadoGrifoSalida)).setText(info.estadoGrifoSalida ? "Abierto" : "Cerrado");
                ((TextView) actividad.findViewById(R.id.jsonRecibido)).setText(strJSON);
                /***************** CODIGO ****************/
            }
        });
    }

}
