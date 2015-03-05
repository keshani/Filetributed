package lk.filetributed.client;

import lk.filetributed.dispatcher.MessageBuffer;
import lk.filetributed.dispatcher.MessageDispatcher;
import lk.filetributed.dispatcher.MessageOutBuffer;
import lk.filetributed.model.*;
import lk.filetributed.model.protocols.*;
import lk.filetributed.model.protocols.FileTableProtocol;
import lk.filetributed.model.protocols.JoinProtocol;
import lk.filetributed.model.protocols.JoinStatus;
import lk.filetributed.model.protocols.MessageProtocolType;
import lk.filetributed.util.Utils;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class Client extends Node {

    private static Logger logger = Logger.getLogger(Client.class);

    private static final String SERVER_NAME = "127.0.0.1";
    private static final int PORT = 9889;

    private static final String CLIENT_IP = "127.0.0.1";
    private static final int CLIENT_PORT = 9886;
    private static final String USERNAME = "24332sa";
    private static final int NO_CLUSTERS = 3;

    private static final String[] FILE_NAMES = {"Adventures of Tintin", "Jack and Jill"};

    private MessageBuffer messageBuffer;
    private MessageOutBuffer outBuffer;

    public Client() {
        super(CLIENT_IP, PORT, NO_CLUSTERS);
        messageBuffer = MessageBuffer.getInstance();
        outBuffer = MessageOutBuffer.getInstance();

        Thread t_udpServer = new Thread(new UDPServer(CLIENT_PORT));
        Thread t_messageDispatcher = new Thread(new MessageDispatcher());
        t_udpServer.start();
        t_messageDispatcher.start();
        initFileTable(FILE_NAMES);
        initialize();
    }

    //connect with the system
    public void initialize() {
        String result = BootstrapConnector.connectToBootstrap(SERVER_NAME, PORT, CLIENT_IP, CLIENT_PORT, USERNAME);
        try {
            response_tokenizer(result);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initFileTable(String[] fileNames) {
        for (String fileName : fileNames) {
            fileTable.addTableEntry(new FileTableEntry(fileName, CLIENT_IP, CLIENT_PORT));
        }
    }

    public List<FileTableEntry> searchFile(String filename) {
        return this.fileTable.searchTable(filename);
    }

    public static void main(String[] args) {
        Client client = new Client();
    }

    public void response_tokenizer(String server_response) throws IOException {
        String[] response_data = server_response.split(" ");
        if (!response_data[1].equals("REGOK")) {
            System.err.println("Registration Failed. Try Again!");
            //FIXME : Come up with a better way to handle this?
            System.exit(0);
        }

        int no_nodes = Integer.parseInt(response_data[2]);


        switch (no_nodes) {
            case 0:
                process();
                break;
            case 1:
                process(response_data[3], Integer.parseInt(response_data[4]));
                break;
            case 2:
                process(response_data[3], Integer.parseInt(response_data[4]), response_data[6], Integer.parseInt(response_data[7]));
                break;
            case 9999:
                System.out.println("failed, there is some error in the command");
                break;
            case 9998:
                System.out.println("failed, already registered to you, unregister first");
                break;
            case 9997:
                System.out.println("failed, registered to another user, try a different IP and port");
                break;
            case 9996:
                System.out.println("failed, can’t register. BS full.");
                break;
        }
        while (true) {
            processBuffer();
        }
    }

    public void process() {

    }

    public void process(String RECIEVED_IP, int RECIEVED_PORT) throws IOException {
        int clusterID;

        clusterID = Utils.getClusterID(RECIEVED_IP, RECIEVED_PORT, NO_CLUSTERS);
        Node node = new Node(RECIEVED_IP, RECIEVED_PORT, NO_CLUSTERS);

        getIpTable().addTableEntry(new TableEntry(RECIEVED_IP, RECIEVED_PORT + "", clusterID + ""));

        //generating the join message
        sendJoinMessage(RECIEVED_IP, RECIEVED_PORT);


//        FileTableProtocol fileTableProtocol = new FileTableProtocol(CLIENT_IP,CLIENT_PORT,fileTable);
//        String fileTableMSG = fileTableProtocol.toString();
//
//        outBuffer.add(new DispatchMessage(fileTableMSG,RECIEVED_IP,RECIEVED_PORT));


    }

    public void process(String RECIEVED_IP_01, int RECIEVED_PORT_01, String RECIEVED_IP_02, int RECIEVED_PORT_02) {
        int clusterID01;
        int clusterID02;

        clusterID01 = Utils.getClusterID(RECIEVED_IP_01, RECIEVED_PORT_01, NO_CLUSTERS);
        Node node01 = new Node(RECIEVED_IP_01, RECIEVED_PORT_01, NO_CLUSTERS);

        getIpTable().addTableEntry(new TableEntry(RECIEVED_IP_01, RECIEVED_PORT_01 + "", clusterID01 + ""));

        //generating the join message
        sendJoinMessage(RECIEVED_IP_01, RECIEVED_PORT_01);

        clusterID02 = Utils.getClusterID(RECIEVED_IP_02, RECIEVED_PORT_02, NO_CLUSTERS);
        Node node02 = new Node(RECIEVED_IP_02, RECIEVED_PORT_02, NO_CLUSTERS);

        getIpTable().addTableEntry(new TableEntry(RECIEVED_IP_02, RECIEVED_PORT_02 + "", clusterID02 + ""));

        //generating the join message
        sendJoinMessage(RECIEVED_IP_02, RECIEVED_PORT_02);

    }

    public void processBuffer() {
        MessageProtocol message = messageBuffer.getMessage();
        switch (MessageProtocolType.valueOf(message.getMessageType())) {

            case JOIN:
                if (message instanceof JoinProtocol)
                    process_JoinMessage((JoinProtocol) message);
                break;

            case IPTABLE:
                /*if(message instanceof IPTableProtocol){
                    ((IPTableProtocol) message).addNewEntriesToIPTable(Integer.parseInt(getClusterID()));


                }*/
                logger.info("IPTable Merging should happen here!");
                break;
            case FILETABLE:
                System.out.println("#######" + message.toString());
                break;
            case GROUP:
                if (message instanceof GroupProtocol)
                    process_groupMessage((GroupProtocol) message);
                break;
            case QUERY:
                if (message instanceof QueryProtocol)
                    process_queryMessage((QueryProtocol) message);
                break;
            default:
                break;
        }
        logger.info("IP TABLE LOG @ PORT" + CLIENT_PORT + " : " + getIpTable().toString());
    }

    /**
     * Process the Join Message
     *
     * @param message
     */
    private void process_JoinMessage(JoinProtocol message) {
        int clusterID = message.getClusterID();
        if (clusterID == this.getClusterID()) { //check if it's in the same cluster
            String ipAddress = message.getIpAddress();
            int port = message.getPort();

            if (!this.getIpTable().isEmpty()) {
                IPTableProtocol ipMessage = new IPTableProtocol(ipAddress, port,
                        this.getClusterID(), this.getIpTable());

                outBuffer.add(new DispatchMessage(ipMessage.toString(), ipMessage.getIpAddress(), ipMessage.getPort()));
                logger.info("Sending IPTables ...: " + ipMessage.toString());
            }
            //TODO Send the file table here
        } else {
            TableEntry entry = this.ipTable.searchClusterID(String.valueOf(clusterID));
            if (entry == null) { //there's no entry in the IP table for this cluster
                //TODO add this entry to the IPTable

            } else { //there's an entry in the IP table for this cluster
                GroupProtocol groupMessage = new GroupProtocol(entry.getIpAddress(), Integer.parseInt(entry.getPort()));
                outBuffer.add(new DispatchMessage(groupMessage.toString(), ipAddress, port));
            }
        }

    }

    public void process_groupMessage(GroupProtocol message) {
        int clusterID = message.getClusterID();
        if (clusterID == this.getClusterID()) { //check if it's in the same cluster
            JoinProtocol joinMessage = new JoinProtocol(message.getClientIP(), message.getPort());
            outBuffer.add(new DispatchMessage(joinMessage.toString(), message.getClientIP(), message.getPort()));
        }
    }

    public void sendJoinMessage(String RECIEVED_IP, int RECIEVED_PORT) {
        //generating the join message
        JoinProtocol joinProtocol = new JoinProtocol(CLIENT_IP, CLIENT_PORT);
        String JOIN_MSG = joinProtocol.toString();

        outBuffer.add(new DispatchMessage(JOIN_MSG, RECIEVED_IP, RECIEVED_PORT));

    }

    public void process_queryMessage(QueryProtocol message) {
        int hopCount = message.getNoOfHops();
        if(hopCount > 0) {
            hopCount--;
            List<FileTableEntry> results = searchFile(message.getKeyword());
            String ipAddress = message.getIpAddress();
            int port = message.getPort();

            if(results.size() > 0) {
                // generating QueryHit message
                QueryHitProtocol queryHitMessage = new QueryHitProtocol(ipAddress, port, results);
                outBuffer.add(new DispatchMessage(queryHitMessage.toString(), ipAddress, port));
            }
        } if(hopCount > 0){ // check hopCount and forward QUERY message
            message.setNoOfHops(hopCount);
            int clusterID = this.clusterID;
            if(!this.getIpTable().isEmpty()) {
                List<TableEntry> ipTable = this.ipTable.getEntries();
                for (TableEntry entry : ipTable) {
                    // send QUERY message to other clusters
                    // FIXME filter out the query source's cluster before forwarding
                    if(!entry.getClusterID().equals(clusterID)) {
                        outBuffer.add(new DispatchMessage(message.toString(),entry.getIpAddress(),
                                Integer.parseInt(entry.getPort())));
                    }
                }
            }
        }



    }

}