import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.text.*;
import java.io.*;
import java.net.*;

import graph.*;

public class House extends JFrame implements Runnable {
    public static final long serialVersionUID = 2L;
    public static void main ( String[] args ) throws SocketException {
        SwingUtilities.invokeLater( new Runnable() {
            public void run() { new House(args[0], Integer.parseInt(args[1])); }
        } );
    }

    HeatingSystem heatingsys = new HeatingSystem();
    GraphPanel plots = new GraphPanel(new Dimension(400,200));
    DatagramPanel receive = new DatagramPanel();

    public House(String ipAddress, int portNumber) {
        super("House Heating");
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel content = new JPanel( );
        content.setLayout( new BoxLayout( content, BoxLayout.Y_AXIS) );

        receive.setAddress(ipAddress, false);
        receive.setPort(portNumber, false);
        content.add(receive);

        content.add( heatingsys );

        JPanel data = new JPanel();
        data.setBorder( BorderFactory.createTitledBorder("Temperature"));
        data.add( plots );
        content.add(data);
        this.setContentPane(content);
        this.pack();
        this.setVisible(true);

        /* Add a plot with key/title, an initial value,
            a colour, and min max value scale
        */
        plots.addTrace("T", 20, Color.blue, new GraphPanel.Scale(0,50));
        plots.addTrace("H", 0, Color.red, new GraphPanel.Scale(-5,2));

        /* Start the threads for the
           Heating system on a 0.5s period
           Plot data on a 0.1s period
        */
        (new javax.swing.Timer(100,heatingsys)).start();
        (new javax.swing.Timer(100,plots)).start();

        /* start thread that handles comminications */
        (new Thread(this)).start();
    }

    public void run() {
        try{
        /* set up socket for reception */
        SocketAddress address = receive.getSocketAddress();
        DatagramSocket socket = new DatagramSocket(address);

        while(true) {
            try{
                /* start with fresh datagram packet */
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive( packet );
                /* extract message and pick appart into
                   lines and key:value pairs
                */
                String message = new String(packet.getData());
                String[] lines = message.trim().split("\n");
                String[] pair  = lines[0].split(":");

                switch( pair[0] ) {/*<-- Java now lets you do switches on strings :-) */
                    case "temperature":
                        if(pair[1].equals("?")) {
                            String reply = String.format("temperature:%f\n",heatingsys.getTemperature() );
                            packet.setData(reply.getBytes());/* packet has return address from the receive() above */
                            socket.send( packet );
                        }
                    break;
                    case "heating":
                        switch( pair[1] ){
                            case "on":  heatingsys.boiler.on();  break;
                            case "off": heatingsys.boiler.off(); break;
                        }
                    break;
                }
            }catch(IOException e){
                System.err.println(e.getMessage());
            }
        }
    }catch(SocketException e){System.err.println(e.getMessage());}
    }


    public class HeatingSystem extends JPanel implements ActionListener {
        public static final long serialVersionUID = 2L;
        Boiler boiler = new Boiler();
        JLabel reading = new JLabel("     \u00B0");
        double temperature;
        double radiators = 0.0;
        public HeatingSystem(){
            super(new FlowLayout( FlowLayout.LEFT, 5, 0));
            this.setBorder( BorderFactory.createTitledBorder("System"));
            this.add(boiler);
            this.add(reading);
        }
        double getTemperature() { return temperature; }
        public void actionPerformed(ActionEvent t){
            double DT = temperature - 10;
            double dt = 0.1;
            double  k = .25;
            if(boiler.active && radiators<5.0){
                radiators += 0.5;
            }
            if( !boiler.active && 0.0<radiators){
                radiators -= 0.25;
            }
            temperature += -k*DT*dt + radiators*.1;
            reading.setText(String.format("%8.1f\u00B0C", temperature));
            plots.plotPoint("T",temperature);
            plots.plotPoint("H",(boiler.active)?1.0:0.0);
        }
        class Boiler extends JLabel {
            public static final long serialVersionUID = 2L;
            boolean active = false;
            ImageIcon[] indicator = {
                new ImageIcon(House.class.getResource("led-grey.png")),
                new ImageIcon(House.class.getResource("led-green.png"))
            };
            public Boiler(){super("heating"); setIcon(indicator[0]);}
            public void on(){active=true; setIcon(indicator[1]);}
            public void off(){active=false; setIcon(indicator[0]);}
        }
        void on()  { boiler.on();  }
        void off() { boiler.off(); }
    }
}
